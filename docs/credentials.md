# Credentials

Everything on this page runs on `WalletClient client = wallet.client()`.

## Listing and reading

```java
List<Credential> credentials = client.getCredentials();
Credential credential = client.getCredential(credentials.getFirst().id());

boolean hasPid = client.hasCredentialWithType("urn:eudi:pid:1");
List<Credential> pids = client.getCredentialsByType("urn:eudi:pid:1");
```

## Issuing

The wallet issues credentials with its own issuer key and imports them directly, no separate issuer needed. Issue from one of the pre-defined templates (such as `pid-sdjwt`, `pid-mdoc`, `german-pid-sdjwt`, `italian-pid-sdjwt`, `dutch-pid-mdoc` or `demo-ticket`), a saved template, or free-form:

```java
// From a pre-defined template, overriding individual claims
Credential pid = client.issueCredential(IssueRequest.fromTemplate("german-pid-sdjwt")
    .claim("given_name", "Jane"));

// Free-form SD-JWT credential
Credential cred = client.issueCredential(IssueRequest.sdJwt("urn:example:my-credential:1")
    .claim("given_name", "Jane")
    .claim("family_name", "Doe")
    .alwaysDisclosed("given_name")   // embed plainly, not selectively disclosable
    .ttl(Duration.ofHours(1)));

// mdoc with per-attribute namespaces (namespace:element claim keys)
Credential mdoc = client.issueCredential(IssueRequest.mdoc("org.iso.23220.photoid.1")
    .namespace("org.iso.23220.1")
    .claim("given_name", "Jane")
    .claim("org.iso.23220.photoid.1:person_id", "12345"));
```

`signedBy(privateKey, certificateChain)` signs the credential with your own key instead of the wallet's issuer key. The key is a PEM or JWK private key, the chain a PEM certificate list with the leaf first, embedded as the credential's `x5c` exactly as given. The wallet then holds a credential from an issuer it does not know, for negative tests and custom trust setups, in SD-JWT or mdoc form:

```java
Credential foreign = client.issueCredential(IssueRequest.sdJwt("urn:example:foreign:1")
    .claim("given_name", "Jane")
    .signedBy(issuerKeyPem, issuerCertPem));
```

## Templates

Templates are named, reusable claim sets shared between the CLI, the HTTP API and the wallet UI:

```java
// List all templates (pre-defined and user)
List<CredentialTemplate> templates = client.getTemplates();

// Save a template and issue from it
client.saveTemplate(CredentialTemplate.named("my-credential")
    .withFormat("sdjwt")
    .withVct("urn:example:my-credential:1")
    .withClaims(Map.of("given_name", "Jane")));

Credential issued = client.issueCredential(IssueRequest.fromTemplate("my-credential"));

client.deleteTemplate("my-credential");
```

`withUniqueClaims(...)` gives a claim a random value in every credential, for example an opaque subject identifier:

```java
client.saveTemplate(CredentialTemplate.named("employee-card")
    .withFormat("sdjwt")
    .withVct("urn:example:employee:1")
    .withClaims(Map.of("sub", "placeholder", "employee_id", "E-1"))
    .withUniqueClaims(List.of("sub")));
```

To add a user template to the registrar's [attestation catalogue](registrar.md#attestation-catalogue), pass a catalogue entry when you save it. The entry takes its format, type and claims from the template, and its name from the template's display name or the template name:

```java
client.saveTemplate(template, CatalogEntry.forTemplate()
    .rulebook("https://example.com/employee-rulebook"));

// or while issuing
client.issueCredential(IssueRequest.sdJwt("urn:example:badge:1")
    .claim("badge", "gold")
    .saveAsTemplate("badge", CatalogEntry.named("Badge")));
```

If the catalogue refuses the entry, for example because its name is taken, the wallet saves neither the template nor the entry. Deleting the template keeps the entry. Delete it with `deleteCatalogAttestation(id)`.

To ship a project folder of template JSON files into the container, use `withTemplatesDirectory(Path)`:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withTemplatesDirectory(Path.of("src/test/resources/templates"));
```

## Startup credentials

`withStartupCredentials(Path)` adds credentials on every start. It takes a YAML or JSON file, or a directory of such files. An entry issues a credential from a template or imports a finished one. Its `id` becomes the credential ID:

```yaml
credentials:
  - id: employee-alice
    template: employee-card       # from withTemplatesDirectory(...)
    claims:
      employee_id: E-2
  - id: erika
    template: german-pid-sdjwt
    exp: 2160h
  - id: partner-ticket
    credential: eyJhbGciOiJFUzI1NiIs...~WyJ...~
```

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withTemplatesDirectory(Path.of("src/test/resources/templates"))
    .withStartupCredentials(Path.of("src/test/resources/credentials.yaml"));

Credential alice = wallet.client().getCredential("employee-alice");
```

See [startup credentials](https://github.com/eudi-dev/eudi-dev/blob/main/docs/wallet/serve.md#startup-credentials) in the eudi-dev docs for every field.

## SD-JWT credential builder

Create signed SD-JWT credentials on the host, without running an issuer:

```java
SdJwtCredentialBuilder builder = new SdJwtCredentialBuilder();

String sdJwt = builder
    .vct("urn:example:my-credential:1")
    .issuer("https://issuer.example.com")
    .claim("given_name", "Jane")
    .claim("family_name", "Doe")
    .objectClaim("address", Map.of("street", "123 Main St", "city", "Berlin"))
    .arrayClaim("nationalities", List.of("DE", "US"))
    .ttl(Duration.ofHours(1))
    .build();

// Import into the wallet
Credential imported = client.importCredential(sdJwt);

// Access the signing key (e.g. for verifier trust configuration)
ECKey issuerKey = builder.getSigningKey();
```

The builder signs on the host with a key it generates, and nothing reaches the wallet until you import the finished credential. `signedBy(...)` under [Issuing](#issuing) does the same through the wallet's issue API and also covers mdoc credentials.

## Renewal

A credential issued with a refresh token can be renewed. `refreshCredential(id)` asks the issuer for a new one, and the renewed credential keeps its ID:

```java
Credential renewed = client.refreshCredential(credential.id());
```

The wallet also renews such credentials on its own shortly before they expire. A failed renewal shows in the activity log.

## Deleting

```java
client.deleteCredential(credentialId);
client.deleteCredentialsByType("urn:eudi:pid:1");
int deleted = client.deleteAllCredentials();
```

## Revocation and status lists

Credentials issued by a wallet running `withStatusList()` carry a managed status list reference, visible on `Credential.status()`:

```java
Credential issued = client.issueCredential(IssueRequest.sdJwt("urn:example:my-credential:1")
    .claim("name", "Revocable"));

client.revokeCredential(issued.id());
CredentialStatusInfo status = client.getCredentialStatus(issued.id());
assertThat(status.isRevoked()).isTrue();

client.unrevokeCredential(issued.id());
```

`getCredentialStatus` also resolves credentials referencing external status lists by fetching them live. The raw status list JWT is available through `client.getStatusList()`.

## Wallet introspection

```java
// Full instance configuration: version, ports, URLs, validation mode, credential count, ...
WalletConfig config = client.getConfig();

// Activity log: assert what the wallet actually did during a flow
List<ActivityLogEntry> log = client.getActivityLog();
client.clearActivityLog();
```
