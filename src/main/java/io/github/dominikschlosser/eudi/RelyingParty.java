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
import java.util.stream.Collectors;

/**
 * A relying party in the wallet's registrar (TS05 v1.5 data model).
 *
 * @param identifier  the {@code organizationIdentifier}, such as
 *                    {@code NLTEST.1A2B3C4D5E6F7A8B}. Both certificates
 *                    contain it
 * @param tradeName   the trade name
 * @param legalName   the legal name
 * @param country     the two-letter country code
 * @param registryUri the registration's URL in the registrar API
 * @param services    the services of the relying party
 */
public record RelyingParty(
        String identifier,
        String tradeName,
        String legalName,
        String country,
        String registryUri,
        List<RelyingPartyService> services) {

    /**
     * Returns the intended uses of every service.
     */
    public List<IntendedUse> intendedUses() {
        return services.stream()
                .flatMap(service -> service.intendedUses().stream())
                .collect(Collectors.toList());
    }
}
