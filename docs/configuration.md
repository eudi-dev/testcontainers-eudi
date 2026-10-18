# Configuration

## Startup options

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withStatusList()                                // enable the status list endpoint, issued credentials become revocable
    .withBaseUrl("http://wallet.example.test:8085")  // advertised HTTP base URL, the HTTPS host follows it
    .withSessionTranscript("iso")                    // mdoc session transcript: "oid4vp" (default) or "iso"
    .withVciClientId("my-wallet")                    // client id for OID4VCI authorization-code flows
    .withVciRedirectUri("http://localhost:8085/callback")
    .withPidType("urn:eudi:pid:de:1")                // the German PID instead of the country-independent one
    .withKeyAttestationLevel(KeyAttestationLevel.ISO_18045_HIGH)
    .withClientAttestation()                         // send the wallet attestation to every issuer
    .withoutAutoAccept()                             // wait for explicit consent instead of accepting everything
    .withoutDefaultPid();                            // start without the default PID credential
```

`withTemplatesDirectory(Path)` ships a folder of credential templates into the container, and `withStartupCredentials(Path)` adds credentials on start, see [Credentials](credentials.md). `withRelyingParties(Path)` and `withCatalog(Path)` register verifiers, issuers and attestation types on start, see [Registrar](registrar.md). `withHostAccess()` lets the wallet reach services on the Docker host, see [Networking](networking.md).

The container counts as started once `GET /readyz` answers. That endpoint also checks the storage backend. Wallet images before eudi-dev 2.6.0 lack it. For those, wait on the version endpoint instead:

```java
new EudiWalletContainer("ghcr.io/eudi-dev/eudi-dev:v2.5.0")
    .waitingFor(Wait.forHttp("/api/version").forPort(8085));
```

## Storage

The wrapper sets `EUDI_DEV_STORAGE=file`, including for images that default to memory storage. Wallet state lives in the container unless you mount a directory. Files copied or mounted under `/home/app/.eudi-dev` are loaded, including `wallet-ca-key.pem` and `wallet-ca-cert.pem`.

Select a backend with `withStorage(Storage.FILE)`, `withStorage(Storage.MEMORY)` or `withStorage(Storage.AUTO)`. Automatic selection uses files when a state directory was supplied or already exists, and memory otherwise. For PostgreSQL, pass the URL: `withStorage("postgres://user@db:5432/eudi?sslmode=disable")`. Set the password with `withEnv("PGPASSWORD", ...)`, and put both containers on one network.

## Conformance settings

These settings control how strictly the wallet plays the protocol. Each has a runtime setter, so a single container can serve tests with different requirements:

| Setting | Startup | Runtime |
|---|---|---|
| Validation mode | `withStrictValidation()` | `setValidationMode(mode)` |
| HAIP 1.0 enforcement | `withHaip()` | `setRequireHaip(flag)` |
| ARF checks of relying party certificates | `withArf()` | `setRequireArf(flag)` |
| Encrypted request objects | `withRequireEncryptedRequest()` | `setRequireEncryptedRequest(flag)` |
| OpenID4VCI feature level | `withVciVersion(version)` | `setVciVersion(version)` |
| Key attestation level | `withKeyAttestationLevel(level)` | `setKeyAttestationLevel(level)` |
| Wallet attestation for every issuer | `withClientAttestation()` | `setForceClientAttestation(flag)` |
| TLS verification of outgoing requests | None | `setTlsVerify(flag)`, `null` follows the validation mode |

In the default `debug` mode the wallet tolerates spec violations by the issuer or verifier under test and logs them. In `strict` mode the violation fails the flow. `withHaip()` enforces HAIP 1.0 on top (x509_hash client id prefix, `direct_post.jwt`, DCQL, JAR, ES256), and `withRequireEncryptedRequest()` makes the wallet advertise an encryption key in its `wallet_metadata` and demand JWE request objects.

`withArf()` checks the access and registration certificates of verifiers and issuers against the ARF. `withRelyingPartyCa(Path)` adds the CA certificates of a registrar outside the wallet, and `withTrustedListCa(Path)` those of a trusted list operator. See [Registrar](registrar.md#arf-checks).

`setKeyAttestationLevel(level)` sets the `key_storage` and `user_authentication` claims of the wallet's key attestations (OpenID4VCI 1.0 Appendix D.2). By default they follow the issuer's requirements. `KeyAttestationLevel.NONE` leaves them out.

`withVciVersion(VciVersion.V1_1)` moves the wallet to the OpenID4VCI 1.1 draft level, which enables interactive authorization: against an issuer publishing an `authorization_challenge_endpoint`, the wallet redeems an authorization-code offer by presenting a credential it holds instead of going through a browser redirect. See [Flows](flows.md#presentation-during-issuance-openid4vci-11) for the flow.

Runtime changes hold until the container restarts. `resetConformance()` restores the startup values:

```java
WalletClient client = wallet.client();
client.setValidationMode(ValidationMode.STRICT);
// run the strict part of the test
client.resetConformance();
```

A wallet that is not a container of the test run, for example one started by Docker Compose, gets its client from its base URL:

```java
WalletClient client = WalletClient.forBaseUrl("http://localhost:8085");
```

The preferred credential format works the same way: `withPreferredFormat(CredentialFormat.SD_JWT)` at startup, `setPreferredFormat(format)` and `clearPreferredFormat()` at runtime. So does auto-accept: `withoutAutoAccept()` at startup, `setAutoAccept(flag)` at runtime.

## Custom PID claims

Use a format-specific claims builder:

```java
// SD-JWT format
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthdate("1990-01-15")
        .nationalities("DE"));

// mdoc format
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims(new MdocPidClaims()
        .givenName("Jane")
        .familyName("Doe")
        .birthDate("1990-01-15")
        .nationality("DE"));
```

Or provide raw JSON:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidClaims("{\"given_name\": \"Jane\", \"family_name\": \"Doe\"}");
```

The builders mirror the EUDI PID Rulebook attributes of the default PID. The wallet holds the PID in both formats, with IDs `pid-sdjwt` and `pid-mdoc`, and both get the same claims. The custom PID is a startup credential, so it works with every storage.

`withPidType(...)` selects another PID type with built-in templates: `urn:eudi:pid:de:1`, `urn:eudi:pid:nl:1` or `urn:eudi:pid:it:1`. Their national claims are set with `claim(name, value)`:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withPidType("urn:eudi:pid:de:1")
    .withPidClaims(new SdJwtPidClaims()
        .givenName("Jane")
        .claim("birth_name", "Doe"));
```

## Errors

A failed call throws `WalletClientException`. When the wallet answered with an HTTP error, `getStatusCode()`, `getError()` and `getErrorDescription()` return its error object. `getError()` is a code such as `invalid_request`, `not_found` or `consent_timeout`. A strict refusal lists the failed checks in `getFindings()`:

```java
assertThatThrownBy(() -> wallet.acceptPresentationRequest(requestUri))
    .isInstanceOfSatisfying(WalletClientException.class, refusal ->
        assertThat(refusal.getFindings()).anyMatch(finding -> finding.startsWith("HAIP 1.0")));
```

A consent request without an answer ends with HTTP 504 and `consent_timeout`. HTTP 503 with `temporarily_unavailable` means too many flows wait for the user.

## Error injection

Make the wallet answer the next presentation request with a specific OAuth error, for testing your verifier's error handling. The override is one-shot and applies to the next request only:

```java
client.setNextError("access_denied", "User denied consent");
client.clearNextError();
```
