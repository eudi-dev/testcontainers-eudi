/*
 * Copyright 2026 Dominik Schlosser
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.dominikschlosser.eudi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class EudiWalletRegistrarTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PID_VCT = "urn:eudi:pid:1";

    @Container
    static EudiWalletContainer wallet = new EudiWalletContainer()
            .withArf()
            .withStrictValidation();

    @Test
    void verifierRegistrationIssuesBothCertificates() throws Exception {
        WalletClient client = wallet.client();
        RelyingParty shop = registerShop(client);

        assertThat(shop.identifier()).matches("NTRNL-[0-9A-F]+");
        assertThat(shop.tradeName()).isEqualTo("Example Shop");
        assertThat(shop.intendedUses()).singleElement().satisfies(use -> {
            assertThat(use.identifier()).isNotBlank();
            assertThat(use.purpose()).isEqualTo("Name check");
            assertThat(use.credentials()).containsExactly(new RegisteredCredential(
                    CredentialFormat.SD_JWT, PID_VCT, List.of("given_name", "family_name")));
        });

        AccessCertificate access = client.issueAccessCertificate(shop.identifier(), csr(), "shop.example");
        assertThat(access.certificate()).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(access.chain().split("BEGIN CERTIFICATE")).hasSize(3);
        assertThat(access.clientIds()).contains("x509_san_dns:shop.example")
                .anyMatch(clientId -> clientId.startsWith("x509_hash:"));

        RegistrationCertificate registration =
                client.issueRegistrationCertificate(shop.identifier(), shop.intendedUses().getFirst().identifier());
        assertThat(jwtHeader(registration.certificate())).containsEntry("typ", "rc-wrp+jwt");
        assertThat(registration.issuerInfo()).isNull();
        List<Map<String, Object>> verifierInfo =
                MAPPER.readValue(registration.verifierInfo(), new TypeReference<>() {});
        assertThat(verifierInfo).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("data", registration.certificate()));
    }

    @Test
    void registeredVerifierPassesStrictArfChecksUntilRevoked() throws Exception {
        WalletClient client = wallet.client();
        RelyingParty shop = registerShop(client);
        String intendedUse = shop.intendedUses().getFirst().identifier();
        AccessCertificate access = client.issueAccessCertificate(shop.identifier(), csr());
        RegistrationCertificate registration = client.issueRegistrationCertificate(shop.identifier(), intendedUse);
        String signingKey = Files.readString(Path.of("src/test/resources/relying-party-key.pem")) + access.chain();

        Map<String, Object> request = createVerifierRequest(signingKey, registration.verifierInfo());
        wallet.acceptPresentationRequest((String) request.get("scheme_uri"));
        assertThat(verifierRequestStatus((String) request.get("id")).get("status")).isEqualTo("verified");

        assertThat(client.revokeRegistrationCertificates(shop.identifier(), intendedUse)).isEqualTo(1);
        Map<String, Object> revoked = createVerifierRequest(signingKey, registration.verifierInfo());
        assertThatThrownBy(() -> wallet.acceptPresentationRequest((String) revoked.get("scheme_uri")))
                .isInstanceOf(WalletClientException.class)
                .hasMessageContaining("revoked");

        assertThat(client.activateRegistrationCertificates(shop.identifier(), intendedUse)).isEqualTo(1);
        Map<String, Object> activated = createVerifierRequest(signingKey, registration.verifierInfo());
        wallet.acceptPresentationRequest((String) activated.get("scheme_uri"));
        assertThat(verifierRequestStatus((String) activated.get("id")).get("status")).isEqualTo("verified");
    }

    @Test
    void registrationCertificatesCanBeRevokedAndActivated() {
        WalletClient client = wallet.client();
        RelyingParty shop = registerShop(client);
        client.issueRegistrationCertificate(shop.identifier(), shop.intendedUses().getFirst().identifier());

        assertThat(client.getRegistrationCertificateStatuses(shop.identifier())).singleElement()
                .satisfies(status -> {
                    assertThat(status.identifier()).isEqualTo(shop.identifier());
                    assertThat(status.intendedUse()).isEqualTo(shop.intendedUses().getFirst().identifier());
                    assertThat(status.revoked()).isFalse();
                    assertThat(status.expires()).isPositive();
                });

        assertThat(client.revokeRegistrationCertificates(shop.identifier())).isEqualTo(1);
        assertThat(client.getRegistrationCertificateStatuses(shop.identifier()))
                .allMatch(RegistrationCertificateStatus::revoked);

        assertThat(client.activateRegistrationCertificates(shop.identifier())).isEqualTo(1);
        assertThat(client.getRegistrationCertificateStatuses(shop.identifier()))
                .noneMatch(RegistrationCertificateStatus::revoked);

        client.deleteRelyingParty(shop.identifier());
        assertThat(client.getRegistrationCertificateStatuses(shop.identifier()))
                .allMatch(status -> status.revoked() && status.superseded());
    }

    @Test
    void issuerRegistrationCarriesIssuerInfo() throws Exception {
        WalletClient client = wallet.client();
        RelyingParty university = client.registerRelyingParty(RelyingPartyRegistration.issuer("Example University")
                .attestation(CredentialFormat.SD_JWT, "urn:example:diploma:1"));

        assertThat(university.services()).singleElement().satisfies(service -> {
            assertThat(service.entitlements()).contains(ProviderEntitlement.NON_Q_EAA_PROVIDER.getWireValue());
            assertThat(service.providedAttestations()).containsExactly(new RegisteredCredential(
                    CredentialFormat.SD_JWT, "urn:example:diploma:1", List.of()));
            assertThat(service.intendedUses()).isEmpty();
        });

        RegistrationCertificate registration = client.issueRegistrationCertificate(university.identifier());
        assertThat(registration.verifierInfo()).isNull();
        List<Map<String, Object>> issuerInfo = MAPPER.readValue(registration.issuerInfo(), new TypeReference<>() {});
        assertThat(issuerInfo).extracting(entry -> entry.get("format"))
                .containsExactly("registrar_dataset", "registration_cert");
    }

    @Test
    void relyingPartiesCanBeListedAndDeleted() {
        WalletClient client = wallet.client();
        RelyingParty shop = registerShop(client);

        assertThat(client.getRelyingParty(shop.identifier())).isEqualTo(shop);
        // the wallet's own issuer is registered too and comes first
        List<RelyingParty> parties = client.getRelyingParties();
        assertThat(parties.getFirst().services())
                .anyMatch(service -> !service.providedAttestations().isEmpty());
        assertThat(parties).contains(shop);

        client.deleteRelyingParty(shop.identifier());

        assertThat(client.getRelyingParties()).doesNotContain(shop);
        assertThatThrownBy(() -> client.getRelyingParty(shop.identifier()))
                .isInstanceOf(WalletClientException.class)
                .hasMessageContaining("404");
    }

    @Test
    void registrarCertificatesCanBeDownloaded() {
        WalletClient client = wallet.client();

        assertThat(client.getRegistrarCertificatePem()).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(client.getRelyingPartyAccessCaPem()).startsWith("-----BEGIN CERTIFICATE-----");
    }

    @Test
    void attestationTypesCanBeAddedToTheCatalogueAndDeleted() {
        WalletClient client = wallet.client();
        assertThat(client.getCatalogAttestations()).filteredOn(CatalogAttestation::template)
                .extracting(CatalogAttestation::name)
                .contains("EUDI PID", "Demo Event Ticket");

        CatalogAttestation diploma = client.addCatalogAttestation(CatalogEntry.named("University diploma")
                .credential(CredentialFormat.SD_JWT, "urn:example:diploma:1", "degree", "address.locality")
                .category("qeaa")
                .rulebook("https://example.com/diploma-rulebook")
                .levelOfSecurity("iso_18045_moderate")
                .trustedList("https://example.com/diploma-issuers"));
        try {
            assertThat(diploma.template()).isFalse();
            assertThat(diploma.category()).isEqualTo("qeaa");
            assertThat(diploma.credentials()).containsExactly(new RegisteredCredential(
                    CredentialFormat.SD_JWT, "urn:example:diploma:1", List.of("degree", "address.locality")));
            assertThat(client.getCatalogAttestations()).contains(diploma);

            AttestationSchema schema = client.getAttestationSchema(diploma.id());
            assertThat(schema).isEqualTo(diploma.schema());
            assertThat(schema.rulebookUri()).isEqualTo("https://example.com/diploma-rulebook");
            assertThat(schema.attestationLoS()).isEqualTo("iso_18045_moderate");
            assertThat(schema.bindingType()).isEqualTo("key");
            assertThat(schema.supportedFormats()).containsExactly("dc+sd-jwt");
            assertThat(schema.trustedAuthorities()).containsExactly(
                    new TrustedAuthority("etsi_tl", "https://example.com/diploma-issuers", true));
            assertThat(schema.schemaUris().get("dc+sd-jwt"))
                    .endsWith("/api/catalog/schemas/" + diploma.id() + "/dc+sd-jwt");
        } finally {
            client.deleteCatalogAttestation(diploma.id());
        }

        assertThat(client.getCatalogAttestations()).extracting(CatalogAttestation::id).doesNotContain(diploma.id());
    }

    @Test
    void predefinedCatalogueEntriesCannotBeDeleted() {
        WalletClient client = wallet.client();
        CatalogAttestation pid = client.getCatalogAttestations().stream()
                .filter(entry -> "EUDI PID".equals(entry.name()))
                .findFirst()
                .orElseThrow();

        assertThat(pid.credentials()).extracting(RegisteredCredential::type)
                .contains(PID_VCT, "eu.europa.ec.eudi.pid.1");
        assertThatThrownBy(() -> client.deleteCatalogAttestation(pid.id()))
                .isInstanceOf(WalletClientException.class)
                .hasMessageContaining("403");
    }

    private static RelyingParty registerShop(WalletClient client) {
        // the demo verifier's SD-JWT PID request asks for these two claims
        return client.registerRelyingParty(RelyingPartyRegistration.verifier("Example Shop")
                .purpose("Name check")
                .credential(CredentialFormat.SD_JWT, PID_VCT, "given_name", "family_name"));
    }

    private static String csr() throws Exception {
        return Files.readString(Path.of("src/test/resources/relying-party.csr"));
    }

    private static Map<String, Object> jwtHeader(String jwt) throws Exception {
        String header = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]), StandardCharsets.UTF_8);
        return MAPPER.readValue(header, new TypeReference<>() {});
    }

    /**
     * Creates an SD-JWT PID request with the wallet's built-in demo verifier,
     * signed with the given key and access certificate chain.
     */
    private static Map<String, Object> createVerifierRequest(String signingKey, String verifierInfo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "pid");
        body.put("format", "sd-jwt");
        body.put("signing_key", signingKey);
        body.put("verifier_info", MAPPER.readValue(verifierInfo, List.class));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(wallet.getBaseUrl() + "/verifier/api/requests"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return MAPPER.readValue(response.body(), new TypeReference<>() {});
    }

    private static Map<String, Object> verifierRequestStatus(String id) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(wallet.getBaseUrl() + "/verifier/api/requests/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return MAPPER.readValue(response.body(), new TypeReference<>() {});
    }
}
