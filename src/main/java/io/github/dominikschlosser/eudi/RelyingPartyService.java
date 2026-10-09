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
 * One service of a registered relying party (TS05 v1.5 §2.4.1).
 *
 * @param serviceIdentifier    the service identifier, {@code null} for the
 *                             only service of a relying party
 * @param tradeName            the service's trade name
 * @param entitlements         the entitlement URIs of ETSI TS 119 475 V1.2.1
 *                             Annex A.2
 * @param providedAttestations the attestation types of an issuer service
 * @param intendedUses         the intended uses of a verifier service
 */
public record RelyingPartyService(
        String serviceIdentifier,
        String tradeName,
        List<String> entitlements,
        List<RegisteredCredential> providedAttestations,
        List<IntendedUse> intendedUses) {
}
