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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EudiWalletStorageTest {

    private static final String IMAGE = System.getProperty(
            "eudi.test.image", "ghcr.io/dominikschlosser/eudi-dev:v3.0.0-beta.1");

    @TempDir
    Path state;

    @Test
    void suppliedCaSignsTheHttpsEndpoints() throws Exception {
        copyFixture("override-issuer-key.pem", "wallet-ca-key.pem");
        copyFixture("override-issuer-cert.pem", "wallet-ca-cert.pem");
        String caPem = Files.readString(state.resolve("wallet-ca-cert.pem"));

        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)
                .withCopyFileToContainer(MountableFile.forHostPath(state, 0777), "/home/app/.eudi-dev")) {
            wallet.start();

            assertThat(wallet.getWalletTlsCaCertificatePem().trim()).isEqualTo(caPem.trim());

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            try (InputStream cert = Files.newInputStream(state.resolve("wallet-ca-cert.pem"))) {
                trustStore.setCertificateEntry("fixture", CertificateFactory.getInstance("X.509")
                        .generateCertificate(cert));
            }
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(trustStore);
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, trust.getTrustManagers(), null);

            try (HttpClient client = HttpClient.newBuilder().sslContext(tls)
                    .connectTimeout(Duration.ofSeconds(10)).build()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(wallet.getIssuerMetadataUrl()))
                        .timeout(Duration.ofSeconds(10)).build();
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            }
        }
    }

    @Test
    void customPidSurvivesStartupCommands() {
        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)
                .withPidClaims(new SdJwtPidClaims().givenName("Storage Test").familyName("Fixture"))) {
            wallet.start();

            assertThat(wallet.listCredentials()).anySatisfy(credential -> {
                assertThat(credential.format()).isEqualTo(CredentialFormat.SD_JWT);
                assertThat(credential.claims()).containsEntry("given_name", "Storage Test")
                        .containsEntry("family_name", "Fixture");
            });
        }
    }

    @Test
    void explicitMemoryStorageOverridesTheDefault() throws Exception {
        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)
                .withEnv("EUDI_DEV_STORAGE", "memory")) {
            wallet.start();

            assertStorageBackend(wallet, "memory");
            assertThat(wallet.listCredentials()).isNotEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"FILE,file", "MEMORY,memory", "AUTO,file"})
    void storageShortcutSelectsBackend(Storage storage, String expectedBackend) throws Exception {
        try (EudiWalletContainer wallet = new EudiWalletContainer(IMAGE)
                .withEnv("EUDI_DEV_HOME", "/tmp/storage-shortcut").withStorage(storage)) {
            wallet.start();

            assertStorageBackend(wallet, expectedBackend);
            assertThat(wallet.listCredentials()).isNotEmpty();
        }
    }

    private void assertStorageBackend(EudiWalletContainer wallet, String expectedBackend) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(wallet.getBaseUrl() + "/api/config"))
                    .timeout(Duration.ofSeconds(10)).build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            assertThat(new ObjectMapper().readTree(body).path("storage").asText()).isEqualTo(expectedBackend);
        }
    }

    private void copyFixture(String resource, String filename) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/" + resource)) {
            Files.copy(input, state.resolve(filename));
        }
    }
}
