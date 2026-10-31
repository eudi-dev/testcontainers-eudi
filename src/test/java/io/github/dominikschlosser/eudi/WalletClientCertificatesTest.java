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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WalletClientCertificatesTest {

    private HttpServer wallet;
    private String response = "{}";
    private final List<String> requests = new ArrayList<>();
    private WalletClient client;

    @BeforeEach
    void startWallet() throws IOException {
        wallet = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        wallet.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
            byte[] answer = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        wallet.start();
        client = WalletClient.forBaseUrl("http://127.0.0.1:" + wallet.getAddress().getPort());
    }

    @AfterEach
    void stopWallet() {
        wallet.stop(0);
    }

    @Test
    void revokesACertificateAndUndoesIt() {
        response = "{\"serial\":\"0a\",\"subject\":\"CN=CA\",\"crl_path\":\"/api/crl\",\"revoked\":1000,\"expires\":2000,\"reason\":1}";

        RevokedCertificate revoked = client.revokeCertificate("PEM", 1);
        client.undoCertificateRevocation("0a");

        assertThat(revoked.serial()).isEqualTo("0a");
        assertThat(revoked.crlPath()).isEqualTo("/api/crl");
        assertThat(revoked.reason()).isEqualTo(1);
        assertThat(requests.get(0)).startsWith("POST /api/certificates/revocations ").contains("\"certificate\":\"PEM\"");
        assertThat(requests.get(1)).startsWith("DELETE /api/certificates/revocations/0a");
    }

    @Test
    void readsACredentialChain() {
        response = "[{\"name\":\"CA\",\"role\":\"intermediate\",\"subject\":\"CN=CA\",\"serial\":\"0a\",\"certificate\":\"PEM\","
                + "\"revocable\":true,\"revoked\":false,\"withdrawable\":true,\"credentials\":[\"urn:other\"]}]";

        List<CredentialCertificate> chain = client.getCredentialCertificates("abc");

        assertThat(chain).singleElement().satisfies(certificate -> {
            assertThat(certificate.role()).isEqualTo("intermediate");
            assertThat(certificate.withdrawable()).isTrue();
            assertThat(certificate.credentials()).containsExactly("urn:other");
        });
    }

    @Test
    void withdrawsACertificateAndRestoresIt() {
        response = "{\"id\":\"abc\",\"subject\":\"CN=CA\",\"certificate\":\"DER\",\"withdrawn\":1000}";

        WithdrawnCertificate withdrawn = client.withdrawCertificate("PEM");
        client.restoreCertificate(withdrawn.id());

        assertThat(requests.get(0)).startsWith("POST /api/trust/withdrawals ");
        assertThat(requests.get(1)).startsWith("DELETE /api/trust/withdrawals/abc");
    }

    @Test
    void setsAndClearsCrlFaults() {
        client.setCrlFault(CrlFault.BAD_SIGNATURE);
        client.setCrlNextUpdate(Duration.ofMinutes(1));
        client.clearCrlFault();

        assertThat(requests.get(0)).startsWith("PUT /api/crl/fault ").contains("bad-signature");
        assertThat(requests.get(1)).contains("\"next_update_seconds\":60");
        assertThat(requests.get(2)).startsWith("DELETE /api/crl/fault");
    }

    @Test
    void requestsACredentialFromAnIssuer() {
        response = "[{\"id\":\"s\",\"name\":\"Ticket\",\"category\":\"eaa\",\"issuers\":[{\"name\":\"Issuer\","
                + "\"url\":\"https://issuer.example\",\"credential_configuration_ids\":[\"ticket\"]}]}]";
        IssuableCredential.Issuer issuer = client.getIssuableCredentials().get(0).issuers().get(0);
        response = "{\"status\":\"authorization_required\",\"authorization_url\":\"https://issuer.example/a\",\"offer_id\":\"o\"}";

        CredentialRequest request = client.requestCredential(issuer.url(), issuer.credentialConfigurationIds());

        assertThat(request.status()).isEqualTo("authorization_required");
        assertThat(request.authorizationUrl()).isEqualTo("https://issuer.example/a");
        assertThat(requests.get(1)).startsWith("POST /api/issuance ").contains("\"credential_configuration_ids\":[\"ticket\"]");
    }
}
