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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What to disclose when approving a pending consent request, for
 * {@link WalletClient#approveRequest(String, ConsentApproval)}. An empty
 * approval keeps the wallet's own selection and discloses every matched
 * claim, exactly like {@link WalletClient#approveRequest(String)}.
 *
 * <p>Credential picks and set choices reference the request's
 * {@link ConsentRequest#credentialOptions()} (since eudi-dev v1.25.0, older
 * wallets ignore them). The wallet validates them against the pending request
 * and rejects an approval that references options the request did not offer,
 * leaving the request pending.
 */
public final class ConsentApproval {

    private final Map<String, List<String>> selectedClaims = new LinkedHashMap<>();
    private final Map<String, Object> picks = new LinkedHashMap<>();
    private final TreeMap<Integer, Integer> setChoices = new TreeMap<>();
    private final Map<String, Integer> claimSets = new LinkedHashMap<>();
    private String txCode;
    private String credentialConfigurationId;

    /**
     * Starts an approval that keeps the wallet's own selection until
     * narrowed.
     */
    public static ConsentApproval approval() {
        return new ConsentApproval();
    }

    /**
     * Discloses only the given claims of one credential. Claims of a
     * credential without an entry stay fully disclosed.
     */
    public ConsentApproval selectClaims(String credentialId, String... claims) {
        return selectClaims(credentialId, List.of(claims));
    }

    /**
     * Discloses only the given claims of one credential. Claims of a
     * credential without an entry stay fully disclosed.
     */
    public ConsentApproval selectClaims(String credentialId, List<String> claims) {
        selectedClaims.put(credentialId, List.copyOf(claims));
        return this;
    }

    /**
     * Presents the given credential for one DCQL credential query instead of
     * the wallet's first choice. The credential must be one of the query's
     * {@link ConsentQueryOptions#candidates()}, or in debug mode one of its
     * {@link ConsentQueryOptions#nonMatching()} credentials.
     */
    public ConsentApproval pickCredential(String queryId, String credentialId) {
        picks.put(queryId, credentialId);
        return this;
    }

    /**
     * Presents only the given credentials for a query that sets
     * {@code multiple}, instead of every candidate. At least one must stay.
     */
    public ConsentApproval pickCredentials(String queryId, String... credentialIds) {
        picks.put(queryId, List.of(credentialIds));
        return this;
    }

    /**
     * Answers a query with the {@code claim_sets} option at the given index
     * instead of the wallet's first choice. The index is a
     * {@link ConsentClaimSet#index()} of the picked credential. Debug mode
     * only.
     */
    public ConsentApproval chooseClaimSet(String queryId, int claimSetIndex) {
        claimSets.put(queryId, claimSetIndex);
        return this;
    }

    /**
     * Answers one DCQL credential set with the option at the given index of
     * its {@link ConsentSetOptions#options()} instead of the wallet's first
     * choice. Sets without a choice keep the wallet's own selection.
     */
    public ConsentApproval chooseSetOption(int setIndex, int optionIndex) {
        setChoices.put(setIndex, optionIndex);
        return this;
    }

    /**
     * Skips a credential set marked {@link ConsentSetOptions#optional()},
     * answering the request without it.
     */
    public ConsentApproval skipOptionalSet(int setIndex) {
        setChoices.put(setIndex, -1);
        return this;
    }

    /**
     * Supplies the transaction code for an issuance offer that requires one.
     */
    public ConsentApproval txCode(String txCode) {
        this.txCode = txCode;
        return this;
    }

    /**
     * Requests this configuration of an offer that lists several, one of
     * {@link ConsentRequest#offerConfigs()}. The default is the first.
     */
    public ConsentApproval credentialConfigurationId(String id) {
        this.credentialConfigurationId = id;
        return this;
    }

    Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!selectedClaims.isEmpty()) {
            body.put("selected_claims", selectedClaims);
        }
        if (!picks.isEmpty()) {
            body.put("picks", picks);
        }
        if (!setChoices.isEmpty()) {
            // positional, index 0 up to the highest chosen set, entries
            // without an explicit choice keep the wallet's own selection
            List<Integer> choices = new ArrayList<>();
            for (int i = 0; i <= setChoices.lastKey(); i++) {
                choices.add(setChoices.getOrDefault(i, 0));
            }
            body.put("set_choices", choices);
        }
        if (!claimSets.isEmpty()) {
            body.put("claim_sets", claimSets);
        }
        if (txCode != null) {
            body.put("tx_code", txCode);
        }
        if (credentialConfigurationId != null) {
            body.put("credential_configuration_id", credentialConfigurationId);
        }
        return body;
    }
}
