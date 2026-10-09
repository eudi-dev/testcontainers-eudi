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
import java.util.Map;

/**
 * A DCQL {@code claim_sets} option of a query (OpenID4VP 1.0 §6.4.1). The
 * credential of its {@link CredentialMatch} satisfies it.
 *
 * @param index  the option's position in the query's {@code claim_sets}, for
 *               {@link ConsentApproval#chooseClaimSet(String, int)}
 * @param keys   the disclosed claims
 * @param claims the values of those claims
 */
public record ConsentClaimSet(int index, List<String> keys, Map<String, Object> claims) {
}
