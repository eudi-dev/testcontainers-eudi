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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WalletClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long POLL_INTERVAL_MILLIS = 50;

    private final String baseUrl;
    private final HttpClient httpClient;

    WalletClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Credential> getCredentials() {
        String body = get(baseUrl + "/api/credentials");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toCredential)
                    .map(this::withDetailWhenSummaryOnly)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse credentials response", e);
        }
    }

    /**
     * The listing carries a summary of each credential (a claim count and the
     * signature state), not the raw credential or its claims, which only the
     * per-credential detail holds. This reads the detail so callers still get a
     * complete {@link Credential}, and leaves an already complete summary alone.
     */
    private Credential withDetailWhenSummaryOnly(Credential summary) {
        if (summary.raw() != null || summary.id() == null) {
            return summary;
        }
        return getCredential(summary.id());
    }

    public Credential getCredential(String id) {
        String body = get(baseUrl + "/api/credentials/" + urlEncode(id));
        return toCredential(parseObject(body, "credential response"));
    }

    /**
     * Deletes all stored credentials and returns the number of deleted entries.
     */
    public int deleteAllCredentials() {
        String body = delete(baseUrl + "/api/credentials");
        Object deleted = parseObject(body, "delete-all response").get("deleted");
        return deleted instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Resolves the live status of a credential: from the wallet's own status
     * list when managed here, otherwise by fetching the external status list
     * referenced by the credential.
     */
    public CredentialStatusInfo getCredentialStatus(String id) {
        String body = get(baseUrl + "/api/credentials/" + urlEncode(id) + "/status");
        return toStatusInfo(parseObject(body, "credential status response"));
    }

    /**
     * Issues a credential with the wallet's issuer key and imports it into the
     * wallet, mirroring {@code eudi issue sdjwt|jwt|mdoc --wallet}.
     */
    public Credential issueCredential(IssueRequest request) {
        String body = postJson(baseUrl + "/api/issue", toJson(request.toBody()));
        return toCredential(parseObject(body, "issue response"));
    }

    /**
     * Returns all credential templates (pre-defined and user), including their
     * claims.
     */
    public List<CredentialTemplate> getTemplates() {
        String body = get(baseUrl + "/api/templates");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toTemplate)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse templates response", e);
        }
    }

    public CredentialTemplate getTemplate(String name) {
        String body = get(baseUrl + "/api/templates/" + urlEncode(name));
        return toTemplate(parseObject(body, "template response"));
    }

    /**
     * Creates or replaces a user template. Saving under a pre-defined
     * template's name overrides it.
     */
    public CredentialTemplate saveTemplate(CredentialTemplate template) {
        return saveTemplate(template, null);
    }

    /**
     * Saves a user template like {@link #saveTemplate(CredentialTemplate)}
     * and adds it to the attestation catalogue (since eudi-dev v3.0.0). Only
     * an SD-JWT VC template with a {@code vct} or an mdoc template with a
     * doctype can be added. If the catalogue refuses the entry, for example
     * because its name is taken, the wallet saves neither. Deleting the
     * template keeps the entry.
     */
    public CredentialTemplate saveTemplate(CredentialTemplate template, CatalogEntry catalogEntry) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (template.description() != null) {
            body.put("description", template.description());
        }
        if (template.format() != null) {
            body.put("format", template.format());
        }
        if (template.vct() != null) {
            body.put("vct", template.vct());
        }
        if (template.docType() != null) {
            body.put("doctype", template.docType());
        }
        if (template.namespace() != null) {
            body.put("namespace", template.namespace());
        }
        if (template.exp() != null) {
            body.put("exp", template.exp());
        }
        body.put("claims", template.claims());
        if (template.alwaysDisclosed() != null && !template.alwaysDisclosed().isEmpty()) {
            body.put("always_disclosed", template.alwaysDisclosed());
        }
        if (template.uniqueClaims() != null && !template.uniqueClaims().isEmpty()) {
            body.put("unique_claims", template.uniqueClaims());
        }
        if (catalogEntry != null) {
            body.put("catalog", catalogEntry.toBody());
        }
        String response = putJson(baseUrl + "/api/templates/" + urlEncode(template.name()), toJson(body));
        return toTemplate(parseObject(response, "template response"));
    }

    /**
     * Removes a user template. Pre-defined templates cannot be deleted;
     * deleting a user template that overrides one restores the pre-defined
     * version.
     */
    public void deleteTemplate(String name) {
        delete(baseUrl + "/api/templates/" + urlEncode(name));
    }

    /**
     * Returns the registrar's attestation catalogue with names and types
     * (since eudi-dev v3.0.0). Every predefined template is in it.
     */
    public List<CatalogAttestation> getCatalogAttestations() {
        String body = get(baseUrl + "/api/catalog/attestations");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toCatalogAttestation)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse catalogue response", e);
        }
    }

    /**
     * Adds an attestation type to the catalogue. The catalogue assigns its
     * schema id and schema URLs.
     */
    public CatalogAttestation addCatalogAttestation(CatalogEntry entry) {
        String body = postJson(baseUrl + "/api/catalog/attestations", toJson(entry.toBody()));
        return toCatalogAttestation(parseObject(body, "catalogue response"));
    }

    /**
     * Returns the {@code SchemaMeta} of a catalogue entry
     * ({@code GET /api/catalog/schemas/{id}}, EC TS11 v1.0 §5.3.1).
     */
    public AttestationSchema getAttestationSchema(String id) {
        Map<String, Object> response = parseObject(
                getJson(baseUrl + "/api/catalog/schemas/" + urlEncode(id)), "attestation schema response");
        return toAttestationSchema(asMap(response.get("data")));
    }

    /**
     * Deletes an added catalogue entry (EC TS11 v1.0 §5.3.3). The entries of
     * predefined templates can't be deleted.
     */
    public void deleteCatalogAttestation(String id) {
        delete(baseUrl + "/api/catalog/schemas/" + urlEncode(id));
    }

    /**
     * Returns the providers and external lists that were put on the wallet's
     * trusted lists (since eudi-dev v3.0.0).
     */
    public TrustState getTrustState() {
        Map<String, Object> raw = parseObject(get(baseUrl + "/api/trust"), "trust");
        return new TrustState(
                mapList(raw.get("entities"), e -> new TrustedEntity((String) e.get("id"), (String) e.get("list"), (String) e.get("name"))),
                mapList(raw.get("lists"), WalletClient::trustedList),
                (String) raw.get("lists_url"));
    }

    /**
     * Puts the CA certificates in PEM on one of the wallet's lists, for
     * example the CA of your issuer on the {@code pid} list. With
     * {@link EudiWalletContainer#withArf()} the CA then anchors the checks of
     * that list (since eudi-dev v3.0.0).
     */
    public TrustedEntity addTrustedEntity(String list, String name, String certificatesPem) {
        Map<String, Object> raw = parseObject(postJson(baseUrl + "/api/trust/entities",
                toJson(Map.of("list", list, "name", name == null ? "" : name, "certificates", certificatesPem))), "trusted entity");
        return new TrustedEntity((String) raw.get("id"), (String) raw.get("list"), (String) raw.get("name"));
    }

    /** Takes a provider off its list. */
    public void removeTrustedEntity(String id) {
        delete(baseUrl + "/api/trust/entities/" + urlEncode(id));
    }

    /**
     * Puts an external list of trusted entities (ETSI TS 119 602) on the
     * wallet's list of trusted lists. Its signer has to chain to the wallet
     * CA or to a CA from
     * {@link EudiWalletContainer#withTrustListCa(java.nio.file.Path)}. The
     * wallet follows the pointers of a list of trusted lists. In debug mode
     * the result says why the wallet can't use the list. Strict mode refuses
     * such a list.
     */
    public TrustedList addTrustedList(String url) {
        return trustedList(parseObject(postJson(baseUrl + "/api/trust/lists", toJson(Map.of("url", url))), "trusted list"));
    }

    private static TrustedList trustedList(Map<String, Object> raw) {
        return new TrustedList((String) raw.get("url"), Boolean.TRUE.equals(raw.get("configured")),
                (String) raw.get("via"), (String) raw.get("error"));
    }

    /** Takes an external list off the list of trusted lists. */
    public void removeTrustedList(String url) {
        delete(baseUrl + "/api/trust/lists?url=" + urlEncode(url));
    }

    /**
     * Returns the shared wallet CA certificate as PEM.
     */
    public String getCaCertificatePem() {
        return get(baseUrl + "/api/certificates/ca");
    }

    /**
     * Returns the shared wallet CA certificate as a JWKS document (public key
     * with {@code x5c} chain).
     */
    public String getCaCertificateJwks() {
        return get(baseUrl + "/api/certificates/ca?format=jwks");
    }

    /**
     * Returns the wallet's HTTPS leaf certificate as PEM.
     */
    public String getTlsCertificatePem() {
        return get(baseUrl + "/api/certificates/tls");
    }

    /**
     * Returns the wallet's HTTPS leaf certificate as a JWKS document (public
     * key with {@code x5c} chain).
     */
    public String getTlsCertificateJwks() {
        return get(baseUrl + "/api/certificates/tls?format=jwks");
    }

    /**
     * Returns the registrar certificate as PEM. It signs registration
     * certificates and registrar responses.
     */
    public String getRegistrarCertificatePem() {
        return get(baseUrl + "/api/certificates/registrar");
    }

    /**
     * Returns the relying party access CA as PEM. It signs the access
     * certificates of registered relying parties.
     */
    public String getRelyingPartyAccessCaPem() {
        return get(baseUrl + "/api/certificates/relying-party-access-ca");
    }

    /**
     * Registers a verifier or an issuer with the wallet's registrar
     * ({@code POST /api/registrar/wrp}, since eudi-dev v3.0.0). The returned
     * registration contains the assigned identifiers.
     */
    public RelyingParty registerRelyingParty(RelyingPartyRegistration registration) {
        String body = postJson(baseUrl + "/api/registrar/wrp", toJson(registration.toBody()));
        return toRelyingParty(parseObject(body, "relying party response"));
    }

    /**
     * Returns every registration in registration order. The demo issuer and the demo verifier come first.
     */
    public List<RelyingParty> getRelyingParties() {
        List<RelyingParty> parties = new ArrayList<>();
        String cursor = "0";
        while (cursor != null) {
            Map<String, Object> page = parseObject(
                    getJson(baseUrl + "/api/registrar/wrp?limit=100&cursor=" + urlEncode(cursor)),
                    "relying parties response");
            if (page.get("data") instanceof List<?> data) {
                data.forEach(entry -> parties.add(toRelyingParty(asMap(entry))));
            }
            cursor = (String) asMap(page.get("pagination")).get("next_cursor");
        }
        return parties;
    }

    public RelyingParty getRelyingParty(String identifier) {
        Map<String, Object> response = parseObject(
                getJson(baseUrl + "/api/registrar/wrp/" + urlEncode(identifier)), "relying party response");
        return toRelyingParty(asMap(response.get("data")));
    }

    /**
     * Deletes a registration and revokes its registration certificates.
     */
    public void deleteRelyingParty(String identifier) {
        delete(baseUrl + "/api/registrar/wrp/" + urlEncode(identifier));
    }

    /**
     * Issues an access certificate for the public key of a PEM certificate
     * signing request. The key must be P-256. Each DNS name adds an
     * {@code x509_san_dns} client identifier.
     */
    public AccessCertificate issueAccessCertificate(String identifier, String csrPem, String... dnsNames) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("identifier", identifier);
        body.put("csr", csrPem);
        if (dnsNames.length > 0) {
            body.put("dnsNames", List.of(dnsNames));
        }
        Map<String, Object> response = parseObject(
                postJson(baseUrl + "/api/registrar/access-certificates", toJson(body)),
                "access certificate response");
        return new AccessCertificate(
                (String) response.get("certificate"),
                (String) response.get("chain"),
                stringList(response.get("clientIds")));
    }

    /**
     * Issues a registration certificate for a verifier's intended use and
     * revokes the previous one. The result carries the
     * {@code verifier_info} value.
     */
    public RegistrationCertificate issueRegistrationCertificate(String identifier, String intendedUseIdentifier) {
        return issueRegistrationCertificate(Map.of(
                "identifier", identifier,
                "intendedUseIdentifier", intendedUseIdentifier));
    }

    /**
     * Issues a registration certificate for an issuer's service and revokes
     * the previous one. The relying party must have exactly one issuer
     * service. The result carries the {@code issuer_info} value.
     */
    public RegistrationCertificate issueRegistrationCertificate(String identifier) {
        return issueRegistrationCertificate(Map.of("identifier", identifier));
    }

    private RegistrationCertificate issueRegistrationCertificate(Map<String, Object> body) {
        Map<String, Object> response = parseObject(
                postJson(baseUrl + "/api/registrar/registration-certificates", toJson(body)),
                "registration certificate response");
        return new RegistrationCertificate(
                (String) response.get("registrationCertificate"),
                (String) response.get("verifierInfo"),
                (String) response.get("issuerInfo"));
    }

    /**
     * Returns the status list entries of the registration certificates
     * issued for a relying party.
     */
    public List<RegistrationCertificateStatus> getRegistrationCertificateStatuses(String identifier) {
        String body = get(baseUrl + "/api/registrar/registration-certificates?identifier=" + urlEncode(identifier));
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(status -> new RegistrationCertificateStatus(
                            asInt(status.get("index")),
                            (String) status.get("identifier"),
                            (String) status.get("intendedUse"),
                            (String) status.get("service"),
                            Boolean.TRUE.equals(status.get("revoked")),
                            Boolean.TRUE.equals(status.get("superseded")),
                            status.get("expires") instanceof Number expires ? expires.longValue() : 0))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse registration certificate statuses", e);
        }
    }

    /**
     * Revokes every registration certificate of a relying party and returns
     * the number of changed certificates.
     */
    public int revokeRegistrationCertificates(String identifier) {
        return setRegistrationCertificatesRevoked(identifier, null, true);
    }

    /**
     * Revokes the registration certificates of one intended use and returns
     * the number of changed certificates.
     */
    public int revokeRegistrationCertificates(String identifier, String intendedUseIdentifier) {
        return setRegistrationCertificatesRevoked(identifier, intendedUseIdentifier, true);
    }

    /**
     * Makes every revoked registration certificate of a relying party valid
     * again and returns the number of changed certificates. Superseded
     * certificates stay revoked.
     */
    public int activateRegistrationCertificates(String identifier) {
        return setRegistrationCertificatesRevoked(identifier, null, false);
    }

    /**
     * Makes the revoked registration certificates of one intended use valid
     * again and returns the number of changed certificates.
     */
    public int activateRegistrationCertificates(String identifier, String intendedUseIdentifier) {
        return setRegistrationCertificatesRevoked(identifier, intendedUseIdentifier, false);
    }

    private int setRegistrationCertificatesRevoked(String identifier, String intendedUseIdentifier, boolean revoked) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("identifier", identifier);
        if (intendedUseIdentifier != null) {
            body.put("intendedUseIdentifier", intendedUseIdentifier);
        }
        body.put("revoked", revoked);
        Map<String, Object> response = parseObject(
                postJson(baseUrl + "/api/registrar/registration-certificates/status", toJson(body)),
                "registration certificate status response");
        return asInt(response.get("changed"));
    }

    /**
     * Returns the wallet's activity log, useful for asserting what the wallet
     * actually did during a flow.
     */
    public List<ActivityLogEntry> getActivityLog() {
        String body = get(baseUrl + "/api/log");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toActivityLogEntry)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse activity log response", e);
        }
    }

    public void clearActivityLog() {
        delete(baseUrl + "/api/log");
    }

    /**
     * Returns the pending consent requests of a wallet running without
     * auto-accept.
     *
     * <p>Plain {@link #acceptCredentialOffer(String)} and
     * {@link #acceptPresentationRequest(String)} submissions accept outright
     * even without auto-accept, because the API call itself is the caller's
     * decision. Use {@link #submitCredentialOfferForConsent(String)} or
     * {@link #submitPresentationRequestForConsent(String)} to produce a
     * pending request.
     */
    public List<ConsentRequest> getPendingRequests() {
        String body = get(baseUrl + "/api/requests");
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(body, new TypeReference<>() {});
            return raw.stream()
                    .map(WalletClient::toConsentRequest)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse pending requests response", e);
        }
    }

    /**
     * Approves a pending consent request with the wallet's own selection,
     * disclosing all matched claims, and waits for the submission result.
     */
    public ApprovalResult approveRequest(String id) {
        return approveRequest(id, ConsentApproval.approval());
    }

    /**
     * Approves a pending consent request, disclosing only the selected claims
     * per credential id, and waits for the submission result.
     */
    public ApprovalResult approveRequest(String id, Map<String, List<String>> selectedClaims) {
        ConsentApproval approval = ConsentApproval.approval();
        if (selectedClaims != null) {
            selectedClaims.forEach((credentialId, claims) ->
                    approval.selectClaims(credentialId, claims == null ? List.of() : claims));
        }
        return approveRequest(id, approval);
    }

    /**
     * Approves a pending consent request with the given selection and waits
     * for the submission result. The approval can present other credentials
     * than the wallet's first choice, referencing the request's
     * {@link ConsentRequest#credentialOptions()}, and can disclose only
     * selected claims per credential.
     */
    public ApprovalResult approveRequest(String id, ConsentApproval approval) {
        return toApprovalResult(postJson(baseUrl + "/api/requests/" + urlEncode(id) + "/approve",
                toJson(approval.toBody())));
    }

    /**
     * Approves like {@link #approveRequest(String)}, returning the result
     * asynchronously. Use this when the approval unblocks a further consent
     * step: approving an offer whose issuer demands a presentation
     * (OpenID4VCI 1.1 interactive authorization) returns only once that
     * presentation request is answered too, so the approval and the answer
     * must not wait on each other.
     */
    public CompletableFuture<ApprovalResult> approveRequestAsync(String id) {
        return approveRequestAsync(id, ConsentApproval.approval());
    }

    /**
     * Approves with a selection like {@link #approveRequest(String, ConsentApproval)},
     * returning the result asynchronously.
     */
    public CompletableFuture<ApprovalResult> approveRequestAsync(String id, ConsentApproval approval) {
        return postJsonAsync(baseUrl + "/api/requests/" + urlEncode(id) + "/approve",
                toJson(approval.toBody()))
                .thenApply(WalletClient::toApprovalResult);
    }

    private static ApprovalResult toApprovalResult(String response) {
        Map<String, Object> parsed = parseObject(response, "approval response");
        return new ApprovalResult(
                (String) parsed.get("status"),
                (String) parsed.get("redirect_uri"),
                (String) parsed.get("error")
        );
    }

    /**
     * Denies a pending consent request.
     */
    public void denyRequest(String id) {
        postJson(baseUrl + "/api/requests/" + urlEncode(id) + "/deny", toJson(Map.of()));
    }

    /**
     * Waits for a consent request to appear and returns it, polling
     * {@link #getPendingRequests()}. Use after
     * {@link #submitCredentialOfferForConsent(String)} or
     * {@link #submitPresentationRequestForConsent(String)}, which only register
     * the request once the wallet has parsed the offer or request object.
     *
     * @throws WalletClientException if no request appears within the timeout
     */
    public ConsentRequest awaitPendingRequest(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            List<ConsentRequest> pending = getPendingRequests();
            if (!pending.isEmpty()) {
                return pending.getFirst();
            }
            if (System.nanoTime() >= deadline) {
                throw new WalletClientException("No consent request appeared within " + timeout);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WalletClientException("Interrupted while waiting for a consent request", e);
            }
        }
    }

    /**
     * Returns the wallet instance's introspection document.
     */
    public WalletConfig getConfig() {
        Map<String, Object> raw = parseObject(get(baseUrl + "/api/config"), "config response");
        return new WalletConfig(
                asInt(raw.get("pid")),
                asInt(raw.get("port")),
                (String) raw.get("build_id"),
                (String) raw.get("version"),
                (String) raw.get("wallet_dir"),
                (String) raw.get("templates_dir"),
                (String) raw.get("base_url"),
                (String) raw.get("issuer_url"),
                (String) raw.get("status_list_url"),
                (String) raw.get("preferred_format"),
                (String) raw.get("validation_mode"),
                (String) raw.get("vci_version"),
                (String) raw.get("key_attestation_level"),
                Boolean.TRUE.equals(raw.get("auto_accept")),
                (String) raw.get("session_transcript"),
                Boolean.TRUE.equals(raw.get("require_haip")),
                Boolean.TRUE.equals(raw.get("require_haip_issuance")),
                Boolean.TRUE.equals(raw.get("require_arf")),
                Boolean.TRUE.equals(raw.get("require_encrypted_request")),
                Boolean.TRUE.equals(raw.get("force_client_attestation")),
                asInt(raw.get("credential_count")),
                Boolean.TRUE.equals(raw.get("tls_listener"))
        );
    }

    public String getTrustList() {
        return get(baseUrl + "/api/trustlist");
    }

    public String getTrustListById(String id) {
        return get(baseUrl + "/api/trustlists/" + urlEncode(id));
    }

    public String getTrustListForVct(String vct) {
        return get(baseUrl + "/api/trustlist?vct=" + urlEncode(vct));
    }

    public String getTrustListForDocType(String docType) {
        return get(baseUrl + "/api/trustlist?doctype=" + urlEncode(docType));
    }

    public List<TrustListIndexEntry> getTrustLists() {
        String body = get(baseUrl + "/api/trustlists");
        try {
            Map<String, Object> raw = MAPPER.readValue(body, new TypeReference<>() {});
            Object trustLists = raw.get("trust_lists");
            if (!(trustLists instanceof List<?> entries)) {
                throw new WalletClientException("Failed to parse trust-list index response");
            }
            return entries.stream()
                    .map(WalletClient::toTrustListIndexEntry)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse trust-list index response", e);
        }
    }

    public String getStatusList() {
        return get(baseUrl + "/api/statuslist");
    }

    public void setNextError(String error, String errorDescription) {
        postJson(baseUrl + "/api/next-error", toJson(Map.of("error", error, "error_description", errorDescription)));
    }

    public void clearNextError() {
        delete(baseUrl + "/api/next-error");
    }

    public void setPreferredFormat(CredentialFormat format) {
        putJson(baseUrl + "/api/config/preferred-format", toJson(Map.of("format", format.getWireValue())));
    }

    public void clearPreferredFormat() {
        putJson(baseUrl + "/api/config/preferred-format", toJson(Map.of("format", "")));
    }

    /**
     * Changes the wallet's validation mode at runtime, part of the
     * conformance settings ({@code PUT /api/config/conformance}). Holds until
     * the process restarts or {@link #resetConformance()}.
     */
    public void setValidationMode(ValidationMode mode) {
        putConformance("mode", mode.getWireValue());
    }

    /**
     * Changes the wallet's OpenID4VCI feature level at runtime, part of the
     * conformance settings ({@code PUT /api/config/conformance}, since
     * eudi-dev v1.23.0). See {@link VciVersion} for what
     * {@link VciVersion#V1_1} enables. Holds until the process restarts or
     * {@link #resetConformance()}.
     */
    public void setVciVersion(VciVersion version) {
        putConformance("vci_version", version.getWireValue());
    }

    /**
     * Toggles HAIP 1.0 enforcement at runtime, part of the conformance
     * settings ({@code PUT /api/config/conformance}). Holds until the process
     * restarts or {@link #resetConformance()}.
     */
    public void setRequireHaip(boolean required) {
        putConformance("haip", required);
    }

    /**
     * Toggles the ARF checks of access and registration certificates at
     * runtime, part of the conformance settings
     * ({@code PUT /api/config/conformance}, since eudi-dev v3.0.0). Holds
     * until the process restarts or {@link #resetConformance()}.
     */
    public void setRequireArf(boolean required) {
        putConformance("arf", required);
    }

    /**
     * Changes the key storage and user authentication claims of the wallet's
     * key attestations at runtime, part of the conformance settings
     * ({@code PUT /api/config/conformance}). Holds until the process restarts
     * or {@link #resetConformance()}.
     */
    public void setKeyAttestationLevel(KeyAttestationLevel level) {
        putConformance("key_attestation_level", level.getWireValue());
    }

    /**
     * Toggles the encrypted-request-object requirement at runtime, part of
     * the conformance settings ({@code PUT /api/config/conformance}). Holds
     * until the process restarts or {@link #resetConformance()}.
     */
    public void setRequireEncryptedRequest(boolean required) {
        putConformance("encrypted", required);
    }

    /**
     * Restores the startup values of the conformance settings (validation
     * mode, HAIP, ARF, encrypted requests, OpenID4VCI feature level, key
     * attestation level)
     * ({@code DELETE /api/config/conformance}).
     */
    public void resetConformance() {
        delete(baseUrl + "/api/config/conformance");
    }

    private void putConformance(String key, Object value) {
        putJson(baseUrl + "/api/config/conformance", toJson(Map.of(key, value)));
    }

    public void importCredential(String rawCredential) {
        postRaw(baseUrl + "/api/credentials", rawCredential);
    }

    public void setCredentialStatus(String credentialId, int status) {
        postJson(baseUrl + "/api/credentials/" + credentialId + "/status", toJson(Map.of("status", status)));
    }

    public void revokeCredential(String credentialId) {
        setCredentialStatus(credentialId, 1);
    }

    public void unrevokeCredential(String credentialId) {
        setCredentialStatus(credentialId, 0);
    }

    /**
     * Submits an OID4VP request to the wallet and returns its response. The
     * submission auto-accepts regardless of the wallet's auto-accept setting —
     * the API call is the caller's consent. Use
     * {@link #submitPresentationRequestForConsent(String)} to exercise the
     * consent dialog instead.
     */
    public PresentationResponse acceptPresentationRequest(String uri) {
        String body = postJson(baseUrl + "/api/presentations", toJson(Map.of("uri", uri)));
        return toPresentationResponse(body);
    }

    /**
     * Submits an OID4VP request as an interactive one, so a wallet running
     * without auto-accept raises a consent request instead of accepting
     * outright.
     *
     * <p>The wallet holds the submission open until the request is decided, so
     * the result is returned asynchronously: wait for the request with
     * {@link #awaitPendingRequest(Duration)}, resolve it with
     * {@link #approveRequest(String)} or {@link #denyRequest(String)}, then
     * join the returned future for the presentation response.
     */
    public CompletableFuture<PresentationResponse> submitPresentationRequestForConsent(String uri) {
        return postJsonAsync(baseUrl + "/api/presentations", toJson(Map.of("uri", uri, "interactive", true)))
                .thenApply(WalletClient::toPresentationResponse);
    }

    /**
     * Submits a credential offer to the wallet and returns its response. The
     * submission auto-accepts regardless of the wallet's auto-accept setting —
     * the API call is the caller's consent. Use
     * {@link #submitCredentialOfferForConsent(String)} to exercise the consent
     * dialog instead.
     */
    public OfferResponse acceptCredentialOffer(String uri) {
        String body = postJson(baseUrl + "/api/offers", toJson(Map.of("uri", uri)));
        return new OfferResponse(body);
    }

    /**
     * Accepts a credential offer that requires a transaction code, supplying
     * the code the issuer delivered separately (OpenID4VCI §4.1.1). Without
     * the code the wallet refuses such an offer.
     */
    public OfferResponse acceptCredentialOffer(String uri, String txCode) {
        String body = postJson(baseUrl + "/api/offers", toJson(Map.of("uri", uri, "tx_code", txCode)));
        return new OfferResponse(body);
    }

    /**
     * Submits a credential offer as an interactive one, so a wallet running
     * without auto-accept raises a consent request instead of importing the
     * credential outright.
     *
     * <p>The wallet holds the submission open until the request is decided, so
     * the result is returned asynchronously: wait for the request with
     * {@link #awaitPendingRequest(Duration)}, resolve it with
     * {@link #approveRequest(String)} or {@link #denyRequest(String)}, then
     * join the returned future for the offer response.
     */
    public CompletableFuture<OfferResponse> submitCredentialOfferForConsent(String uri) {
        return postJsonAsync(baseUrl + "/api/offers", toJson(Map.of("uri", uri, "interactive", true)))
                .thenApply(OfferResponse::new);
    }

    public void deleteCredential(String id) {
        delete(baseUrl + "/api/credentials/" + id);
    }

    public boolean hasCredentialWithType(String type) {
        return getCredentials().stream()
                .anyMatch(c -> type.equals(c.type()));
    }

    public List<Credential> getCredentialsByType(String type) {
        return getCredentials().stream()
                .filter(c -> type.equals(c.type()))
                .collect(Collectors.toList());
    }

    public void deleteCredentialsByType(String type) {
        getCredentials().stream()
                .filter(c -> type.equals(c.type()))
                .forEach(c -> deleteCredential(c.id()));
    }

    private String get(String url) {
        return sendRequest(HttpRequest.newBuilder().uri(URI.create(url)).GET().build());
    }

    /**
     * The registrar signs its read responses unless the client asks for JSON
     * only (TS05 v1.5 §3.2).
     */
    private String getJson(String url) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build());
    }

    private String postJson(String url, String body) {
        return sendRequest(postJsonRequest(url, body));
    }

    private CompletableFuture<String> postJsonAsync(String url, String body) {
        return sendRequestAsync(postJsonRequest(url, body));
    }

    private static HttpRequest postJsonRequest(String url, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String postRaw(String url, String body) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String putJson(String url, String body) {
        return sendRequest(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private String delete(String url) {
        return sendRequest(HttpRequest.newBuilder().uri(URI.create(url)).DELETE().build());
    }

    private String sendRequest(HttpRequest request) {
        try {
            return requireSuccess(request, httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException | InterruptedException e) {
            throw new WalletClientException("HTTP request failed: " + request.method() + " " + request.uri(), e);
        }
    }

    private CompletableFuture<String> sendRequestAsync(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw new WalletClientException("HTTP request failed: " + request.method()
                                + " " + request.uri(), throwable);
                    }
                    return requireSuccess(request, response);
                });
    }

    private static String requireSuccess(HttpRequest request, HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            Map<String, Object> body;
            try {
                body = asMap(MAPPER.readValue(response.body(), Object.class));
            } catch (IOException e) {
                body = Map.of();
            }
            throw new WalletClientException(
                    "HTTP " + response.statusCode() + " " + request.method()
                            + " " + request.uri() + ": " + response.body(),
                    response.statusCode(),
                    body.get("error") instanceof String error ? error : null,
                    body.get("error_description") instanceof String description ? description : null);
        }
        return response.body();
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (IOException e) {
            throw new WalletClientException("Failed to serialize request body", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Credential toCredential(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        CredentialFormat format = CredentialFormat.fromWireValue((String) raw.get("format"));
        String type = (String) raw.get("type");
        if (type == null) {
            type = (String) raw.get("vct");
        }
        if (type == null) {
            type = (String) raw.get("doctype");
        }
        Map<String, Object> claims = (Map<String, Object>) raw.getOrDefault("claims", Map.of());
        CredentialStatusInfo status = raw.get("status") instanceof Map<?, ?> statusMap
                ? toStatusInfo((Map<String, Object>) statusMap)
                : null;
        return new Credential(id, format, type, claims, (String) raw.get("raw"), status);
    }

    private static CredentialStatusInfo toStatusInfo(Map<String, Object> raw) {
        return new CredentialStatusInfo(
                Boolean.TRUE.equals(raw.get("managed")),
                raw.get("status") instanceof Number status ? status.intValue() : null,
                (String) raw.get("source"),
                (String) raw.get("uri"),
                raw.get("idx") instanceof Number idx ? idx.intValue() : null
        );
    }

    @SuppressWarnings("unchecked")
    private static CredentialTemplate toTemplate(Map<String, Object> raw) {
        return new CredentialTemplate(
                (String) raw.get("name"),
                (String) raw.get("description"),
                (String) raw.get("format"),
                (String) raw.get("vct"),
                (String) raw.get("doctype"),
                (String) raw.get("namespace"),
                (String) raw.get("exp"),
                (Map<String, Object>) raw.getOrDefault("claims", Map.of()),
                (List<String>) raw.getOrDefault("always_disclosed", List.of()),
                (List<String>) raw.getOrDefault("unique_claims", List.of()),
                Boolean.TRUE.equals(raw.get("predefined"))
        );
    }

    @SuppressWarnings("unchecked")
    private static ActivityLogEntry toActivityLogEntry(Map<String, Object> raw) {
        return new ActivityLogEntry(
                (String) raw.get("time"),
                (String) raw.get("action"),
                (String) raw.get("detail"),
                Boolean.TRUE.equals(raw.get("success")),
                (Map<String, Object>) raw.getOrDefault("details", Map.of())
        );
    }

    @SuppressWarnings("unchecked")
    private static ConsentRequest toConsentRequest(Map<String, Object> raw) {
        List<CredentialMatch> matched = credentialMatches(raw.get("matched_credentials"));
        List<String> purposes = stringList(raw.get("purposes"));
        List<String> privacyPolicies = stringList(raw.get("privacy_policies"));
        ConsentCredentialOptions options = raw.get("credential_options") instanceof Map<?, ?> rawOptions
                ? toConsentCredentialOptions((Map<String, Object>) rawOptions)
                : null;
        return new ConsentRequest(
                (String) raw.get("id"),
                (String) raw.get("type"),
                (String) raw.get("status"),
                (String) raw.get("client_id"),
                (String) raw.get("created_at"),
                matched,
                purposes,
                privacyPolicies,
                options,
                stringList(raw.get("findings"))
        );
    }

    @SuppressWarnings("unchecked")
    private static ConsentCredentialOptions toConsentCredentialOptions(Map<String, Object> raw) {
        List<ConsentSetOptions> sets = raw.get("sets") instanceof List<?> rawSets
                ? rawSets.stream()
                        .map(set -> (Map<String, Object>) set)
                        .map(set -> new ConsentSetOptions(
                                set.get("options") instanceof List<?> options
                                        ? (List<List<String>>) options
                                        : List.of(),
                                Boolean.TRUE.equals(set.get("optional")),
                                set.get("unmatched") instanceof List<?> unmatched
                                        ? (List<Integer>) unmatched
                                        : List.of()))
                        .collect(Collectors.toList())
                : List.of();
        List<ConsentQueryOptions> queries = raw.get("queries") instanceof List<?> rawQueries
                ? rawQueries.stream()
                        .map(query -> (Map<String, Object>) query)
                        .map(query -> new ConsentQueryOptions(
                                (String) query.get("id"),
                                credentialMatches(query.get("candidates")),
                                Boolean.TRUE.equals(query.get("multiple")),
                                credentialMatches(query.get("non_matching"))))
                        .collect(Collectors.toList())
                : List.of();
        return new ConsentCredentialOptions(sets, queries);
    }

    @SuppressWarnings("unchecked")
    private static CredentialMatch toCredentialMatch(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new WalletClientException("Failed to parse credential match");
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return new CredentialMatch(
                (String) map.get("query_id"),
                (String) map.get("credential_id"),
                (String) map.get("format"),
                (String) map.get("vct"),
                (String) map.get("doctype"),
                map.get("claims") instanceof Map<?, ?> claims
                        ? (Map<String, Object>) claims
                        : Map.of(),
                stringList(map.get("selected_keys")),
                Boolean.TRUE.equals(map.get("untrusted_authority")),
                stringList(map.get("empty_array_claims")),
                stringList(map.get("missing_claims")),
                stringList(map.get("mismatches")),
                mapList(map.get("claim_sets"), claimSet -> new ConsentClaimSet(
                        asInt(claimSet.get("index")),
                        stringList(claimSet.get("keys")),
                        asMap(claimSet.get("claims"))))
        );
    }

    private static List<CredentialMatch> credentialMatches(Object raw) {
        return raw instanceof List<?> list
                ? list.stream().map(WalletClient::toCredentialMatch).collect(Collectors.toList())
                : List.of();
    }

    private static RelyingParty toRelyingParty(Map<String, Object> raw) {
        List<?> identifiers = raw.get("identifier") instanceof List<?> list ? list : List.of();
        List<String> legalNames = stringList(asMap(raw.get("legalPerson")).get("legalName"));
        return new RelyingParty(
                identifiers.isEmpty() ? null : (String) asMap(identifiers.getFirst()).get("identifier"),
                (String) raw.get("tradeName"),
                legalNames.isEmpty() ? null : legalNames.getFirst(),
                (String) raw.get("country"),
                (String) raw.get("registryURI"),
                mapList(raw.get("services"), WalletClient::toRelyingPartyService));
    }

    private static RelyingPartyService toRelyingPartyService(Map<String, Object> raw) {
        return new RelyingPartyService(
                (String) raw.get("serviceIdentifier"),
                (String) raw.get("serviceTradeName"),
                stringList(raw.get("entitlements")),
                mapList(raw.get("providesAttestations"), WalletClient::toRegisteredCredential),
                mapList(raw.get("intendedUses"), use -> new IntendedUse(
                        (String) use.get("intendedUseIdentifier"),
                        firstField(use.get("purpose"), "content"),
                        firstField(use.get("privacyPolicy"), "policyURI"),
                        mapList(use.get("credentials"), WalletClient::toRegisteredCredential))));
    }

    private static RegisteredCredential toRegisteredCredential(Map<String, Object> raw) {
        CredentialFormat format = CredentialFormat.fromWireValue((String) raw.get("format"));
        // A provided attestation names its type (TS05 ProvidedAttestation), a
        // registered credential names it in its DCQL meta.
        Map<String, Object> meta = asMap(raw.get("meta"));
        List<String> vcts = stringList(meta.get("vct_values"));
        String type = raw.get("type") instanceof String provided ? provided
                : vcts.isEmpty() ? (String) meta.get("doctype_value") : vcts.getFirst();
        List<String> claims = mapList(raw.get("claims"), claim -> claim.get("path") instanceof List<?> path
                ? ClaimPaths.toClaim(format, type, path)
                : null);
        return new RegisteredCredential(format, type, claims);
    }

    private static CatalogAttestation toCatalogAttestation(Map<String, Object> raw) {
        return new CatalogAttestation(
                (String) raw.get("name"),
                mapList(raw.get("credentials"), credential -> {
                    CredentialFormat format = CredentialFormat.fromWireValue((String) credential.get("format"));
                    String type = (String) credential.get("type");
                    List<String> claims = credential.get("claims") instanceof List<?> paths
                            ? paths.stream()
                                    .map(path -> ClaimPaths.toClaim(format, type, (List<?>) path))
                                    .collect(Collectors.toList())
                            : List.of();
                    return new RegisteredCredential(format, type, claims);
                }),
                toAttestationSchema(asMap(raw.get("schema"))),
                (String) raw.get("category"),
                Boolean.TRUE.equals(raw.get("template")));
    }

    private static AttestationSchema toAttestationSchema(Map<String, Object> raw) {
        Map<String, String> schemaUris = new LinkedHashMap<>();
        mapList(raw.get("schemaURIs"), Function.identity())
                .forEach(uri -> schemaUris.put((String) uri.get("formatIdentifier"), (String) uri.get("uri")));
        return new AttestationSchema(
                (String) raw.get("id"),
                (String) raw.get("version"),
                (String) raw.get("rulebookURI"),
                mapList(raw.get("trustedAuthorities"), authority -> new TrustedAuthority(
                        (String) authority.get("frameworkType"),
                        (String) authority.get("value"),
                        Boolean.TRUE.equals(authority.get("isLOTE")))),
                (String) raw.get("attestationLoS"),
                (String) raw.get("bindingType"),
                stringList(raw.get("supportedFormats")),
                schemaUris);
    }

    private static String firstField(Object list, String field) {
        return list instanceof List<?> entries && !entries.isEmpty()
                ? (String) asMap(entries.getFirst()).get(field)
                : null;
    }

    private static <T> List<T> mapList(Object raw, Function<Map<String, Object>, T> mapper) {
        return raw instanceof List<?> list
                ? list.stream().map(WalletClient::asMap).map(mapper).collect(Collectors.toList())
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static PresentationResponse toPresentationResponse(String body) {
        try {
            Map<String, Object> parsed = MAPPER.readValue(body, new TypeReference<>() {});
            String redirectUri = (String) parsed.get("redirect_uri");
            String idToken = (String) parsed.get("id_token");
            if (redirectUri == null) {
                Object response = parsed.get("response");
                if (response instanceof Map<?, ?> responseMap) {
                    redirectUri = (String) responseMap.get("redirect_uri");
                    if (idToken == null) {
                        idToken = (String) responseMap.get("id_token");
                    }
                }
            }
            return new PresentationResponse((String) parsed.get("status"), redirectUri, idToken,
                    (String) parsed.get("error"), (String) parsed.get("error_description"),
                    stringList(parsed.get("findings")), body);
        } catch (IOException e) {
            return new PresentationResponse(null, null, null, null, null, List.of(), body);
        }
    }

    private static Map<String, Object> parseObject(String body, String description) {
        try {
            return MAPPER.readValue(body, new TypeReference<>() {});
        } catch (IOException e) {
            throw new WalletClientException("Failed to parse " + description, e);
        }
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static TrustListIndexEntry toTrustListIndexEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new WalletClientException("Failed to parse trust-list index entry");
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        // "attestations" may be an explicit null (e.g. the wallet-provider
        // profile, which anchors wallet attestations rather than credentials)
        List<TrustListAttestation> attestations = map.get("attestations") instanceof List<?> rawAttestations
                ? rawAttestations.stream()
                        .map(WalletClient::toTrustListAttestation)
                        .collect(Collectors.toList())
                : List.of();
        return new TrustListIndexEntry(
                (String) map.get("id"),
                Boolean.TRUE.equals(map.get("default")),
                (String) map.get("path"),
                (String) map.get("loTEType"),
                (String) map.get("entityName"),
                (String) map.get("issuanceServiceType"),
                (String) map.get("revocationServiceType"),
                attestations,
                (String) map.get("advertised_url"),
                (String) map.get("url")
        );
    }

    @SuppressWarnings("unchecked")
    private static TrustListAttestation toTrustListAttestation(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            throw new WalletClientException("Failed to parse trust-list attestation");
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return new TrustListAttestation(
                (String) map.get("format"),
                (String) map.get("vct"),
                (String) map.get("doctype"),
                (List<String>) map.getOrDefault("entitlements", List.of()),
                (String) map.get("trust_list_type"),
                (String) map.get("status_determination_approach"),
                (String) map.get("scheme_type_community_rules"),
                (String) map.get("scheme_territory"),
                (String) map.get("entity_name"),
                (String) map.get("issuance_service_type"),
                (String) map.get("revocation_service_type"),
                (String) map.get("issuance_service_name"),
                (String) map.get("revocation_service_name")
        );
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
