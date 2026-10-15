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
 * Result of asking the issuer for a deferred credential now
 * ({@code POST /api/deferred/{id}/collect}). {@code credentialId} is set when
 * the credential arrived. {@code reason} explains a pending or abandoned one.
 */
public record DeferredAttempt(boolean collected, boolean pending, boolean abandoned,
        String credentialId, String format, String reason) {
}
