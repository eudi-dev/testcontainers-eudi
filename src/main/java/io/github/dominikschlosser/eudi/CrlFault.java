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
 * How the CRL endpoints of a wallet fail (since eudi-dev v3.6.0).
 */
public enum CrlFault {
    /** The endpoints answer 503. */
    UNAVAILABLE("unavailable"),
    /** The endpoints answer 503 after 2 minutes. */
    HANG("hang"),
    /** The CRL has a nextUpdate in the past. */
    EXPIRED("expired"),
    /** The CRL is signed with the wrong key. */
    BAD_SIGNATURE("bad-signature");

    private final String value;

    CrlFault(String value) {
        this.value = value;
    }

    /** Returns the value of the wallet API. */
    public String value() {
        return value;
    }
}
