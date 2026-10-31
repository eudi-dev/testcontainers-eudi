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

import java.time.Instant;

/**
 * A certificate on the CRL of its issuer (since eudi-dev v3.6.0).
 *
 * @param serial  the serial number in hexadecimal
 * @param subject the subject name
 * @param crlPath the path of the CRL on the wallet
 * @param revoked when it was revoked
 * @param expires when the certificate expires
 * @param reason  the CRL reason code of RFC 5280 §5.3.1, where 0 means unspecified
 */
public record RevokedCertificate(String serial, String subject, String crlPath, Instant revoked, Instant expires,
        int reason) {
}
