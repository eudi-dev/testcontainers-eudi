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
 * An attestation type for the registrar's catalogue (since eudi-dev v3.0.0),
 * for {@link WalletClient#addCatalogAttestation(CatalogEntry)}. It also adds
 * a user template to the catalogue, with
 * {@link WalletClient#saveTemplate(CredentialTemplate, CatalogEntry)} or
 * {@link IssueRequest#saveAsTemplate(String, CatalogEntry)}. The entry then
 * takes its format, type and claims from the template.
 *
 * <pre>{@code
 * client.addCatalogAttestation(CatalogEntry.named("University diploma")
 *         .credential(CredentialFormat.SD_JWT, "urn:example:diploma:1", "degree")
 *         .trustedList("https://example.com/diploma-issuers"));
 * }</pre>
 *
 * <p>Without a rulebook the entry links a placeholder page. The level of
 * security defaults to {@code iso_18045_basic} and the holder binding to
 * {@code key}.
 */
public class CatalogEntry {

    private final String name;
    private final List<RegisteredCredential> credentials = new ArrayList<>();
    private String rulebookUri;
    private String attestationLoS;
    private String bindingType;
    private String trustedList;
    private String category;

    private CatalogEntry(String name) {
        this.name = name;
    }

    /** Starts an entry with a name that is unique in the catalogue. */
    public static CatalogEntry named(String name) {
        return new CatalogEntry(name);
    }

    /**
     * Starts an entry for a template. It takes the template's display name,
     * or the template name if the template has none.
     */
    public static CatalogEntry forTemplate() {
        return new CatalogEntry(null);
    }

    /**
     * Adds a format with its {@code vct} or doctype. Claims use the notation
     * of {@link RelyingPartyRegistration#credential}. Ignored for a template
     * entry.
     */
    public CatalogEntry credential(CredentialFormat format, String type, String... claims) {
        credentials.add(new RegisteredCredential(format, type, List.of(claims)));
        return this;
    }

    /**
     * Sets the credential category: {@code pid}, {@code qeaa},
     * {@code pub-eaa} or {@code eaa} (the default). The wallet signs
     * credentials of the type for the trusted list of this category, and the
     * entry links that list unless {@link #trustedList} names another.
     */
    public CatalogEntry category(String category) {
        this.category = category;
        return this;
    }

    public CatalogEntry rulebook(String rulebookUri) {
        this.rulebookUri = rulebookUri;
        return this;
    }

    /**
     * Sets the level of security: {@code iso_18045_basic},
     * {@code iso_18045_enhanced-basic}, {@code iso_18045_moderate} or
     * {@code iso_18045_high}.
     */
    public CatalogEntry levelOfSecurity(String attestationLoS) {
        this.attestationLoS = attestationLoS;
        return this;
    }

    /**
     * Sets how the attestation is bound to its holder: {@code key},
     * {@code claim}, {@code biometric} or {@code none}. The wallet binds
     * every credential to a key regardless.
     */
    public CatalogEntry bindingType(String bindingType) {
        this.bindingType = bindingType;
        return this;
    }

    /**
     * Sets the URL of the trusted list of issuers, an ETSI TS 119 602 list
     * of trusted entities. With ARF checks a received credential of this type
     * must chain to a certificate on the list.
     */
    public CatalogEntry trustedList(String trustedListUrl) {
        this.trustedList = trustedListUrl;
        return this;
    }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (name != null) {
            body.put("name", name);
        }
        if (category != null) {
            body.put("category", category);
        }
        if (!credentials.isEmpty()) {
            body.put("credentials", credentials.stream()
                    .map(credential -> Map.of(
                            "format", credential.format().getWireValue(),
                            "type", credential.type(),
                            "claims", credential.claims().stream()
                                    .map(claim -> ClaimPaths.toPath(credential.format(), credential.type(), claim))
                                    .toList()))
                    .toList());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        if (rulebookUri != null) {
            schema.put("rulebookURI", rulebookUri);
        }
        if (attestationLoS != null) {
            schema.put("attestationLoS", attestationLoS);
        }
        if (bindingType != null) {
            schema.put("bindingType", bindingType);
        }
        if (trustedList != null) {
            schema.put("trustedAuthorities", List.of(Map.of(
                    "frameworkType", "etsi_tl",
                    "value", trustedList,
                    "isLOTE", true)));
        }
        body.put("schema", schema);
        return body;
    }
}
