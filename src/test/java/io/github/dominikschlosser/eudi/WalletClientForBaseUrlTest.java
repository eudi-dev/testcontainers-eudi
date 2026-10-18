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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletClientForBaseUrlTest {

    private HttpServer wallet;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void startWallet() throws IOException {
        wallet = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        wallet.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        wallet.start();
    }

    @AfterEach
    void stopWallet() {
        wallet.stop(0);
    }

    @Test
    void talksToTheWalletAtTheBaseUrl() {
        String baseUrl = "http://127.0.0.1:" + wallet.getAddress().getPort() + "/";

        assertThat(WalletClient.forBaseUrl(baseUrl).getCredentials()).isEmpty();
        assertThat(requestedPaths).containsExactly("/api/credentials");
    }

    @Test
    void requiresABaseUrl() {
        assertThatThrownBy(() -> WalletClient.forBaseUrl(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WalletClient.forBaseUrl(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
