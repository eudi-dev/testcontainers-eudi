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
 * The wallet's answer to a presentation request. {@code status} is
 * {@code submitted} when the wallet sent a presentation. It is
 * {@code denied}, {@code refused} or {@code error} when the wallet sent the
 * verifier an error response with {@code error} and {@code errorDescription}
 * (OpenID4VP 1.0 §8.5).
 */
public record PresentationResponse(String status, String redirectUri, String idToken,
        String error, String errorDescription, String rawBody) {
}
