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
 * The credentials that match one DCQL credential query. The first candidate
 * is the wallet's own choice.
 *
 * @param id          the DCQL credential query id
 * @param candidates  the matching credentials in the order the wallet prefers
 *                    them
 * @param multiple    whether the query sets {@code multiple}. The wallet then
 *                    sends every candidate unless the approval picks fewer
 * @param nonMatching in debug mode, the wallet's non-matching credentials.
 *                    An approval can pick them to test how the verifier
 *                    handles a wrong answer
 */
public record ConsentQueryOptions(
        String id,
        List<CredentialMatch> candidates,
        boolean multiple,
        List<CredentialMatch> nonMatching) {
}
