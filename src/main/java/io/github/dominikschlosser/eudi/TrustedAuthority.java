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
 * The trust framework of an attestation type in the catalogue (EC TS11 v1.0
 * §4.3.3).
 *
 * @param frameworkType {@code etsi_tl}, {@code aki} or
 *                      {@code openid_federation}
 * @param value         the trusted list URL, key identifier or entity
 *                      identifier
 * @param lote          whether an {@code etsi_tl} value is a list of trusted
 *                      entities (ETSI TS 119 602)
 */
public record TrustedAuthority(String frameworkType, String value, boolean lote) {
}
