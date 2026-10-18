<a id="back-to-top"></a>

# Key Derivation

## Menu
- [What Problem Does Key Derivation Solve?](#key-derivation-purpose)
- [Passwords as Input Key Material](#password-key-material)
- [SecretKeyFactory and PBEKeySpec for Password-Based Derivation](#password-kdf-java-api)
- [Salt, Work Factors, and Derivation Cost](#salt-and-work-factor)
- [Using Derived Keys for the Right Purpose](#derived-key-usage)

## <a id="key-derivation-purpose">What Problem Does Key Derivation Solve?</a>

<details>
<summary>Click for details</summary>

**Key derivation** transforms input material into key material with properties suitable for a defined purpose.

The input may come from a password or from a protocol secret. This chapter focuses on password-based derivation because that is the module's curriculum ownership.

Human passwords usually have low and uneven entropy. Using password bytes directly as an AES or HMAC key makes offline guessing too cheap.

A password-based derivation mechanism introduces a salt and deliberate computational work so that each password guess costs more and precomputed results cannot be reused across records as easily.
</details>

- [Back to top](#back-to-top)

---

## <a id="password-key-material">Passwords as Input Key Material</a>

<details>
<summary>Click for details</summary>

A password is **human-chosen input material**, not a ready-to-use cryptographic key.

PBEKeySpec stores the password as char[] so callers have an opportunity to clear the data when it is no longer needed:

~~~java
char[] password = ...;
PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
try {
    // derive key material
} finally {
    spec.clearPassword();
    Arrays.fill(password, '\0');
}
~~~

Clearing is good hygiene, not proof that every copy has disappeared from stacks, providers, or runtime internals. The goal is to reduce unnecessary lifetime rather than promise perfect memory erasure.
</details>

- [Back to top](#back-to-top)

---

## <a id="password-kdf-java-api">SecretKeyFactory and PBEKeySpec for Password-Based Derivation</a>

<details>
<summary>Click for details</summary>

Password-based derivation commonly uses **SecretKeyFactory plus PBEKeySpec**:

~~~java
SecretKeyFactory factory =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

PBEKeySpec spec =
        new PBEKeySpec(password, salt, iterations, 256);

byte[] derived;
try {
    derived = factory.generateSecret(spec).getEncoded();
} finally {
    spec.clearPassword();
}
~~~

The algorithm above is a common provider-supported example, not a permanent recommendation for every security policy. Select the KDF and parameters according to current requirements, threat model, and interoperability needs.

If the bytes become an AES/HMAC key, document the expected length, encoding, and purpose so all participants derive the same material.
</details>

- [Back to top](#back-to-top)

---

## <a id="salt-and-work-factor">Salt, Work Factors, and Derivation Cost</a>

<details>
<summary>Click for details</summary>

A **salt** is not secret. It should be unique/random enough that equal passwords in different records do not produce the same derived result and that one precomputed table cannot be reused globally.

The **work factor** makes each password guess deliberately more expensive. With PBKDF2, iteration count is part of the parameter set. A suitable value is not timeless; hardware and policy change, so production systems should calibrate and version their parameters.

A record generally needs enough information to reproduce derivation:

~~~text
KDF identifier/version
salt
work factor / parameters
derived verifier or protected payload
~~~

Avoid one fixed global salt. Also avoid work factors that turn legitimate authentication into self-inflicted denial of service.
</details>

- [Back to top](#back-to-top)

---

## <a id="derived-key-usage">Using Derived Keys for the Right Purpose</a>

<details>
<summary>Click for details</summary>

Derived key material should have a **defined purpose**. Reusing the same bytes across unrelated primitives or protocol contexts can couple security assumptions in unintended ways.

If a protocol needs multiple keys, follow its specified key-separation/derivation design instead of slicing or hashing bytes ad hoc.

Store the salt and KDF parameters with the protected record so future code can derive the same material. Do not store the password alongside the record in plaintext.

Password derivation also does not turn a weak password into a high-entropy secret. It increases the cost of guessing; password quality, rate limiting, MFA, and credential policy remain broader application concerns.
Generated or derived keys still need to be loaded, stored, and protected across their lifecycle; that is where KeyStore becomes relevant.

</details>

- [Back to top](#back-to-top)
