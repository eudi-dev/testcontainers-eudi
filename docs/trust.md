# Trust

## Trust lists

The wallet serves ETSI trust lists for the credentials it issues:

```java
WalletClient client = wallet.client();

// The default trust list
String trustListJwt = client.getTrustList();

// All registered trust-list profiles
List<TrustListIndexEntry> trustLists = client.getTrustLists();

// Select a specific trust list
String eaaTrustListJwt = client.getTrustListById("eaa");
String pidTrustListByDocType = client.getTrustListForDocType("eu.europa.ec.eudi.pid.1");
String customTrustListByVct = client.getTrustListForVct("urn:example:my-credential:1");
```

`/api/trustlists` is a discovery endpoint. Each entry carries the trust list's relative `path` and an `advertisedUrl` built from the wallet's own base URL, which points inside the container and is not reachable from the test. Resolve `path` against the mapped wallet URL instead:

```java
String reachableEaaTrustListUrl = client.getTrustLists().stream()
    .filter(entry -> "eaa".equals(entry.id()))
    .findFirst()
    .map(wallet::resolveTrustListUrl)
    .orElseThrow();
```

The selector methods above return the ETSI trust-list JWT itself: by profile id, by SD-JWT credential type, or by mdoc document type.

## Your own providers and lists

With `withArf()` the wallet takes every trust anchor from its trusted lists (ETSI TS 119 602). Put the CA of your issuer or registrar on one of them, or add an external list:

```java
WalletClient client = wallet.client();

// Anchor the PIDs of your issuer (lists: pid, qeaa, pub-eaa, eaa, wallet-provider, access-ca, registrar)
TrustedEntity entity = client.addTrustedEntity("pid", "Example PID Provider", Files.readString(Path.of("pid-ca.pem")));

// Add an external list. In debug mode error() says why the wallet can't use it yet.
TrustedList list = client.addTrustedList("https://lists.example/pid.jwt");

TrustState state = client.getTrustState();
state.lists();    // external lists, and the lists a list of trusted lists points to (via())
state.listsUrl(); // the wallet's list of trusted lists

client.removeTrustedEntity(entity.id());
client.removeTrustedList(list.url());
```

`withTrustedList(url)` adds a list when the container starts. A list's signer has to chain to the wallet CA or to a CA from `withTrustedListCa(Path)`. A list that a list of trusted lists points to is checked against the pointer's certificate instead.

In debug mode the wallet reports the checks an issuer failed in the `findings` member of the offer response (`OfferResponse.rawBody()`), and the checks a verifier failed in `PresentationResponse.findings()`.

## TLS certificates

The wallet exposes both its HTTPS leaf certificate and the shared wallet CA certificate, each as PEM or JWKS. Use the leaf certificate to trust one wallet instance. Use the CA certificate if your test harness must trust auxiliary wallets signed by the same CA:

```java
EudiWalletContainer wallet = new EudiWalletContainer();

String leafPem = wallet.getWalletTlsCertificatePem();
Path leafCertFile = wallet.exportWalletTlsCertificate(Path.of("target/eudi-wallet.pem"));

String caPem = wallet.getWalletTlsCaCertificatePem();
Path caCertFile = wallet.exportWalletTlsCaCertificate(Path.of("target/eudi-wallet-ca.pem"));

// JWKS form (public key with x5c chain), e.g. for Keycloak trust configuration
String leafJwks = wallet.getWalletTlsCertificateJwks();
String caJwks = wallet.getWalletTlsCaCertificateJwks();
```

Add the exported PEM to your verifier's trust store instead of disabling TLS verification.

## Issuer metadata

When the wallet acts as the issuer, its metadata and HTTPS endpoints are:

```java
String issuerUrl = wallet.getIssuerUrl();                 // alias of getHttpsBaseUrl()
String issuerMetadataUrl = wallet.getIssuerMetadataUrl(); // https://host:tls-port/.well-known/jwt-vc-issuer
String trustListUrl = wallet.getHttpsTrustListUrl();
String statusListUrl = wallet.getHttpsStatusListUrl();
```
