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

/**
 * The status list entry of an issued registration certificate (ETSI TS 119
 * 475 V1.2.1 Table 7).
 *
 * @param index       the entry in the registrar's status list
 * @param identifier  the relying party's identifier
 * @param intendedUse the certified intended use, empty for an issuer service
 * @param service     the certified issuer service, if any
 * @param revoked     whether the certificate is revoked
 * @param superseded  whether the registrar revoked the certificate itself
 *                    because the registration changed or was deleted. Such
 *                    a certificate can't be activated again
 * @param expires     the expiry as Unix time in seconds
 */
public record RegistrationCertificateStatus(
        int index,
        String identifier,
        String intendedUse,
        String service,
        boolean revoked,
        boolean superseded,
        long expires) {
}
