package com.traceguard.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Generates ECDSA (P-256) key pairs per checkpoint. Private keys are written to a key store folder
 * outside the database; only the public key (Base64 X.509) is stored with the checkpoint.
 */
@Service
public class KeyService {

    private final Path dir;

    public KeyService(@Value("${traceguard.keystore.dir}") String dir) {
        this.dir = Path.of(dir);
    }

    /** Creates and stores a key pair for the checkpoint; returns the Base64 public key. */
    public String generateFor(long checkpointId) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = gen.generateKeyPair();
            Files.createDirectories(dir);
            Files.writeString(file(checkpointId), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key generation failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PrivateKey privateKey(long checkpointId) {
        try {
            byte[] der = Base64.getDecoder().decode(Files.readString(file(checkpointId)).trim());
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Bad private key for checkpoint " + checkpointId, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PublicKey publicKey(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Bad public key", e);
        }
    }

    private Path file(long checkpointId) {
        return dir.resolve("checkpoint-" + checkpointId + ".key");
    }
}
