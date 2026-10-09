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
 * An external list on the wallet's list of trusted lists (since eudi-dev
 * v3.0.0).
 *
 * @param url        where the list is published
 * @param configured whether it comes from {@code --trusted-list}, which the API can't remove
 * @param via        the list of trusted lists that points to it, or {@code null}
 * @param error      why the wallet can't use the list, or {@code null}
 */
public record TrustedList(String url, boolean configured, String via, String error) {
}
