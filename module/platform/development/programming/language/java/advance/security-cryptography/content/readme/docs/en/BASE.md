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
