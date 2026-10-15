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
 * A failed wallet API call. When the wallet answered with an HTTP error, the
 * exception carries the status code and the error object: {@code error} is a
 * code such as {@code invalid_request}, {@code not_found} or
 * {@code consent_timeout}, and {@code error_description} says what went
 * wrong. {@code POST /api/presentations} refuses an invalid request with an
 * OpenID4VP 1.0 §8.5 error code. A refusal on findings lists them in
 * {@link #getFindings()}.
 *
 * <p>A consent request without an answer ends with HTTP 504 and
 * {@code consent_timeout}. HTTP 503 with {@code temporarily_unavailable}
 * means too many flows wait for the user. Retry after the
 * {@code Retry-After} seconds.
 */
public class WalletClientException extends RuntimeException {

    private final int statusCode;
    private final String error;
    private final String errorDescription;
    private final List<String> findings;

    public WalletClientException(String message) {
        this(message, 0, null, null, List.of());
    }

    public WalletClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.error = null;
        this.errorDescription = null;
        this.findings = List.of();
    }

    public WalletClientException(String message, int statusCode, String error, String errorDescription,
            List<String> findings) {
        super(message);
        this.statusCode = statusCode;
        this.error = error;
        this.errorDescription = errorDescription;
        this.findings = List.copyOf(findings);
    }

    /** The HTTP status code, or 0 if the wallet sent no error response. */
    public int getStatusCode() {
        return statusCode;
    }

    /** The {@code error} field of the response body, if any. */
    public String getError() {
        return error;
    }

    /** The {@code error_description} field of the response body, if any. */
    public String getErrorDescription() {
        return errorDescription;
    }

    /** The failed checks of a refusal, one per entry. Empty for other errors. */
    public List<String> getFindings() {
        return findings;
    }
}
