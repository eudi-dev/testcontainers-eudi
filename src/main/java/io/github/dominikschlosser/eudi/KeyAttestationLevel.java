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
 * The {@code key_storage} and {@code user_authentication} claims of the
 * wallet's key attestations (OpenID4VCI 1.0 Appendix D.2). The claims
 * describe a simulated protection level. The wallet stores its keys
 * unencrypted.
 *
 * <p>{@link #ISSUER_REQUIREMENTS} (the default) follows the issuer's
 * requirements. {@link #NONE} leaves both claims out. The other values set
 * both claims to that ISO/IEC 18045 level.
 */
public enum KeyAttestationLevel {
    ISSUER_REQUIREMENTS(""),
    NONE("none"),
    ISO_18045_BASIC("iso_18045_basic"),
    ISO_18045_ENHANCED_BASIC("iso_18045_enhanced-basic"),
    ISO_18045_MODERATE("iso_18045_moderate"),
    ISO_18045_HIGH("iso_18045_high");

    private final String wireValue;

    KeyAttestationLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }
}
