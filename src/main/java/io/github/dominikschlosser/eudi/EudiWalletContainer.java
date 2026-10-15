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

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Testcontainers wrapper for the <a href="https://github.com/eudi-dev/eudi-dev">eudi-dev</a>
 * wallet (formerly oid4vc-dev), providing a containerized EUDI test wallet for
 * OID4VCI and OID4VP integration tests.
 */
public class EudiWalletContainer extends GenericContainer<EudiWalletContainer> {

    private static final String DEFAULT_IMAGE = "ghcr.io/eudi-dev/eudi-dev:v3.1.4";
    private static final int WALLET_PORT = 8085;
    private static final int ISSUER_TLS_PORT = 8086;
    private static final String CONTAINER_TEMPLATES_DIR = "/templates";
    private static final String CONTAINER_RELYING_PARTY_CA_DIR = "/relying-party-ca";
    private static final String CONTAINER_TRUSTED_LIST_CA_DIR = "/trusted-list-ca";
    private static final String CONTAINER_RELYING_PARTIES_DIR = "/relying-parties";
    private static final String CONTAINER_CATALOG_DIR = "/catalog";
    // The wallet runs as a non-root user. It can't read a copied host file
    // with owner-only permissions, such as a temp file.
    private static final int READABLE_FILE_MODE = 0644;
    private static final int READABLE_DIRECTORY_MODE = 0755;
    // The startup credentials of the caller and the custom PID share one
    // directory, because the wallet takes one --credentials value.
    private static final String CONTAINER_STARTUP_CREDENTIALS_DIR = "/startup-credentials";
    // The built-in PID templates of each PID type: <prefix>pid-sdjwt and
    // <prefix>pid-mdoc.
    private static final Map<String, String> PID_TEMPLATE_PREFIXES = Map.of(
            "urn:eudi:pid:1", "",
            "urn:eudi:pid:de:1", "german-",
            "urn:eudi:pid:nl:1", "dutch-",
            "urn:eudi:pid:it:1", "italian-");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean includeDefaultPid = true;
    private boolean autoAccept = true;
    private boolean statusList = false;
    private boolean requireEncryptedRequest = false;
    private boolean haip = false;
    private boolean strictValidation = false;
    private boolean arf = false;
    private String baseUrl;
    private CredentialFormat preferredFormat;
    private String sessionTranscript;
    private VciVersion vciVersion;
    private String vciClientId;
    private String vciRedirectUri;
    private boolean templatesDirectoryMounted = false;
    private final List<String> relyingPartyCas = new ArrayList<>();
    private final List<String> trustedListCas = new ArrayList<>();
    private final List<String> trustedLists = new ArrayList<>();
    private boolean startupCredentials = false;
    private boolean relyingParties = false;
    private boolean catalog = false;
    private KeyAttestationLevel keyAttestationLevel;
    private boolean clientAttestation = false;
    private PidClaims customPidClaims;
    private String customPidJson;
    private String pidType;
    private WalletClient cachedClient;

    public EudiWalletContainer() {
        this(DockerImageName.parse(DEFAULT_IMAGE));
    }

