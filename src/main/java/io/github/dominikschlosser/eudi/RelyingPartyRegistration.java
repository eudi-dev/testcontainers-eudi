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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request for {@link WalletClient#registerRelyingParty(RelyingPartyRegistration)}:
 * registers a verifier or an issuer with the wallet's registrar (since
 * eudi-dev v3.0.0). The registration has one service. A verifier's service
 * has one intended use with its permitted credentials and claims.
 *
 * <pre>{@code
 * RelyingParty shop = client.registerRelyingParty(RelyingPartyRegistration.verifier("Example Shop")
 *         .purpose("Age check")
 *         .credential(CredentialFormat.SD_JWT, "urn:eudi:pid:1", "age_equal_or_over.18"));
 *
 * RelyingParty university = client.registerRelyingParty(RelyingPartyRegistration.issuer("Example University")
 *         .attestation(CredentialFormat.SD_JWT, "urn:example:diploma:1"));
 * }</pre>
 *
 * <p>The registrar fills in missing fields: the identifier, the legal name,
 * the country, the support URL and the privacy policy URL.
 */
public class RelyingPartyRegistration {

    private final String name;
    private String identifier;
    private String legalName;
    private String country;
    private String supportUri;
    private String serviceIdentifier;
    private ProviderEntitlement entitlement;
    private String purpose;
    private String privacyPolicy;
    private final List<RegisteredCredential> credentials = new ArrayList<>();
    private final List<RegisteredCredential> attestations = new ArrayList<>();

    private RelyingPartyRegistration(String name) {
        this.name = name;
    }

    /** Registers a verifier with the given trade name. */
    public static RelyingPartyRegistration verifier(String name) {
        return new RelyingPartyRegistration(name);
    }

    /**
     * Registers an issuer with the given trade name and the
     * {@link ProviderEntitlement#NON_Q_EAA_PROVIDER} entitlement.
     */
    public static RelyingPartyRegistration issuer(String name) {
        RelyingPartyRegistration registration = new RelyingPartyRegistration(name);
        registration.entitlement = ProviderEntitlement.NON_Q_EAA_PROVIDER;
        return registration;
    }

    /**
     * Sets the {@code organizationIdentifier}, such as
     * {@code LEIXG-5299000ABCDEF12345}: the identifier type ({@code LEI},
     * {@code NTR}, {@code VAT}, {@code EOR} or {@code EXC}), a country code,
     * a dash and the value (ETSI TS 119 475 §5.1.3).
     */
    public RelyingPartyRegistration identifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    public RelyingPartyRegistration legalName(String legalName) {
        this.legalName = legalName;
        return this;
    }

    /** Two-letter country code. */
    public RelyingPartyRegistration country(String country) {
        this.country = country;
        return this;
    }

    public RelyingPartyRegistration supportUri(String supportUri) {
        this.supportUri = supportUri;
        return this;
    }

    public RelyingPartyRegistration serviceIdentifier(String serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
        return this;
    }

    /** Sets an issuer's entitlement. */
    public RelyingPartyRegistration entitlement(ProviderEntitlement entitlement) {
        this.entitlement = entitlement;
        return this;
    }

    /** The purpose of the intended use. The consent dialog shows it. */
    public RelyingPartyRegistration purpose(String purpose) {
        this.purpose = purpose;
        return this;
    }

    /** The privacy policy URL of the intended use. */
    public RelyingPartyRegistration privacyPolicy(String privacyPolicy) {
        this.privacyPolicy = privacyPolicy;
        return this;
    }

    /**
     * Adds a permitted credential to the intended use, with at least one
     * claim. SD-JWT claims are dotted paths such as {@code address.locality}.
     * mdoc claims are element names in the doctype's namespace, or
     * {@code namespace:element} for another namespace. With ARF checks a
     * request may only ask for registered claims (ARF RPRC_21).
     */
    public RelyingPartyRegistration credential(CredentialFormat format, String type, String... claims) {
        credentials.add(new RegisteredCredential(format, type, List.of(claims)));
        return this;
    }

    /** Adds an attestation type of an issuer. */
    public RelyingPartyRegistration attestation(CredentialFormat format, String type) {
        attestations.add(new RegisteredCredential(format, type, List.of()));
        return this;
    }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tradeName", name);
        if (identifier != null) {
            body.put("identifier", List.of(Map.of("identifier", identifier)));
        }
        if (legalName != null) {
            body.put("legalPerson", Map.of("legalName", List.of(legalName)));
        }
        if (country != null) {
            body.put("country", country);
        }
        Map<String, Object> service = new LinkedHashMap<>();
        if (serviceIdentifier != null) {
            service.put("serviceIdentifier", serviceIdentifier);
        }
        if (supportUri != null) {
            service.put("supportURI", List.of(supportUri));
        }
        if (entitlement != null) {
            service.put("entitlements", List.of(entitlement.getWireValue()));
        }
        if (!attestations.isEmpty()) {
            service.put("providesAttestations", attestations.stream()
                    .map(attestation -> Map.of(
                            "format", attestation.format().getWireValue(),
                            "meta", meta(attestation)))
                    .toList());
        }
        if (!credentials.isEmpty()) {
            service.put("intendedUses", List.of(intendedUse()));
        }
        body.put("services", List.of(service));
        return body;
    }

    private Map<String, Object> intendedUse() {
        Map<String, Object> use = new LinkedHashMap<>();
        if (purpose != null) {
            use.put("purpose", List.of(Map.of("lang", "en", "content", purpose)));
        }
        if (privacyPolicy != null) {
            use.put("privacyPolicy", List.of(Map.of("policyURI", privacyPolicy)));
        }
        use.put("credentials", credentials.stream()
                .map(credential -> Map.of(
                        "format", credential.format().getWireValue(),
                        "meta", meta(credential),
                        "claims", credential.claims().stream()
                                .map(claim -> Map.of("path",
                                        ClaimPaths.toPath(credential.format(), credential.type(), claim)))
                                .toList()))
                .toList());
        return use;
    }

    private static Map<String, Object> meta(RegisteredCredential credential) {
        return credential.format() == CredentialFormat.MSO_MDOC
                ? Map.of("doctype_value", credential.type())
                : Map.of("vct_values", List.of(credential.type()));
    }
}
