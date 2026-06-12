package xss.it.kosmix.helper.settings;

import xss.it.kosmix.helper.ec.AesEncryption;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.settings package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Abstract foundation of the settings stack: a {@link Properties} bag
 * persisted to a plain text file, with optional AES-256/GCM encryption
 * of individual values when a secret key is supplied.
 */
public abstract class BaseSettings {
    /**
     * Holds the application properties.
     */
    protected final Properties properties;

    /**
     * The file path where the properties are stored.
     */
    protected final String filePath;

    /**
     * The secret key used to encrypt/decrypt sensitive values, or
     * {@code null} when encryption is disabled.
     */
    protected final String secretKey;

    /**
     * Constructs a BaseSettings instance with the specified file path and
     * secret key. Initializes the properties and loads the file when it
     * already exists on disk.
     *
     * @param filePath  the path to the settings file
     * @param secretKey the secret key for value encryption, may be
     *                  {@code null} or blank to disable encryption
     */
    public BaseSettings(String filePath, String secretKey) {
        this.properties = new Properties();
        this.filePath = filePath;
        this.secretKey = (secretKey == null || secretKey.isBlank()) ? null : secretKey;
        /*
         * Load the file if it exists.
         */
        if (Files.exists(Paths.get(filePath))) {
            loadFromFile();
        }
    }

    /**
     * Returns the properties object containing the application settings.
     *
     * @return the Properties object
     */
    public final Properties getProperties() {
        return properties;
    }

    /**
     * Returns the file path where the settings are stored.
     *
     * @return the file path as a String
     */
    public final String getFilePath() {
        return filePath;
    }

    /**
     * Copies the settings from the provided resource input stream into the
     * settings file.
     *
     * @param stream  the InputStream from which to copy the defaults
     * @param refresh if {@code true} the current values are replaced;
     *                otherwise only missing keys are filled in
     * @throws IOException if an I/O error occurs during copying
     */
    public final void copyFromResource(InputStream stream, boolean refresh) throws IOException {
        final Properties others = readFromResources(stream);
        if (refresh) {
            properties.clear();
            properties.putAll(others);
        } else {
            others.forEach((k, v) -> {
                if (!properties.containsKey(k)) {
                    properties.put(k, v);
                }
            });
        }
        saveToFile();
    }

    /**
     * Reads properties from the provided resource input stream.
     *
     * @param stream the InputStream from which to load the properties
     * @return a Properties object containing the loaded properties
     * @throws IOException if an I/O error occurs while reading
     */
    public final Properties readFromResources(InputStream stream) throws IOException {
        final Properties copy = new Properties();
        copy.load(stream);
        return copy;
    }

    /**
     * Saves the current properties to the settings file.
     *
     * @throws RuntimeException if an I/O error occurs during saving
     */
    protected final void saveToFile() {
        try (OutputStream outputStream = new FileOutputStream(filePath)) {
            properties.store(outputStream, "Kosmix Settings");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads the properties from the settings file.
     *
     * @throws RuntimeException if an I/O error occurs during loading
     */
    final void loadFromFile() {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Encrypts the given value using AES with the configured secret key.
     *
     * @param value the value to be encrypted
     * @return the encrypted value as a Base64 encoded string
     * @throws Exception if encryption fails
     */
    protected final String encrypt(String value) throws Exception {
        return AesEncryption.encrypt(value, AesEncryption.toKey(secretKey));
    }

    /**
     * Decrypts the given encrypted value using AES with the configured
     * secret key.
     *
     * @param encryptedValue the encrypted value as a Base64 encoded string
     * @return the decrypted value as a plain string
     * @throws Exception if decryption fails
     */
    protected final String decrypt(String encryptedValue) throws Exception {
        return AesEncryption.decrypt(encryptedValue, AesEncryption.toKey(secretKey));
    }
}
