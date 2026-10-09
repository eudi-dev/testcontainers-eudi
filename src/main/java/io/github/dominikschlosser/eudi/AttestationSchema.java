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
import java.util.Map;

/**
 * The {@code SchemaMeta} of an attestation type in the catalogue (EC TS11
 * v1.0 §4.3.1).
 *
 * @param id                 the schema id
 * @param version            the schema version
 * @param rulebookUri        the rulebook URL
 * @param trustedAuthorities the trust frameworks of the attestation's issuers
 * @param attestationLoS     the level of security, such as
 *                           {@code iso_18045_basic}
 * @param bindingType        how the attestation is bound to its holder:
 *                           {@code key}, {@code claim}, {@code biometric} or
 *                           {@code none}
 * @param supportedFormats   the format identifiers, such as
 *                           {@code dc+sd-jwt}
 * @param schemaUris         the schema URL per format identifier. The wallet
 *                           serves each schema
 */
public record AttestationSchema(
        String id,
        String version,
        String rulebookUri,
        List<TrustedAuthority> trustedAuthorities,
        String attestationLoS,
        String bindingType,
        List<String> supportedFormats,
        Map<String, String> schemaUris) {
}
