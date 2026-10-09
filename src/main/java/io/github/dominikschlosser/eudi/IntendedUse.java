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
 * A registered intended use of a verifier. Each intended use gets its own
 * registration certificate (ARF RPRC_09).
 *
 * @param identifier    the intended use identifier, assigned by the registrar
 * @param purpose       the purpose for the consent dialog, if any
 * @param privacyPolicy the privacy policy URL
 * @param credentials   the permitted credentials and claims
 */
public record IntendedUse(
        String identifier,
        String purpose,
        String privacyPolicy,
        List<RegisteredCredential> credentials) {
}
