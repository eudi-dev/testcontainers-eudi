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
 * A failed wallet API call. When the wallet answered with an HTTP error, the
 * exception carries the status code and the error fields of a JSON body.
 * {@code POST /api/presentations} refuses an invalid request with an
 * OpenID4VP 1.0 §8.5 error code in {@code error} and the failed checks in
 * {@code error_description} (since eudi-dev v3.0.0). Other endpoints put a
 * message in {@code error}.
 */
public class WalletClientException extends RuntimeException {

    private final int statusCode;
    private final String error;
    private final String errorDescription;

    public WalletClientException(String message) {
        this(message, 0, null, null);
    }

    public WalletClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.error = null;
        this.errorDescription = null;
    }

    public WalletClientException(String message, int statusCode, String error, String errorDescription) {
        super(message);
        this.statusCode = statusCode;
        this.error = error;
        this.errorDescription = errorDescription;
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
}
