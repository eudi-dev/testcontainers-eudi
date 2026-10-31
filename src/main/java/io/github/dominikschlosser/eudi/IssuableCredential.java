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
 * An attestation type of the registrar catalogue with the issuers that offer it
 * (since eudi-dev v3.6.0).
 *
 * @param id       the schema id
 * @param name     the name
 * @param category {@code pid}, {@code qeaa}, {@code pub-eaa} or {@code eaa}
 * @param issuers  the issuers, which can be empty
 */
public record IssuableCredential(String id, String name, String category, List<Issuer> issuers) {

    /**
     * An issuer of the type.
     *
     * @param name                      the name
     * @param url                       the Credential Issuer Identifier
     * @param credentialConfigurationIds one configuration per format
     */
    public record Issuer(String name, String url, List<String> credentialConfigurationIds) {
    }
}
