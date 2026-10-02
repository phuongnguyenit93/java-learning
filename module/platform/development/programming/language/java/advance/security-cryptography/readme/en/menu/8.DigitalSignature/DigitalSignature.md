<a id="back-to-top"></a>

# Digital Signatures

## Menu
- [Digital Signature Model](#signature-model)
- [Signing and Verification](#sign-and-verify)
- [Signed Data and the Verification Contract](#signature-data-contract)
- [Digital Signatures vs MACs and Encryption](#signature-comparison)

## <a id="signature-model">Digital Signature Model</a>

<details>
<summary>Click for details</summary>

A digital signature uses an asymmetric key pair to separate **signing authority** from **verification authority**.

~~~text
message + private key
       ↓ sign
signature

message + signature + public key
       ↓ verify
true / false
~~~

The private key must remain protected; the public key can be distributed to verifiers. Verification is meaningful only if the verifier has a trustworthy binding between that public key and the expected signer identity.

That is why certificates and trust appear later: correct signature mathematics does not help if the application trusts the wrong public key.
</details>

- [Back to top](#back-to-top)

---

## <a id="sign-and-verify">Signing and Verification</a>

<details>
<summary>Click for details</summary>

Java uses the Signature engine in signing or verification state:

~~~java
Signature signer = Signature.getInstance("SHA256withRSA");
signer.initSign(privateKey);
signer.update(message);
byte[] signature = signer.sign();

Signature verifier = Signature.getInstance("SHA256withRSA");
verifier.initVerify(publicKey);
verifier.update(message);
boolean valid = verifier.verify(signature);
~~~

The algorithm name describes a signature scheme according to the provider contract. Production choices must follow protocol and current security policy, not merely an example that happens to run.

Signature is stateful. Avoid uncontrolled sharing of one instance across concurrent requests.
</details>

- [Back to top](#back-to-top)

---

## <a id="signature-data-contract">Signed Data and the Verification Contract</a>

<details>
<summary>Click for details</summary>

A signature protects the **exact bytes that were signed**, not the broader business meaning that a developer intended.

If a producer signs JSON bytes but a consumer parses and reserializes before verification, differences in whitespace, field ordering, or Unicode representation may break validation. Conversely, if only selected fields are signed while business logic trusts the whole object, unsigned fields may remain mutable.

A robust protocol defines:

- the exact payload or canonical representation;
- context/version binding;
- which keys or certificate identities may sign;
- replay handling when replay is part of the threat model.

The Signature API does not design that data contract for the application.
</details>

- [Back to top](#back-to-top)

---

## <a id="signature-comparison">Digital Signatures vs MACs and Encryption</a>

<details>
<summary>Click for details</summary>

A digital signature is not “encryption with the private key.” The operations have different goals and scheme semantics even when they use keys from the same mathematical family.

- **signature**: private key signs, public key verifies; goal is authenticity/integrity;
- **asymmetric encryption**: public key commonly protects data for the private-key holder; goal is confidentiality;
- **MAC**: one shared secret creates and verifies tags.

Thinking in terms of “reverse encryption” hides important details such as signature encoding, padding, and security proofs.

If a protocol needs confidentiality and signer identity, use a standard composition instead of substituting a reversed Cipher operation for a signature.
A signature provides authenticity but does not hide content, so the module now turns to confidentiality, beginning with symmetric encryption.

</details>

- [Back to top](#back-to-top)
