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

import java.time.Instant;

/**
 * A certificate removed from the wallet's own trusted lists (since eudi-dev
 * v3.6.0).
 *
 * @param id        the SHA-256 of the certificate, used to restore it
 * @param subject   the subject name
 * @param withdrawn when it was withdrawn
 */
public record WithdrawnCertificate(String id, String subject, Instant withdrawn) {
}
