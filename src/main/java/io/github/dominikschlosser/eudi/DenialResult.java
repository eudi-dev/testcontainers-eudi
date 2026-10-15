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
 * Result of denying a pending consent request. The wallet sends the verifier
 * an error response. {@code redirectUri} is the verifier's redirect after it
 * (OpenID4VP 1.0 §8.2). {@code error} is set when sending failed.
 */
public record DenialResult(String status, String redirectUri, String error) {
}
