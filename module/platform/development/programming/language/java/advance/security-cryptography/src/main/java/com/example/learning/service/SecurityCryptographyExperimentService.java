package com.example.learning.service;

import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SecurityCryptographyExperimentService {

    private static final byte[] MESSAGE =
            "amount=100&currency=USD".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();

    public Map<String, Object> macTamper() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256, secureRandom);
        SecretKey key = generator.generateKey();

        byte[] originalTag = mac(key, MESSAGE);
        byte[] tamperedMessage = MESSAGE.clone();
        tamperedMessage[0] ^= 1;

        boolean originalValid = MessageDigest.isEqual(
                originalTag,
                mac(key, MESSAGE)
        );
        boolean tamperedValid = MessageDigest.isEqual(
                originalTag,
                mac(key, tamperedMessage)
        );

        return Map.of(
                "algorithm", "HmacSHA256",
                "keySizeBits", 256,
                "originalValid", originalValid,
                "tamperedValid", tamperedValid,
                "observation", "same key + changed message produces a different MAC"
        );
    }

    public Map<String, Object> signatureTamper() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, secureRandom);
        KeyPair pair = generator.generateKeyPair();

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate(), secureRandom);
        signer.update(MESSAGE);
        byte[] signature = signer.sign();

        boolean originalValid = verifySignature(pair, MESSAGE, signature);

        byte[] tamperedMessage = MESSAGE.clone();
        tamperedMessage[tamperedMessage.length - 1] ^= 1;
        boolean tamperedValid = verifySignature(pair, tamperedMessage, signature);

        return Map.of(
                "algorithm", "SHA256withRSA",
                "originalValid", originalValid,
                "tamperedValid", tamperedValid,
                "observation", "the same signature no longer verifies after the signed bytes change"
        );
    }

    public Map<String, Object> aeadTamperDetection() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, secureRandom);
        SecretKey key = generator.generateKey();

        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        GCMParameterSpec parameters = new GCMParameterSpec(128, nonce);

        Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding");
        encryptor.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] ciphertextAndTag = encryptor.doFinal(MESSAGE);

        Cipher decryptor = Cipher.getInstance("AES/GCM/NoPadding");
        decryptor.init(Cipher.DECRYPT_MODE, key, parameters);
        byte[] recovered = decryptor.doFinal(ciphertextAndTag);

        byte[] tampered = ciphertextAndTag.clone();
        tampered[0] ^= 1;

        String tamperFailure = null;
        try {
            Cipher tamperedDecryptor = Cipher.getInstance("AES/GCM/NoPadding");
            tamperedDecryptor.init(Cipher.DECRYPT_MODE, key, parameters);
            tamperedDecryptor.doFinal(tampered);
        }
        catch (AEADBadTagException exception) {
            tamperFailure = exception.getClass().getSimpleName();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transformation", "AES/GCM/NoPadding");
        result.put("originalRoundTrip", Arrays.equals(MESSAGE, recovered));
        result.put("tamperFailure", tamperFailure);
        result.put("tamperingRejected", "AEADBadTagException".equals(tamperFailure));
        result.put("observation", "changing authenticated ciphertext makes tag verification fail");
        return result;
    }

    public Map<String, Object> compareKeyEstablishment() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = generator.generateKeyPair();
        KeyPair bob = generator.generateKeyPair();

        byte[] aliceSecret = null;
        byte[] bobSecret = null;
        byte[] senderKemSecret = null;
        byte[] receiverKemSecret = null;

        try {
            aliceSecret = agree(alice, bob);
            bobSecret = agree(bob, alice);

            KeyPair kemReceiver = generator.generateKeyPair();
            KEM kem = KEM.getInstance("DHKEM");
            KEM.Encapsulated encapsulated =
                    kem.newEncapsulator(kemReceiver.getPublic()).encapsulate();
            SecretKey decapsulated =
                    kem.newDecapsulator(kemReceiver.getPrivate())
                            .decapsulate(encapsulated.encapsulation());

            senderKemSecret = encapsulated.key().getEncoded();
            receiverKemSecret = decapsulated.getEncoded();
            boolean kemSecretExtractable =
                    senderKemSecret != null && receiverKemSecret != null;

            return Map.of(
                    "keyAgreementAlgorithm", "X25519",
                    "keyAgreementSecretsMatch", MessageDigest.isEqual(aliceSecret, bobSecret),
                    "kemAlgorithm", "DHKEM",
                    "kemSecretExtractable", kemSecretExtractable,
                    "kemSecretsMatch", kemSecretExtractable &&
                            MessageDigest.isEqual(senderKemSecret, receiverKemSecret),
                    "kemSecretAlgorithm", encapsulated.key().getAlgorithm(),
                    "secretMaterialReturned", false,
                    "observation", "KeyAgreement and KEM establish matching secret material through different interaction models"
            );
        }
        finally {
            clear(aliceSecret);
            clear(bobSecret);
            clear(senderKemSecret);
            clear(receiverKemSecret);
        }
    }

    public Map<String, Object> passwordDerivationSaltEffect() throws GeneralSecurityException {
        char[] password = "training-password-only".toCharArray();
        byte[] firstSalt = "demo-salt-A-2026".getBytes(StandardCharsets.UTF_8);
        byte[] secondSalt = "demo-salt-B-2026".getBytes(StandardCharsets.UTF_8);
        int iterations = 20_000;
        byte[] first = null;
        byte[] repeated = null;
        byte[] differentSalt = null;

        try {
            first = derive(password, firstSalt, iterations);
            repeated = derive(password, firstSalt, iterations);
            differentSalt = derive(password, secondSalt, iterations);

            return Map.of(
                    "algorithm", "PBKDF2WithHmacSHA256",
                    "iterations", iterations,
                    "sameParametersSameOutput", MessageDigest.isEqual(first, repeated),
                    "differentSaltChangesOutput", !MessageDigest.isEqual(first, differentSalt),
                    "derivedKeyReturned", false,
                    "observation", "salt and KDF parameters are part of the derivation contract",
                    "parameterNote", "20,000 iterations is a bounded learning-demo value, not a production recommendation",
                    "saltNote", "fixed salts are used only to make this comparison repeatable; real records need an appropriate unique salt strategy"
            );
        }
        finally {
            Arrays.fill(password, '\0');
            clear(first);
            clear(repeated);
            clear(differentSalt);
        }
    }

    private byte[] mac(SecretKey key, byte[] message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        return mac.doFinal(message);
    }

    private boolean verifySignature(
            KeyPair pair,
            byte[] message,
            byte[] signatureBytes
    ) throws GeneralSecurityException {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        return verifier.verify(signatureBytes);
    }

    private byte[] agree(KeyPair local, KeyPair peer) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(local.getPrivate());
        agreement.doPhase(peer.getPublic(), true);
        return agreement.generateSecret();
    }

    private byte[] derive(
            char[] password,
            byte[] salt,
            int iterations
    ) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            if (encoded == null) {
                throw new GeneralSecurityException(
                        "PBKDF2 provider returned non-extractable derived key material"
                );
            }
            return encoded;
        }
        finally {
            spec.clearPassword();
        }
    }

    private void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }
}
