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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Converts between the claim notation of the wallet UI and DCQL claims path
 * pointers (OpenID4VP 1.0 §7). An SD-JWT claim is a dotted path such as
 * {@code address.locality}. An mdoc claim is an element of the doctype's
 * namespace such as {@code given_name}, or {@code namespace:element} for
 * another namespace.
 */
final class ClaimPaths {

    private ClaimPaths() {
    }

    static List<Object> toPath(CredentialFormat format, String type, String claim) {
        if (format == CredentialFormat.MSO_MDOC) {
            int colon = claim.indexOf(':');
            return colon < 0
                    ? List.of(type, claim)
                    : List.of(claim.substring(0, colon), claim.substring(colon + 1));
        }
        return new ArrayList<>(Arrays.asList(claim.split("\\.")));
    }

    static String toClaim(CredentialFormat format, String type, List<?> path) {
        if (format == CredentialFormat.MSO_MDOC && path.size() == 2) {
            String namespace = String.valueOf(path.get(0));
            String element = String.valueOf(path.get(1));
            return Objects.equals(namespace, type) ? element : namespace + ":" + element;
        }
        return path.stream().map(String::valueOf).collect(Collectors.joining("."));
    }
}
