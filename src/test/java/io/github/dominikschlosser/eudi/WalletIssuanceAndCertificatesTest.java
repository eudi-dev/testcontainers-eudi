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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the wallet-initiated issuance and CRL calls against a real wallet. */
class WalletIssuanceAndCertificatesTest {

    private static final String IMAGE = System.getProperty("eudi.test.image", "ghcr.io/eudi-dev/eudi-dev:v3.6.0");

    @Test
    void aCredentialRequestWaitsForSignInUntilItIsCancelled() throws InterruptedException {
        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)) {
            wallet.start();
            WalletClient client = wallet.client();
            IssuableCredential.Issuer issuer = client.getIssuableCredentials().stream()
                    .flatMap(credential -> credential.issuers().stream())
                    .findFirst()
                    .orElseThrow();

            CredentialRequest request = client.requestCredential(issuer.url(), issuer.credentialConfigurationIds());

            assertThat(request.status()).isEqualTo("authorization_required");
            assertThat(request.authorizationUrl()).contains("/authorize");
            client.cancelCredentialRequest(request.offerId());
            for (int i = 0; i < 50 && !"cancelled".equals(client.getCredentialRequest(request.offerId()).status()); i++) {
                Thread.sleep(100);
            }
            assertThat(client.getCredentialRequest(request.offerId()).status()).isEqualTo("cancelled");
        }
    }

    @Test
    void aRevokedCertificateIsListedOnTheCrl() {
        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)) {
            wallet.start();
            WalletClient client = wallet.client();
            String credentialId = client.getCredentials().get(0).id();
            List<CredentialCertificate> chain = client.getCredentialCertificates(credentialId);

            RevokedCertificate revoked = client.revokeCertificate(chain.get(0).certificate());

            assertThat(client.getRevokedCertificates()).contains(revoked);
            client.setCrlFault(CrlFault.UNAVAILABLE);
            client.clearCrlFault();
        }
    }
}
