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
 * One certificate of a credential's chain, leaf first (since eudi-dev v3.6.0).
 *
 * @param name         the common name
 * @param role         {@code signer}, {@code intermediate} or {@code root}
 * @param subject      the subject name
 * @param serial       the serial number in hexadecimal
 * @param certificate  the certificate as PEM
 * @param revocable    whether this wallet issued it, so it can revoke it
 * @param revoked      whether it is on the CRL of its issuer
 * @param withdrawable whether it is a CA on a trusted list of this wallet
 * @param withdrawnId  the id for {@link WalletClient#restoreCertificate(String)}, or {@code null}
 * @param credentials  the other credentials with this certificate in their chain
 */
public record CredentialCertificate(String name, String role, String subject, String serial, String certificate,
        boolean revocable, boolean revoked, boolean withdrawable, String withdrawnId, List<String> credentials) {
}
