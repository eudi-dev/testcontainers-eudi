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
 * An access certificate from the wallet's registrar (ETSI TS 119 411-8).
 *
 * @param certificate the PEM leaf certificate
 * @param chain       the PEM leaf followed by the relying party access CA
 * @param clientIds   the matching client identifiers: {@code x509_hash},
 *                    and {@code x509_san_dns} for each DNS
 *                    name (OpenID4VP 1.0 §5.9.3)
 */
public record AccessCertificate(String certificate, String chain, List<String> clientIds) {
}
