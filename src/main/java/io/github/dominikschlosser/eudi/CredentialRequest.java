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
 * The state of a wallet-initiated credential request (since eudi-dev v3.6.0).
 *
 * @param status           {@code authorization_required}, {@code completed}, {@code deferred} or {@code failed}
 * @param authorizationUrl where the user signs in at the issuer, or {@code null}
 * @param offerId          the id for {@link WalletClient#getCredentialRequest(String)}, or {@code null}
 * @param error            why it failed, or {@code null}
 */
public record CredentialRequest(String status, String authorizationUrl, String offerId, String error) {
}
