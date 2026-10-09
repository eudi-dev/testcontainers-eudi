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
 * A provider that was put on one of the wallet's trusted lists (since
 * eudi-dev v3.0.0).
 *
 * @param id   the entity id, for {@link WalletClient#removeTrustedEntity(String)}
 * @param list the wallet list: {@code pid}, {@code qeaa}, {@code pub-eaa},
 *             {@code eaa}, {@code wallet-provider}, {@code access-ca} or
 *             {@code registrar}
 * @param name the provider's name on the list
 */
public record TrustedEntity(String id, String list, String name) {
}
