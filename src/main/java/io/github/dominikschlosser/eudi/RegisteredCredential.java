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
 * A credential type in a relying party registration. An intended use lists
 * its permitted credentials with their claims. An issuer service lists its
 * attestation types.
 *
 * @param format the credential format
 * @param type   the SD-JWT VC {@code vct} or the mdoc doctype
 * @param claims the permitted claims of an intended use, as dotted paths for
 *               SD-JWT and element names for mdoc, with a
 *               {@code namespace:} prefix outside the doctype's namespace.
 *               Empty for a provided attestation type
 */
public record RegisteredCredential(CredentialFormat format, String type, List<String> claims) {
}
