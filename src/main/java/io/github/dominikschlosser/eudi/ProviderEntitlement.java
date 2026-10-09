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
 * The entitlement of an attestation provider (ETSI TS 119 475 V1.2.1 Annex
 * A.2). With ARF checks a PID provider needs {@link #PID_PROVIDER}. Any other
 * attestation needs one of the three EAA entitlements. The wallet treats
 * those three alike, so the choice only changes the content of the
 * registration certificate.
 */
public enum ProviderEntitlement {
    PID_PROVIDER("https://uri.etsi.org/19475/Entitlement/PID_Provider"),
    QEAA_PROVIDER("https://uri.etsi.org/19475/Entitlement/QEAA_Provider"),
    PUB_EAA_PROVIDER("https://uri.etsi.org/19475/Entitlement/PUB_EAA_Provider"),
    NON_Q_EAA_PROVIDER("https://uri.etsi.org/19475/Entitlement/Non_Q_EAA_Provider");

    private final String wireValue;

    ProviderEntitlement(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
