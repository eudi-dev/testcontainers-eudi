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
 * Result of approving a pending consent request.
 *
 * @param status        {@code approved}, or {@code pending} when the issuer deferred the credential
 * @param statusCode    the HTTP status of the verifier's or issuer's answer, 0 if none
 * @param redirectUri   the verifier's redirect URI after a presentation submission, if any
 * @param error         submission error, if the flow failed after approval
 * @param findings      the failed checks behind {@code error}, one per entry
 * @param transactionId the transaction of a deferred credential
 * @param retryInterval how often the wallet asks for a deferred credential
 */
public record ApprovalResult(String status, int statusCode, String redirectUri, String error,
        List<String> findings, String transactionId, String retryInterval) {
}
