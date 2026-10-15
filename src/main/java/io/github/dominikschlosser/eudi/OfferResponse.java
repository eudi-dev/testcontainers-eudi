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
 * The wallet's answer to a credential offer ({@code POST /api/offers}) or to
 * {@link WalletClient#getOfferStatus(String)}. {@code status} is
 * {@code completed}, {@code deferred} (the issuer will deliver the credential
 * later, see {@link WalletClient#getDeferredIssuances()}), {@code failed} or
 * {@code authorization_required}. The last one means the user has to sign in
 * at {@code authorizationUrl}. Poll the offer with its {@code offerId} then.
 * {@code findings} lists the checks the issuer failed in debug mode.
 */
public record OfferResponse(String status, String credentialId, String format, String issuer,
        String verificationStatus, String error, String transactionId, String retryInterval,
        String authorizationUrl, String offerId, List<String> findings, String rawBody) {
}
