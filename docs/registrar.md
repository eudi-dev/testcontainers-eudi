# Registrar and attestation catalogue

The wallet includes a relying party registrar (eudi-dev 3.0.0 or later). Register your verifier or your issuer with it and get two certificates:

- An **access certificate** (ETSI TS 119 411-8). A verifier signs its request objects with the matching key. An issuer signs its Credential Issuer Metadata with it.
- A **registration certificate** (ETSI TS 119 475). A verifier sends it in `verifier_info`. An issuer publishes it in `issuer_info`.

Start the wallet with `withArf()` and it checks both certificates against the ARF. See [ARF checks](#arf-checks).

Everything on this page runs on `WalletClient client = wallet.client()`.

## Registering a verifier

A verifier registers one intended use. It lists the permitted credentials and claims, and a purpose for the consent dialog:

```java
RelyingParty shop = client.registerRelyingParty(RelyingPartyRegistration.verifier("Example Shop")
    .purpose("Age check")
    .credential(CredentialFormat.SD_JWT, "urn:eudi:pid:1", "age_equal_or_over.18"));

String identifier = shop.identifier();                       // such as NLTEST.1A2B3C4D5E6F7A8B
String intendedUse = shop.intendedUses().getFirst().identifier();
```

SD-JWT claims are dotted paths such as `address.locality`. An mdoc claim is an element name in the doctype's namespace. Put another namespace in front with a colon, such as `eu.europa.ec.eudi.pid.de.1:birth_name`.

The registrar fills in missing fields: the identifier, the legal name, the country, the support URL and the privacy policy URL. Set them with `identifier(...)`, `legalName(...)`, `country(...)`, `supportUri(...)` and `privacyPolicy(...)` when your test needs specific values.

Then request both certificates. Create the key and the certificate signing request yourself, for example with `openssl`. The key must be P-256:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out verifier.key
openssl req -new -key verifier.key -subj "/" -out verifier.csr
```

```java
AccessCertificate access = client.issueAccessCertificate(identifier, csrPem, "shop.example");
String chainPem = access.chain();              // leaf and relying party access CA
List<String> clientIds = access.clientIds();    // x509_hash:... and x509_san_dns:shop.example

RegistrationCertificate registration = client.issueRegistrationCertificate(identifier, intendedUse);
String verifierInfo = registration.verifierInfo(); // JSON array for the verifier_info request parameter
```

Your verifier signs its request object with `verifier.key` and puts `access.certificate()` in `x5c`. It sends `verifierInfo` as `verifier_info`.

## Registering an issuer

An issuer registers its attestation types. The registrar gives it the entitlement of each type's category in the catalogue, for example `PID_PROVIDER` for a PID. `entitlement(ProviderEntitlement)` sets another one:

```java
RelyingParty university = client.registerRelyingParty(RelyingPartyRegistration.issuer("Example University")
    .attestation(CredentialFormat.SD_JWT, "urn:example:diploma:1"));

AccessCertificate access = client.issueAccessCertificate(university.identifier(), csrPem);
RegistrationCertificate registration = client.issueRegistrationCertificate(university.identifier());
String issuerInfo = registration.issuerInfo();
```

Put the `issuerInfo` array at the top level of the Credential Issuer Metadata. Sign the metadata with the access certificate key (OpenID4VCI 1.0 §12.2.3) and serve it when a wallet asks for `application/jwt`.

## Registering in one step

`enrol` registers a verifier or an issuer and issues both certificates in one request. If a certificate fails, nothing is stored:

```java
Enrolment enrolment = client.enrol(RelyingPartyRegistration.verifier("Example Shop")
    .purpose("Age check")
    .credential(CredentialFormat.SD_JWT, "urn:eudi:pid:1", "age_equal_or_over.18"), csrPem, "shop.example");

String chainPem = enrolment.accessCertificate().chain();
String verifierInfo = enrolment.registrationCertificate().verifierInfo();
```

For an issuer the registration certificate covers its service and carries `issuerInfo()`. `updateEnrolment(...)` changes an existing registration the same way. Set the registration's `identifier(...)` for that.

`updateRelyingParty(registration)` replaces a registration without new certificates. If an intended use or a service changes or is missing, its certificates are revoked.

## Registrations from files

`withRelyingParties(Path)` registers verifiers and issuers on every start, from a YAML or JSON file or a directory of such files. An entry has the shape of a TS05 registration. It sets the `identifier` of the party and the `intendedUseIdentifier` of each intended use, so the certificates stay the same on every start. The registrar issues a registration certificate for every intended use and issuer service. See [pre-registered relying parties](https://github.com/eudi-dev/eudi-dev/blob/main/docs/wallet/serve.md#pre-registered-relying-parties) for the format.

`withCatalog(Path)` adds attestation types to the catalogue the same way, see [pre-registered catalogue entries](https://github.com/eudi-dev/eudi-dev/blob/main/docs/wallet/serve.md#pre-registered-catalogue-entries).

## Revoking registration certificates

Every registration certificate has an entry in the registrar's status list. Revoke it to test how your verifier or issuer behaves when the wallet refuses it:

```java
client.revokeRegistrationCertificates(identifier, intendedUse);   // one intended use
client.activateRegistrationCertificates(identifier, intendedUse);

client.revokeRegistrationCertificates(identifier);                // every certificate of the relying party
List<RegistrationCertificateStatus> statuses = client.getRegistrationCertificateStatuses(identifier);
```

Issuing another certificate for the same intended use revokes the previous one. So does deleting the relying party. The registrar marks these as `superseded()`, and they can't be activated again.

## Listing and deleting registrations

```java
List<RelyingParty> parties = client.getRelyingParties(); // the demo issuer and the demo verifier come first
RelyingParty shop = client.getRelyingParty(identifier);
client.deleteRelyingParty(identifier);
```

The registrar certificate signs the registration certificates. The relying party access CA signs the access certificates. Both are available as PEM:

```java
String registrarPem = client.getRegistrarCertificatePem();
String accessCaPem = client.getRelyingPartyAccessCaPem();
```

## ARF checks

`withArf()` makes the wallet check the access and registration certificates of every verifier and issuer. In the default debug mode it logs the findings and continues. Add `withStrictValidation()` and it refuses the request. A verifier that signed its request with a trusted access certificate gets an `access_denied` error response that names the findings (OpenID4VP 1.0 §8.5):

```java
@Container
static EudiWalletContainer wallet = new EudiWalletContainer()
    .withArf()
    .withStrictValidation();

PresentationResponse refusal = wallet.acceptPresentationRequest(requestUri);
refusal.status();           // refused
refusal.error();            // access_denied
refusal.errorDescription(); // The request does not meet the ARF registration rules: ARF RPRC_21: ...
```

A missing registration certificate is a finding in every mode. CIR (EU) 2026/1731 defers that check to 11 August 2028. A registration certificate that is present is checked, and strict mode refuses it when it fails.

The wallet sends no response to a verifier it can't authenticate. Then the call throws a `WalletClientException` with status 400, as described in [Flows](flows.md).

The wallet trusts the certificates of its own registrar. To trust another registrar, pass its CA certificates with `withRelyingPartyCa(Path)`. A received credential must chain to a trusted list signed by the wallet or by a CA from `withTrustedListCa(Path)`. Call each once per PEM file. `setRequireArf(boolean)` switches the checks at runtime, see [Conformance settings](configuration.md#conformance-settings).

## Attestation catalogue

The registrar keeps a catalogue of attestation types, like the catalogue of attestations in EC TS11. Every predefined template is in it. With `withArf()` a received credential must chain to the trusted list of its catalogue entry, and the wallet warns about an offered type without an entry.

```java
List<CatalogAttestation> catalogue = client.getCatalogAttestations();

CatalogAttestation diploma = client.addCatalogAttestation(CatalogEntry.named("University diploma")
    .credential(CredentialFormat.SD_JWT, "urn:example:diploma:1", "degree")
    .category("qeaa")
    .rulebook("https://example.com/diploma-rulebook")
    .levelOfSecurity("iso_18045_moderate")
    .trustedList("https://example.com/diploma-issuers"));

AttestationSchema schema = client.getAttestationSchema(diploma.id()); // TS11 SchemaMeta
client.deleteCatalogAttestation(diploma.id());
```

The trusted list is an ETSI TS 119 602 list of trusted entities. The category is `pid`, `qeaa`, `pub-eaa` or `eaa` (the default). The wallet signs credentials of the type for the trusted list of that category, and an entry without `trustedList` links that list. The next start restores a deleted entry of a predefined template.

To add a user template to the catalogue, save it with a catalogue entry (see [Templates](credentials.md#templates)).