    public EudiWalletContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public EudiWalletContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        withEnv("EUDI_DEV_STORAGE", Storage.FILE.getWireValue());
        addExposedPort(WALLET_PORT);
        addExposedPort(ISSUER_TLS_PORT);
        waitingFor(Wait.forHttp("/readyz").forPort(WALLET_PORT));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("eudi-dev")));
    }

    public EudiWalletContainer withStorage(Storage storage) {
        return withEnv("EUDI_DEV_STORAGE", storage.getWireValue());
    }

    /**
     * Stores the wallet in Postgres, for example
     * {@code postgres://user@db:5432/eudi?sslmode=disable}. Pass the password
     * with {@code withEnv("PGPASSWORD", ...)}. The container has to reach the
     * database, for example on a shared {@link org.testcontainers.containers.Network}.
     */
    public EudiWalletContainer withStorage(String url) {
        return withEnv("EUDI_DEV_STORAGE", url);
    }

    public EudiWalletContainer withoutDefaultPid() {
        this.includeDefaultPid = false;
        return this;
    }

    public EudiWalletContainer withPidClaims(PidClaims claims) {
        this.customPidClaims = claims;
        return this;
    }

    public EudiWalletContainer withPidClaims(String json) {
        this.customPidJson = json;
        return this;
    }

    /**
     * Selects the PID type (SD-JWT {@code vct}) of the wallet's PID
     * credentials, and with it the built-in templates and their claim set:
     * {@code urn:eudi:pid:1} is the country-independent EUDI PID (the
     * default). {@code urn:eudi:pid:de:1}, {@code urn:eudi:pid:nl:1} and
     * {@code urn:eudi:pid:it:1} add national claims. For another type, save a
     * template and add the credential with {@link #withStartupCredentials(Path)}.
     * The PID credentials get the IDs {@code pid-sdjwt} and {@code pid-mdoc}.
     *
     * @throws IllegalArgumentException for a type without built-in templates
     */
    public EudiWalletContainer withPidType(String vct) {
        if (!PID_TEMPLATE_PREFIXES.containsKey(vct)) {
            throw new IllegalArgumentException("eudi-dev has no PID templates for " + vct
                    + ". Built-in types: " + String.join(", ", PID_TEMPLATE_PREFIXES.keySet()));
        }
        this.pidType = vct;
        return this;
    }

    public EudiWalletContainer withoutAutoAccept() {
        this.autoAccept = false;
        return this;
    }

    public EudiWalletContainer withStatusList() {
        this.statusList = true;
        return this;
    }

    /**
     * Sets the wallet base URL advertised by eudi-dev for its HTTP endpoints.
     * The same host is also reused for the wallet's HTTPS endpoints.
     */
    public EudiWalletContainer withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public EudiWalletContainer withPreferredFormat(CredentialFormat format) {
        this.preferredFormat = format;
        return this;
    }

    public EudiWalletContainer withSessionTranscript(String mode) {
        this.sessionTranscript = mode;
        return this;
    }

    /**
     * Requires verifiers to encrypt OID4VP request objects. When enabled, the
     * wallet advertises an encryption key in its {@code wallet_metadata} so that
     * verifiers can encrypt the Authorization Request using JWE.
     */
    public EudiWalletContainer withRequireEncryptedRequest() {
        this.requireEncryptedRequest = true;
        return this;
    }

    /**
     * Enforces HAIP 1.0 compliance (x509_hash client id prefix,
     * {@code direct_post.jwt}, DCQL, JAR, ES256).
     */
    public EudiWalletContainer withHaip() {
        this.haip = true;
        return this;
    }

    /**
     * Checks the access and registration certificates of verifiers and
     * issuers against the ARF ({@code --arf}, since eudi-dev v3.0.0). The
     * certificates from the wallet's own registrar pass. Add the CAs of other
     * registrars with {@link #withRelyingPartyCa(Path)}. Combine with
     * {@link #withStrictValidation()} to refuse a request or an offer on any
     * finding.
     */
    public EudiWalletContainer withArf() {
        this.arf = true;
        return this;
    }

    /**
     * Copies a PEM file with CA certificates into the container and trusts
     * them for relying party access and registration certificates
     * ({@code --relying-party-ca}, since eudi-dev v3.0.0). Call it once per
     * file. Only {@link #withArf()} checks these certificates.
     */
    public EudiWalletContainer withRelyingPartyCa(Path pemFile) {
        String target = CONTAINER_RELYING_PARTY_CA_DIR + "/ca-" + relyingPartyCas.size() + ".pem";
        withCopyFileToContainer(MountableFile.forHostPath(pemFile, READABLE_FILE_MODE), target);
        relyingPartyCas.add(target);
        return this;
    }

    /**
     * Copies a PEM file with CA certificates into the container and trusts
     * them for the signatures of trusted lists ({@code --trusted-list-ca},
     * since eudi-dev v3.0.0). The wallet trusts the lists it signs itself.
     * Call it once per file. Only {@link #withArf()} checks these signatures.
     */
    public EudiWalletContainer withTrustedListCa(Path pemFile) {
        String target = CONTAINER_TRUSTED_LIST_CA_DIR + "/ca-" + trustedListCas.size() + ".pem";
        withCopyFileToContainer(MountableFile.forHostPath(pemFile, READABLE_FILE_MODE), target);
        trustedListCas.add(target);
        return this;
    }

    /**
     * Puts an external list of trusted entities (ETSI TS 119 602) on the
     * wallet's list of trusted lists ({@code --trusted-list}, since eudi-dev
     * v3.0.0). Call it once per list. Its signer has to chain to a CA from
     * {@link #withTrustedListCa(Path)}.
     */
    public EudiWalletContainer withTrustedList(String url) {
        trustedLists.add(url);
        return this;
    }

    /**
     * Copies a YAML or JSON credentials file, or a directory of such files,
     * into the container and adds those credentials on start
     * ({@code --credentials}, since eudi-dev v3.0.0). An entry issues a
     * credential from a template or imports a finished one. Its {@code id}
     * becomes the credential ID.
     */
    public EudiWalletContainer withStartupCredentials(Path hostPath) {
        copyFileOrDirectory(hostPath, CONTAINER_STARTUP_CREDENTIALS_DIR);
        this.startupCredentials = true;
        return this;
    }

    /**
     * Copies a YAML or JSON file of relying parties, or a directory of such
     * files, into the container and registers them on start
     * ({@code --relying-parties}, since eudi-dev v3.0.2). An entry is a TS05
     * registration. The wallet's registrar issues its access and registration
     * certificates.
     */
    public EudiWalletContainer withRelyingParties(Path hostPath) {
        copyFileOrDirectory(hostPath, CONTAINER_RELYING_PARTIES_DIR);
        this.relyingParties = true;
        return this;
    }

    /**
     * Copies a YAML or JSON file of attestation catalogue entries, or a
     * directory of such files, into the container and adds them on start
     * ({@code --catalog}, since eudi-dev v3.0.0).
     */
    public EudiWalletContainer withCatalog(Path hostPath) {
        copyFileOrDirectory(hostPath, CONTAINER_CATALOG_DIR);
        this.catalog = true;
        return this;
    }

    /**
     * Sets the key attestation claims of the wallet's key attestations
     * ({@code --key-attestation-level}). Change it at runtime with
     * {@link WalletClient#setKeyAttestationLevel(KeyAttestationLevel)}.
     */
    public EudiWalletContainer withKeyAttestationLevel(KeyAttestationLevel level) {
        this.keyAttestationLevel = level;
        return this;
    }

    /**
     * Sends the wallet attestation on OID4VCI token requests even when the
     * issuer doesn't advertise {@code attest_jwt_client_auth}
     * ({@code --client-attestation}).
     */
    public EudiWalletContainer withClientAttestation() {
        this.clientAttestation = true;
        return this;
    }

    /**
     * Runs the wallet in {@code strict} validation mode instead of the lenient
     * {@code debug} default, so spec violations by the issuer or verifier under
     * test fail the flow instead of being logged and tolerated.
     */
    public EudiWalletContainer withStrictValidation() {
        this.strictValidation = true;
        return this;
    }

    /**
     * Selects the OpenID4VCI feature level the wallet uses as a client
     * ({@code --vci-version}, since eudi-dev v1.23.0). At
     * {@link VciVersion#V1_1} the wallet uses interactive authorization
     * (OpenID4VCI 1.1 §6) against an issuer whose authorization server
     * publishes an {@code authorization_challenge_endpoint}: it redeems an
     * authorization-code offer by presenting a credential it holds instead of
     * going through a browser redirect, needing no
     * {@link #withVciRedirectUri(String)}. The container's built-in demo
     * issuer also publishes its challenge endpoint at this level, so
     * {@code POST /issuer/api/offers?grant=authorization_code&authorization=presentation}
     * exercises the full flow.
     */
    public EudiWalletContainer withVciVersion(VciVersion version) {
        this.vciVersion = version;
        return this;
    }

    /**
     * Client ID the wallet should use for OID4VCI authorization-code flows.
     */
    public EudiWalletContainer withVciClientId(String clientId) {
        this.vciClientId = clientId;
        return this;
    }

    /**
     * Redirect URI the wallet should use for OID4VCI authorization-code flows.
     */
    public EudiWalletContainer withVciRedirectUri(String redirectUri) {
        this.vciRedirectUri = redirectUri;
        return this;
    }

    /**
     * Copies a host directory of credential template JSON files into the
     * container and points the wallet at it via {@code --templates-dir}, so a
     * project folder of shared templates works as a self-contained setup.
     * Templates are also manageable at runtime via
     * {@link WalletClient#saveTemplate(CredentialTemplate)}.
     */
    public EudiWalletContainer withTemplatesDirectory(Path hostDirectory) {
        withCopyFileToContainer(
                MountableFile.forHostPath(hostDirectory),
                CONTAINER_TEMPLATES_DIR
        );
        this.templatesDirectoryMounted = true;
        return this;
    }

    /**
     * Maps {@code localhost} inside the container to the Docker host via the
     * {@code host-gateway} special address. This allows the wallet to reach
     * services running on the host machine (e.g. an issuer or verifier started
     * in the test) using {@code localhost:<port>}.
     *
     * <p>The {@code host-gateway} value is resolved by the Docker daemon to the
     * host's internal IP address (typically {@code 172.17.0.1} on Linux or
     * the VM gateway on Docker Desktop). It is equivalent to Docker's
     * {@code --add-host=localhost:host-gateway} CLI flag.
     *
     * <p>IPv6 is disabled inside the container so that {@code localhost} resolves
     * over IPv4 to the mapped host gateway. Without this, on a Linux Docker host
     * the wallet resolves {@code localhost} to its own {@code ::1} loopback first
     * and never reaches the host (the mapping works on Docker Desktop regardless,
     * which is why this only surfaces on Linux/CI).
     *
     * <p>Combine with {@link org.testcontainers.Testcontainers#exposeHostPorts(int...)}
     * to make specific host ports accessible.
     */
    public EudiWalletContainer withHostAccess() {
        withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                .withSysctls(Map.of(
                        "net.ipv6.conf.all.disable_ipv6", "1",
                        "net.ipv6.conf.default.disable_ipv6", "1")));
        return withExtraHost("localhost", "host-gateway");
    }

    @Override
    protected void configure() {
        String claimsJson = customPidClaims != null ? customPidClaims.toJson() : customPidJson;
        boolean customPid = claimsJson != null || pidType != null;
        if (customPid) {
            withCopyToContainer(Transferable.of(customPidFile(claimsJson), READABLE_FILE_MODE),
                    CONTAINER_STARTUP_CREDENTIALS_DIR + "/custom-pid.json");
        }
        List<String> cmd = new ArrayList<>(List.of("wallet", "serve"));
        cmd.addAll(buildServeFlags(customPid));
        setCommand(cmd.toArray(new String[0]));
    }

    // The image turns auto-accept and the default PID on through environment
    // variables, so both flags are always set explicitly.
    private List<String> buildServeFlags(boolean customPid) {
        List<String> flags = new ArrayList<>();
        flags.add("--port");
        flags.add(String.valueOf(WALLET_PORT));
        flags.add("--auto-accept=" + autoAccept);
        flags.add("--pid=" + (includeDefaultPid && !customPid));
        if (statusList) {
            flags.add("--status-list");
        }
        if (baseUrl != null) {
            flags.add("--base-url");
            flags.add(baseUrl);
        }
        if (preferredFormat != null) {
            flags.add("--preferred-format");
            flags.add(preferredFormat.getWireValue());
        }
        if (sessionTranscript != null) {
            flags.add("--session-transcript");
            flags.add(sessionTranscript);
        }
        if (requireEncryptedRequest) {
            flags.add("--require-encrypted-request");
        }
        if (haip) {
            flags.add("--haip");
        }
        if (strictValidation) {
            flags.add("--mode");
            flags.add("strict");
        }
        if (arf) {
            flags.add("--arf");
        }
        for (String ca : relyingPartyCas) {
            flags.add("--relying-party-ca");
            flags.add(ca);
        }
        for (String ca : trustedListCas) {
            flags.add("--trusted-list-ca");
            flags.add(ca);
        }
        for (String list : trustedLists) {
            flags.add("--trusted-list");
            flags.add(list);
        }
        if (startupCredentials || customPid) {
            flags.add("--credentials");
            flags.add(CONTAINER_STARTUP_CREDENTIALS_DIR);
        }
        if (relyingParties) {
            flags.add("--relying-parties");
            flags.add(CONTAINER_RELYING_PARTIES_DIR);
        }
        if (catalog) {
            flags.add("--catalog");
            flags.add(CONTAINER_CATALOG_DIR);
        }
        if (keyAttestationLevel != null) {
            flags.add("--key-attestation-level=" + keyAttestationLevel.getWireValue());
        }
        if (clientAttestation) {
            flags.add("--client-attestation");
        }
        if (vciVersion != null) {
            flags.add("--vci-version");
            flags.add(vciVersion.getWireValue());
        }
        if (vciClientId != null) {
            flags.add("--vci-client-id");
            flags.add(vciClientId);
        }
        if (vciRedirectUri != null) {
            flags.add("--vci-redirect-uri");
            flags.add(vciRedirectUri);
        }
        if (templatesDirectoryMounted) {
            flags.add("--templates-dir");
            flags.add(CONTAINER_TEMPLATES_DIR);
        }
        return flags;
    }

    // The custom PID is a startup credentials file with one entry per format.
    // Both entries get the same claim overrides.
    private String customPidFile(String claimsJson) {
        String prefix = PID_TEMPLATE_PREFIXES.get(pidType != null ? pidType : "urn:eudi:pid:1");
        try {
            Object claims = claimsJson != null ? MAPPER.readValue(claimsJson, Map.class) : null;
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String format : List.of("sdjwt", "mdoc")) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", "pid-" + format);
                entry.put("template", prefix + "pid-" + format);
                if (claims != null) {
                    entry.put("claims", claims);
                }
                entries.add(entry);
            }
            return MAPPER.writeValueAsString(Map.of("credentials", entries));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("PID claims are not a JSON object: " + claimsJson, e);
        }
    }

    // A file keeps its name, so the wallet still reads it by its extension.
    private void copyFileOrDirectory(Path hostPath, String containerDirectory) {
        boolean directory = Files.isDirectory(hostPath);
        String target = directory ? containerDirectory : containerDirectory + "/" + hostPath.getFileName();
        int mode = directory ? READABLE_DIRECTORY_MODE : READABLE_FILE_MODE;
        withCopyFileToContainer(MountableFile.forHostPath(hostPath, mode), target);
    }

    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(WALLET_PORT);
    }

    public String getHttpsBaseUrl() {
        return "https://" + getHost() + ":" + getMappedPort(ISSUER_TLS_PORT);
    }

    public String getAuthorizeUrl() {
        return getBaseUrl() + "/authorize";
    }

    public String getHttpsAuthorizeUrl() {
        return getHttpsBaseUrl() + "/authorize";
    }

    /**
     * The wallet's credential offer endpoint. Deliver an offer to
     * {@code <url>?credential_offer_uri=...} (or {@code credential_offer=...})
     * instead of the {@code openid-credential-offer://} custom scheme.
     */
    public String getCredentialOfferUrl() {
        return getBaseUrl() + "/credential-offer";
    }

    public String getHttpsCredentialOfferUrl() {
        return getHttpsBaseUrl() + "/credential-offer";
    }

    public String getTrustListUrl() {
        return getBaseUrl() + "/api/trustlist";
    }

    public String getTrustListUrl(String id) {
        return getTrustListsUrl() + "/" + urlEncode(id);
    }

    public String getTrustListUrlForVct(String vct) {
        return getTrustListUrl() + "?vct=" + urlEncode(vct);
    }

    public String getTrustListUrlForDocType(String docType) {
        return getTrustListUrl() + "?doctype=" + urlEncode(docType);
    }

    public String getHttpsTrustListUrl() {
        return getHttpsBaseUrl() + "/api/trustlist";
    }

    public String getHttpsTrustListUrl(String id) {
        return getHttpsTrustListsUrl() + "/" + urlEncode(id);
    }

    public String getHttpsTrustListUrlForVct(String vct) {
        return getHttpsTrustListUrl() + "?vct=" + urlEncode(vct);
    }

    public String getHttpsTrustListUrlForDocType(String docType) {
        return getHttpsTrustListUrl() + "?doctype=" + urlEncode(docType);
    }

    public String getTrustListsUrl() {
        return getBaseUrl() + "/api/trustlists";
    }

    public String getHttpsTrustListsUrl() {
        return getHttpsBaseUrl() + "/api/trustlists";
    }

    /**
     * Resolves a trust-list discovery entry against this container's mapped
     * HTTP wallet address. Prefer this over {@code entry.url()} when the wallet
     * runs behind Docker port mappings or Testcontainers.
     */
    public String resolveTrustListUrl(TrustListIndexEntry entry) {
        return entry.resolveUrl(getBaseUrl());
    }

    /**
     * Resolves a trust-list discovery entry against this container's mapped
     * HTTPS wallet address.
     */
    public String resolveHttpsTrustListUrl(TrustListIndexEntry entry) {
        return entry.resolveUrl(getHttpsBaseUrl());
    }

    public String getIssuerUrl() {
        return getHttpsBaseUrl();
    }

    public String getIssuerMetadataUrl() {
        return getHttpsBaseUrl() + "/.well-known/jwt-vc-issuer";
    }

    public String getCredentialsUrl() {
        return getBaseUrl() + "/api/credentials";
    }

    public String getTemplatesUrl() {
        return getBaseUrl() + "/api/templates";
    }

    public String getStatusListUrl() {
        return getBaseUrl() + "/api/statuslist";
    }

    public String getHttpsStatusListUrl() {
        return getHttpsBaseUrl() + "/api/statuslist";
    }

    public OfferResponse acceptCredentialOffer(String uri) {
        return client().acceptCredentialOffer(uri);
    }

    /**
     * @see WalletClient#acceptCredentialOffer(String, String)
     */
    public OfferResponse acceptCredentialOffer(String uri, String txCode) {
        return client().acceptCredentialOffer(uri, txCode);
    }

    public PresentationResponse acceptPresentationRequest(String uri) {
        return client().acceptPresentationRequest(uri);
    }

    /**
     * @see WalletClient#submitCredentialOfferForConsent(String)
     */
    public CompletableFuture<OfferResponse> submitCredentialOfferForConsent(String uri) {
        return client().submitCredentialOfferForConsent(uri);
    }

    /**
     * @see WalletClient#submitPresentationRequestForConsent(String)
     */
    public CompletableFuture<PresentationResponse> submitPresentationRequestForConsent(String uri) {
        return client().submitPresentationRequestForConsent(uri);
    }

    public List<Credential> listCredentials() {
        return client().getCredentials();
    }

    public WalletClient client() {
        if (cachedClient == null) {
            cachedClient = new WalletClient(getBaseUrl());
        }
        return cachedClient;
    }

    /**
     * Returns the PEM-encoded leaf HTTPS certificate used by this wallet's
     * HTTPS endpoints. Tests can add this certificate to a trust store to call
     * this wallet's metadata, trust list, or status list endpoints without
     * disabling TLS verification.
     *
     * <p>If multiple wallet HTTPS certificates are issued by a shared CA,
     * prefer {@link #getWalletTlsCaCertificatePem()} for trust stores that
     * must validate sibling wallet certificates as well.
     */
    public String getWalletTlsCertificatePem() {
        return client().getTlsCertificatePem();
    }

    /**
     * Returns the wallet HTTPS leaf certificate as a JWKS document (public key
     * with {@code x5c} chain), e.g. for pasting into Keycloak trust
     * configuration.
     */
    public String getWalletTlsCertificateJwks() {
        return client().getTlsCertificateJwks();
    }

    /**
     * Writes the PEM-encoded wallet HTTPS leaf certificate to the given host path.
     */
    public Path exportWalletTlsCertificate(Path outputPath) {
        return writePem(outputPath, getWalletTlsCertificatePem(), "wallet TLS certificate");
    }

    /**
     * Returns the PEM-encoded shared CA certificate used to sign wallet HTTPS
     * certificates and related x5c chains.
     */
    public String getWalletTlsCaCertificatePem() {
        return client().getCaCertificatePem();
    }

    /**
     * Returns the shared wallet CA certificate as a JWKS document (public key
     * with {@code x5c} chain).
     */
    public String getWalletTlsCaCertificateJwks() {
        return client().getCaCertificateJwks();
    }

    /**
     * Writes the PEM-encoded shared wallet CA certificate to the given host path.
     */
    public Path exportWalletTlsCaCertificate(Path outputPath) {
        return writePem(outputPath, getWalletTlsCaCertificatePem(), "wallet TLS CA certificate");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Path writePem(Path outputPath, String pem, String description) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, pem + System.lineSeparator());
            return outputPath;
        } catch (IOException e) {
            throw new WalletClientException("Failed to write " + description + " to " + outputPath, e);
        }
    }
}
