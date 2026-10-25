# 📂 README MODULE STRUCTURE (EN)

* **1.MentalModel**
    * [MentalModel](content/readme/docs/en/menu/1.MentalModel/knowledge/MentalModel.md)
* **2.EncodingVsEncryption**
    * [EncodingVsEncryption](content/readme/docs/en/menu/2.EncodingVsEncryption/knowledge/EncodingVsEncryption.md)
* **3.ProvidersAlgorithms**
    * [ProvidersAlgorithms](content/readme/docs/en/menu/3.ProvidersAlgorithms/knowledge/ProvidersAlgorithms.md)
* **4.SecureRandom**
    * [SecureRandom](content/readme/docs/en/menu/4.SecureRandom/knowledge/SecureRandom.md)
* **5.KeysKeyPair**
    * [KeysKeyPair](content/readme/docs/en/menu/5.KeysKeyPair/knowledge/KeysKeyPair.md)
* **6.MessageDigest**
    * [MessageDigest](content/readme/docs/en/menu/6.MessageDigest/knowledge/MessageDigest.md)
* **7.Mac**
    * [Mac](content/readme/docs/en/menu/7.Mac/knowledge/Mac.md)
* **8.DigitalSignature**
    * [DigitalSignature](content/readme/docs/en/menu/8.DigitalSignature/knowledge/DigitalSignature.md)
* **9.SymmetricEncryption**
    * [SymmetricEncryption](content/readme/docs/en/menu/9.SymmetricEncryption/knowledge/SymmetricEncryption.md)
* **10.AsymmetricEncryption**
    * [AsymmetricEncryption](content/readme/docs/en/menu/10.AsymmetricEncryption/knowledge/AsymmetricEncryption.md)
* **11.AuthenticatedEncryption**
    * [AuthenticatedEncryption](content/readme/docs/en/menu/11.AuthenticatedEncryption/knowledge/AuthenticatedEncryption.md)
* **12.KeyEstablishment**
    * [KeyEstablishment](content/readme/docs/en/menu/12.KeyEstablishment/knowledge/KeyEstablishment.md)
* **13.KeyDerivation**
    * [KeyDerivation](content/readme/docs/en/menu/13.KeyDerivation/knowledge/KeyDerivation.md)
* **14.KeyStore**
    * [KeyStore](content/readme/docs/en/menu/14.KeyStore/knowledge/KeyStore.md)
* **15.CertificatesTrust**
    * [CertificatesTrust](content/readme/docs/en/menu/15.CertificatesTrust/knowledge/CertificatesTrust.md)
* **16.TlsJsse**
    * [TlsJsse](content/readme/docs/en/menu/16.TlsJsse/knowledge/TlsJsse.md)
* **17.CryptoPitfalls**
    * [CryptoPitfalls](content/readme/docs/en/menu/17.CryptoPitfalls/knowledge/CryptoPitfalls.md)

# Security & Cryptography in Java

This module builds the mental model needed to use Java security and cryptography APIs deliberately. The goal is not to memorize algorithm names, but to understand which security goal must be solved, which primitive fits that goal, how Java selects implementations through the JCA/JCE provider architecture, and how key, certificate, and trust material participate in the complete flow.

## Recommended prerequisites

Learners should already understand Java Core, exception/resource handling, and basic random-number concepts. Networking knowledge helps when reaching JSSE/TLS, but socket and HTTP mechanics are not retaught here.

## Learning flow

The module progresses from foundations to integration:

1. identify security goals and distinguish encoding, hashing, MACs, encryption, and signatures;
2. understand JCA/JCE, engine classes, algorithm names, and the provider architecture;
3. build a mental model for SecureRandom and key material;
4. learn integrity and authenticity through MessageDigest, MACs, and digital signatures;
5. learn confidentiality through symmetric, asymmetric, and authenticated encryption;
6. distinguish shared-secret establishment from key derivation;
7. manage key and certificate material with KeyStore and the certification-path trust model;
8. connect these concepts to JSSE/TLS;
9. synthesize choices, constraints, and failure patterns into a safe cryptographic design flow.

## Module boundary

This module owns Java security-provider architecture, cryptographic primitives, key APIs, KeyStore/certificate APIs, and JSSE/TLS security semantics. General random-number concepts belong to Java Numbers; socket and HTTP mechanics belong to Java Networking; authentication and authorization framework behavior belongs to the corresponding security frameworks; infrastructure IAM, secret lifecycle, and PKI operations are outside this module's curriculum boundary.

## Expected outcome

After completing the module, learners should be able to reason from a security goal to a primitive, algorithm/provider, randomness and key material, storage/trust, and TLS where appropriate. They should also recognize failures such as using a digest as authentication, reusing a nonce, bypassing certificate validation, hard-coding secrets, or unnecessarily binding an application to one provider.
