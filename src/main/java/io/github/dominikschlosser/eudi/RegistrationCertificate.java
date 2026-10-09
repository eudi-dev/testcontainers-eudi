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
 * A registration certificate from the wallet's registrar (ETSI TS 119 475
 * V1.2.1, typ {@code rc-wrp+jwt}).
 *
 * @param certificate  the signed certificate JWT
 * @param verifierInfo for a verifier's intended use, the {@code verifier_info}
 *                     JSON array for the request (OpenID4VP 1.0 §5.1),
 *                     otherwise {@code null}
 * @param issuerInfo   for an issuer service, the {@code issuer_info} JSON
 *                     array for the Credential Issuer Metadata (ETSI TS 119
 *                     472-3 V1.1.1 §4.2.3), otherwise {@code null}
 */
public record RegistrationCertificate(String certificate, String verifierInfo, String issuerInfo) {
}
