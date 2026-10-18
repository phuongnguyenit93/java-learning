<a id="back-to-top"></a>

# Certificates, Certification Paths, and Trust

## Menu
- [X.509 Certificate Model](#x509-certificate-model)
- [Certificate Chains and Certification Paths](#certification-path)
- [Trust Anchors and Path Validation](#trust-anchor-validation)
- [Certificate Revocation Checking](#certificate-revocation)
- [Trust Validation vs Identity Verification](#trust-vs-identity)

## <a id="x509-certificate-model">X.509 Certificate Model</a>

<details>
<summary>Click for details</summary>

An X.509 certificate binds a **public key** to subject/identity information and metadata such as issuer, validity, and extensions. The certificate is signed by an issuer so a verifier can validate that binding within a trust model.

The certificate does not contain the private key. Private key material typically lives separately, for example in a PrivateKeyEntry, while certificates can be distributed publicly.

Java X509Certificate exposes subject/issuer information, public key, validity, SAN values, key usage, and extensions.

Parsing a certificate is not the same as trusting it. Trust requires certification-path validation against configured trust anchors and policy.
</details>

- [Back to top](#back-to-top)

---

## <a id="certification-path">Certificate Chains and Certification Paths</a>

<details>
<summary>Click for details</summary>

A leaf certificate is often trusted through a **certification path** that leads toward a trust anchor:

~~~text
leaf certificate
    ↓ signed by
intermediate CA
    ↓ issued toward
certificate issued by the trust anchor

trust anchor
    = separately configured trusted input
    ≠ a certificate contained in the CertPath
~~~

A peer usually supplies the leaf and necessary intermediate certificates. The trust anchor normally comes from local trust configuration and does not need to be transmitted by the peer.

Java models certificate/path data through `CertificateFactory` and `CertPath`; `CertPathBuilder` can build candidate paths from available material. By convention, an X.509 `CertPath` starts with the target certificate and ends with a certificate **issued by the trust anchor**; the certificate representing the trust anchor itself should not be included in the `CertPath`.

A chain merely existing does not mean it is valid. Validation still checks signatures, validity, constraints, intended usage, and the relationship to a trusted anchor.
</details>

- [Back to top](#back-to-top)

---

## <a id="trust-anchor-validation">Trust Anchors and Path Validation</a>

<details>
<summary>Click for details</summary>

CertPathValidator validates a certification path with an algorithm such as PKIX.

~~~java
CertPathValidator validator = CertPathValidator.getInstance("PKIX");
PKIXParameters params = new PKIXParameters(trustAnchors);
params.setRevocationEnabled(true);

CertPathValidatorResult result =
        validator.validate(certPath, params);
~~~

This answers “does the path reach an accepted trust anchor and satisfy the validation policy?” The `TrustAnchor` is supplied separately through `PKIXParameters`, and PKIX results expose it separately from the `CertPath`. Path validation does not by itself answer whether the certificate represents the hostname the application intended to contact.

Trust anchors are local policy inputs. If an attacker can modify the trust store and add a new CA, cryptographic path validation may still succeed under the compromised policy.
</details>

- [Back to top](#back-to-top)

---

## <a id="certificate-revocation">Certificate Revocation Checking</a>

<details>
<summary>Click for details</summary>

A certificate can still be within its validity period after its private key has been compromised or the issuer has revoked it. Revocation checking lets the verifier incorporate status information such as CRLs or OCSP according to PKIX/provider configuration.

CertPathValidator can expose a PKIXRevocationChecker for more detailed control.

Revocation has operational trade-offs: network availability, caching, freshness, and soft-fail versus hard-fail behavior all affect reliability and security. There is no universal setting for every system.

The important point is to choose failure policy deliberately. Disabling revocation merely to remove certificate errors can turn an incident signal into a trust bypass.
</details>

- [Back to top](#back-to-top)

---

## <a id="trust-vs-identity">Trust Validation vs Identity Verification</a>

<details>
<summary>Click for details</summary>

Two checks answer different questions:

1. **trust validation** — does the certificate/path satisfy the configured CA/trust-anchor policy?
2. **identity verification** — does the certificate represent the endpoint or hostname that the application intended to reach?

A certificate can be perfectly trusted for attacker.example and still be wrong for api.example.

TLS clients generally need both checks. JSSE/HTTP stacks support endpoint identification based on hostname and certificate identity data.

A TrustManager that accepts everything or a hostname verifier that always returns true removes part of the security contract. Trust says “this path is acceptable”; identity says “this key belongs to the peer I meant to contact.”
Once key material, certification paths, trust, and endpoint identity are understood, those pieces can be combined in the secure-channel model provided by JSSE/TLS.

</details>

- [Back to top](#back-to-top)
