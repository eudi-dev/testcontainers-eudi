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

import java.util.List;

/**
 * An entry of the registrar's attestation catalogue (since eudi-dev v3.0.0).
 *
 * @param name        the attestation name
 * @param credentials the formats with their {@code vct} or doctype, and
 *                    their claims
 * @param schema      the TS11 {@code SchemaMeta}
 * @param category    the credential category: {@code pid}, {@code qeaa},
 *                    {@code pub-eaa} or {@code eaa}. The wallet signs
 *                    credentials of the type for the trusted list of this
 *                    category
 * @param template    whether the entry comes from a predefined template.
 *                    Such an entry can't be deleted
 */
public record CatalogAttestation(
        String name,
        List<RegisteredCredential> credentials,
        AttestationSchema schema,
        String category,
        boolean template) {

    /** The schema id, which identifies the entry. */
    public String id() {
        return schema.id();
    }
}
