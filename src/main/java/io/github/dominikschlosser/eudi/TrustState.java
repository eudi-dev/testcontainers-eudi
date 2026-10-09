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
 * The providers and external lists that were put on the wallet's trusted
 * lists (since eudi-dev v3.0.0).
 *
 * @param entities providers on the wallet's lists
 * @param lists    external lists on the wallet's list of trusted lists and the lists they point to
 * @param listsUrl the wallet's list of trusted lists
 */
public record TrustState(List<TrustedEntity> entities, List<TrustedList> lists, String listsUrl) {
}
