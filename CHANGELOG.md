# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.1] - 2026-10-18

### Added

- `WalletClient.forBaseUrl(String)` returns a client for a wallet that is not a container of the test run, for example one started by Docker Compose.

## [3.0.0] - 2026-10-15

The module moved to the [eudi-dev](https://github.com/eudi-dev) organization. Its major version now follows eudi-dev's, so 3.x runs the eudi-dev 3 wallet. The Maven coordinates stay `io.github.dominikschlosser:testcontainers-eudi`.

### Breaking changes

- The default wallet image is `ghcr.io/eudi-dev/eudi-dev:v3.1.4`. The container waits for `GET /readyz`, which wallet images before eudi-dev 2.6.0 lack. For those, call `waitingFor(Wait.forHttp("/api/version").forPort(8085))`.
- The custom PID of `withPidClaims(...)` and `withPidType(...)` is now a startup credential. It works with every storage. `withPidType(...)` takes the PID types with built-in templates (`urn:eudi:pid:1`, `urn:eudi:pid:de:1`, `urn:eudi:pid:nl:1`, `urn:eudi:pid:it:1`) and refuses others.
- `withTrustListCa(Path)` is now `withTrustedListCa(Path)`, like the eudi-dev flag.
- Typed results. `OfferResponse` has the offer's status, credential ID, findings and deferred or sign-in details. `denyRequest(...)` returns a `DenialResult`, and `importCredential(...)` the stored `Credential`. `ApprovalResult` and `PresentationResponse` have more components.
- Other records changed too: `WalletConfig` (`requireHaipIssuance` is gone, `tlsVerify`, `tlsVerifyOverride` and `storage` are new), `TrustListAttestation` (`category`, and `trustListType` and `entityName` are now `trustedListType` and `trustedEntityName`), `TrustListIndexEntry` (`description`, `category`), `ConsentRequest` (`offerConfigs`, `clientName`, `registeredName`), `CredentialTemplate`, `CredentialMatch`, `ConsentQueryOptions` and `ConsentSetOptions`. Code that calls their canonical constructors has to pass the added values.
- eudi-dev 3 changes some wallet behavior. Errors use one object with `error` and `error_description`, and a consent timeout answers 504. A missing registration certificate is a finding, not a refusal. The demo verifier gives a request's result only for its `transaction_id`. In strict mode an unbound credential only matches a query that allows it. The wallet publishes one trusted list per credential category. `IssueRequest.trustProfile(...)` is now `category(...)`. See the [eudi-dev changelog](https://github.com/eudi-dev/eudi-dev/blob/main/CHANGELOG.md).

### Added

- Registrar client. `registerRelyingParty`, `updateRelyingParty`, `enrol` and `updateEnrolment` register verifiers and issuers. `issueAccessCertificate` and `issueRegistrationCertificate` issue their certificates. Further methods revoke the certificates and list or delete the registrations. See [Registrar](docs/registrar.md).
- ARF checks with `withArf()` and `setRequireArf(boolean)`. `withRelyingPartyCa(Path)` and `withTrustedListCa(Path)` trust the CAs of other registrars and trusted list operators.
- Attestation catalogue: `getCatalogAttestations()`, `addCatalogAttestation(CatalogEntry)`, `getAttestationSchema(id)` and `deleteCatalogAttestation(id)`.
- Startup files: `withStartupCredentials(Path)`, `withRelyingParties(Path)` and `withCatalog(Path)` add credentials, registrations and catalogue entries on every start.
- Deferred issuance: `getDeferredIssuances()`, `collectDeferred(id)` and `abandonDeferred(id)`. `getOfferStatus(offerId)` polls an offer that waits for the user's sign-in.
- `refreshCredential(id)` renews a credential with its refresh token.
- Consent options: `ConsentApproval.pickCredentials(...)`, `chooseClaimSet(...)` and `credentialConfigurationId(...)`. In debug mode `pickCredential(...)` also takes a non-matching credential.
- Trusted lists: `addTrustedEntity`, `removeTrustedEntity`, `addTrustedList`, `removeTrustedList` and `getTrustState()`. `withTrustedList(url)` adds a list on start.
- Findings. `ConsentRequest`, `PresentationResponse` and `OfferResponse` list the checks a verifier or issuer failed. `WalletClientException.getFindings()` lists those of a refusal.
- Settings: `withKeyAttestationLevel(...)`, `withClientAttestation()`, `setForceClientAttestation(boolean)`, `setTlsVerify(Boolean)`, `setAutoAccept(boolean)` and `withStorage(String)` for a Postgres URL.
- `CredentialTemplate.withUniqueClaims(...)` gives a claim a fresh random value in every credential.
- `acceptCredentialOffer(uri, txCode, credentialConfigurationId)`, `acceptPresentationRequest(uri, sessionTranscript)` and `getVersion()`.

## [2.5.0] - 2026-10-02

### Added

- `withStorage(Storage)` selects file, memory or automatic storage through the `Storage` enum.

### Changed

- Update the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v2.5.0`, which supports storage selection.

### Fixed

- Use file storage by default so mounted CA certificates are loaded and custom PID credentials survive the startup commands when using wallet images that default to memory storage. Explicit `EUDI_DEV_STORAGE` overrides remain supported.

## [2.4.0] - 2026-09-02

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v2.2.0` (from `v2.0.7`). The new wallet checks relying party registration certificates against the ARF content rules, offers credentials a request's `trusted_authorities` did not match in debug mode (flagged in the consent dialog), discloses an empty array for a claim path that selects selectively disclosable array elements without naming them, names `response_types_supported` and `response_modes_supported` in its wallet metadata, cites the violated specification uniformly in validation findings, and warns about undefined request fields
- `ConsentRequest` now carries what the consent dialog shows: `matchedCredentials()` (the credentials the wallet would present on its own), `purposes()` (what the verifier registered the data request for), and `credentialOptions()` (every credential that could answer each query). The previous five-component constructor remains available and leaves the new components empty
- `approveRequest(id, null)` with a bare null literal no longer compiles because the selected-claims overload gained a `ConsentApproval` sibling. Call `approveRequest(id)` instead, which does the same

### Added

- `ConsentApproval` and `approveRequest(id, ConsentApproval)`: approve a pending consent request with a specific credential per query (`pickCredential`), a specific credential set option (`chooseSetOption` and `skipOptionalSet`), only selected claims per credential (`selectClaims`), or a transaction code for an issuance offer that requires one (`txCode`). An empty approval keeps the wallet's own selection and behaves like `approveRequest(id)`
- `approveRequestAsync(id[, ConsentApproval])` approves without blocking on the submission result, for approvals that unblock a further consent step. Approving an offer whose issuer demands a presentation (OpenID4VCI 1.1 interactive authorization) completes only once the raised `issuance_presentation` request is answered too
- `acceptCredentialOffer(uri, txCode)` on the container and the client supplies the transaction code of a pre-authorized offer (OpenID4VCI §4.1.1). On a wallet without auto-accept the code travels with the approval instead, via `ConsentApproval.txCode`
- `CredentialMatch`, `ConsentCredentialOptions`, `ConsentSetOptions` and `ConsentQueryOptions`, describing what a pending consent request matched and what it offers to choose from
- `IssueRequest.signedBy(privateKey, certificateChain)` signs an issued credential with a caller-provided key and certificate chain instead of the wallet's issuer key (eudi-dev v2.2.0). The chain is embedded as the credential's `x5c` exactly as given, so the wallet holds a credential from an issuer it does not know, in SD-JWT or mdoc form

## [2.3.0] - 2026-08-28

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v2.0.7` (from `v1.24.2`), moving to the eudi-dev 2.x line: the wallet redesign around the credential card, batch and deferred issuance, short hex credential ids, and the country-independent PID carrying the EUDI PID Rulebook's Jan Wijnand example instead of the German specimen. The builder API is unchanged, so overrides keep working; a test that asserts specific default-PID values reads the rulebook example now (`family_name` is `'t Hart`, not `MUSTERMANN`)

### Fixed

- `WalletClient` reads a credential's `raw` form and its claims from the per-credential detail endpoint when the listing omits them. eudi-dev 2.0.3 dropped `raw` and the claim values from `GET /api/credentials` (an overview card does not render them, and they cost megabytes on an image-heavy wallet), so `getCredentials()` returned entries whose `raw()` and `claims()` were null

## [2.2.1] - 2026-08-18

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.24.2`, which carries wallet-side fixes only: the request object encryption algorithms are named with the `request_object` metadata pair, `verifier_info` is read from plain request parameters, the challenge endpoint accepts the `auth_session` handed out with `auth_via_web` and caps pushed requests like PAR, a derived callback is accepted on a wallet started without `--base-url`, an explicit flag wins over every demo default, and wallet store saves are serialized against stale snapshots. No API changes in this module

## [2.2.0] - 2026-08-17

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.24.1`
- the default PID claim set follows the EUDI PID Rulebook since eudi-dev v1.22.0, and the `SdJwtPidClaims` / `MdocPidClaims` override builders follow it: `birthFamilyName` / `familyNameBirth` (`birth_family_name` / `family_name_birth`), `sex(int)`, a nested `placeOfBirth(locality[, country])`, `personalAdministrativeNumber`, `documentNumber`, `dateOfIssuance` / `dateOfExpiry` (SD-JWT), an SD-JWT `address` overload with the rulebook's separate `house_number`, and an array-valued mdoc `nationality(...)`. The methods for claims the rulebook dropped (`gender`, the flat `birthPlace` / `birthCountry` / `birthState` / `birthCity`, `givenNameBirth`, the `age*` thresholds, the SD-JWT `issuanceDate` / `expiryDate` names) are gone — for national additions such as the German PID's, use `claim(name, value)`
- since eudi-dev v1.22.0 the pre-defined `pid-sdjwt` / `pid-mdoc` templates back the default PID, and `german-pid-sdjwt` / `german-pid-mdoc` carry the German PID type — issuing from `german-pid-sdjwt` now yields `vct urn:eudi:pid:de:1`

### Added

- OpenID4VCI feature level (eudi-dev v1.23.0): `withVciVersion(VciVersion.V1_1)` starts the wallet at the 1.1 draft level, which enables interactive authorization (OpenID4VCI 1.1 §6) — against an issuer publishing an `authorization_challenge_endpoint`, the wallet redeems an authorization-code offer by presenting a credential it holds instead of a browser redirect, with no redirect URI needed. The container's built-in demo issuer implements the issuer half at the same level (`POST /issuer/api/offers?grant=authorization_code&authorization=presentation`)
- runtime conformance setters on `WalletClient` (`PUT`/`DELETE /api/config/conformance`): `setValidationMode(ValidationMode)`, `setRequireHaip(boolean)`, `setRequireEncryptedRequest(boolean)`, `setVciVersion(VciVersion)`, and `resetConformance()` to restore the startup values — no container restart needed
- `withPidType(vct)` selects the generated default PID's type and claim set (eudi-dev v1.22.0): `urn:eudi:pid:de:1` for the German PID with its national additions, combinable with `withPidClaims(...)`
- `WalletConfig` gained `vciVersion()`, `requireHaipIssuance()` and `forceClientAttestation()`

## [2.1.0] - 2026-08-12

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.21.6`
- the default PID is the country-independent EUDI PID since eudi-dev v1.19.3: the SD-JWT `vct` is `urn:eudi:pid:1` (was `urn:eudi:pid:de:1`) and every mdoc element sits in `eu.europa.ec.eudi.pid.1` with no national namespace. Verifier configurations and DCQL queries matching on the old `vct` have to be updated

### Fixed

- `SdJwtCredentialBuilder` emits RFC 9901-conformant selective disclosure now: object claims nest their sub-field digests in an `_sd` array inside the object (instead of literal dotted top-level names like `address.city`), and array claims disclose per element through `{"...": <digest>}` placeholders (instead of several disclosures sharing one claim name, which resolves to a duplicate claim). eudi-dev enforces RFC 9901 §7.1 on import since v1.19.18 and rejects the old shapes
- `getTrustLists()` no longer fails on a trust-list profile without attestations, such as the wallet-provider list eudi-dev serves since v1.19.1

## [2.0.1] - 2026-08-04

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.18.4`
- consent is per channel since eudi-dev v1.18.0: `acceptCredentialOffer(...)` and `acceptPresentationRequest(...)` accept even on a wallet started `withoutAutoAccept()`, because the API call is the caller's own consent
- `WalletConfig` gained `version()` and `tlsListener()` (false when an external TLS terminator serves the issuer origin, so the built-in HTTPS listener is disabled)

### Added

- interactive consent submissions: `submitCredentialOfferForConsent(uri)` / `submitPresentationRequestForConsent(uri)` submit through the interactive channel so a wallet without auto-accept raises a consent request, and `awaitPendingRequest(timeout)` waits for it. Both submissions return a `CompletableFuture` because the wallet holds them open until the request is decided

## [2.0.0] - 2026-08-03

### Changed

- **Renamed to `testcontainers-eudi`**, following the wrapped tool's rename from `oid4vc-dev` to [`eudi-dev`](https://github.com/dominikschlosser/eudi-dev). The Maven artifact is `io.github.dominikschlosser:testcontainers-eudi`, the Java package is `io.github.dominikschlosser.eudi`, and the container class is `EudiWalletContainer`. Versions up to 1.6.0 remain available as `testcontainers-oid4vc`
- pinned the default wallet image to `ghcr.io/dominikschlosser/eudi-dev:v1.16.1`
- container readiness now waits on `GET /api/version` instead of `/`
- TLS certificate exports now use the wallet management HTTP API (`GET /api/certificates/tls|ca`) instead of `exec`ing the CLI in the container
- `Credential` now carries the raw credential (`raw()`) and its status-list metadata (`status()`)
- trust-list JWT payloads are nested under a top-level `LoTE` object since eudi-dev v1.7.1 (spec-conformant shape)

### Added

- credential issuance via the wallet's issuer key: `WalletClient.issueCredential(IssueRequest)` mirrors `eudi issue sdjwt|jwt|mdoc --wallet`, with support for claims, templates, `always_disclosed` SD-JWT claims, per-attribute mDoc namespaces (`namespace:element` claim keys), expiry/not-before, status-list references, and trust profiles
- credential template management: `getTemplates()`, `getTemplate(name)`, `saveTemplate(CredentialTemplate)`, `deleteTemplate(name)`, plus `EudiWalletContainer.withTemplatesDirectory(Path)` to ship a folder of template JSON files into the container (`--templates-dir`)
- credential status handling: `getCredentialStatus(id)` resolves the live status from the wallet's own status list or by fetching an external one; credential summaries include a typed `CredentialStatusInfo`
- consent handling for wallets without auto-accept: `getPendingRequests()`, `approveRequest(id[, selectedClaims])`, `denyRequest(id)`
- wallet introspection: `getConfig()` returns the instance document (`GET /api/config`), `getActivityLog()` / `clearActivityLog()` expose the wallet activity log
- single-credential fetch (`getCredential(id)`) and bulk delete (`deleteAllCredentials()`)
- JWKS certificate exports (`getWalletTlsCertificateJwks()`, `getWalletTlsCaCertificateJwks()`), e.g. for Keycloak trust configuration
- credential offer endpoint URLs (`getCredentialOfferUrl()`, `getHttpsCredentialOfferUrl()`) for delivering offers by plain web URL instead of the `openid-credential-offer://` scheme
- new container options: `withHaip()` (enforce HAIP 1.0), `withStrictValidation()` (strict wallet validation mode), `withVciClientId(...)` / `withVciRedirectUri(...)` (OID4VCI authorization-code flows)

### Removed

- the v1.5.0 issuer-focused certificate aliases (`getIssuerTlsCertificatePem`, `exportIssuerTlsCertificate`, `getIssuerTlsCaCertificatePem`, `exportIssuerTlsCaCertificate`) — use the `WalletTls*` methods
- the `withStatusListBaseUrl(...)` compatibility alias — use `withBaseUrl(...)`

## [1.6.0] - 2026-03-22

### Added

- trust-list discovery support for `oid4vc-dev v1.7.0`, including `/api/trustlists`, `/api/trustlists/{id}`, and legacy `vct` / `doctype` selectors
- typed `TrustListIndexEntry` and `TrustListAttestation` records for wallet trust-list discovery results
- URL resolution helpers for trust-list discovery entries so Testcontainers callers can resolve relative wallet `path` values against mapped host URLs

### Changed

- pinned the default wallet image to `ghcr.io/dominikschlosser/oid4vc-dev:v1.7.0`
- updated README usage examples to document trust-list discovery and mapped-URL resolution
