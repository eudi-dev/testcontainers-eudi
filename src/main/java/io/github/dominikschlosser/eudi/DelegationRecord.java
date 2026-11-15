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
import java.util.List;
import java.util.Map;

/**
 * One Delegate SD-JWT that the wallet signed with the key of a credential
 * (draft-ietf-oauth-delegate-sd-jwt-00, since eudi-dev v3.11.0).
 *
 * @param id        the record ID
 * @param name      the name of the verifier that asked for the delegation
 * @param clientId  the client ID of that verifier
 * @param aud       the aud of the KB-SD-JWT
 * @param format    {@code dSD-JWT} or {@code dSD-JWT+KB}
 * @param signedAt  the iat of the KB-SD-JWT
 * @param disclosed the claim paths disclosed to the verifier
 * @param expired   true when every delegate has passed its exp
 * @param expiresAt the latest exp of the delegates, null when a delegate has no exp
 * @param revoked   true when the credential is revoked on the wallet's own status list
 * @param delegates one entry for each Delegate Payload
 * @param chain     the dSD-JWT in compact serialization
 */
public record DelegationRecord(String id, String name, String clientId, String aud, String format,
        Instant signedAt, List<String> disclosed, boolean expired, Instant expiresAt, boolean revoked,
        List<Delegate> delegates, String chain) {

    /**
     * One Delegate Payload of a record.
     *
     * @param keyThumbprint the JWK thumbprint (RFC 7638) of the cnf key
     * @param jwk           the cnf key of the Delegate Holder
     * @param payload       the Delegate Payload
     * @param expiresAt     its exp, null when it has none
     */
    public record Delegate(String keyThumbprint, Map<String, Object> jwk, Map<String, Object> payload,
            Instant expiresAt) {
    }
}
