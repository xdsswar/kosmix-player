package xss.it.kosmix.helper.settings;

import java.util.Optional;

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
 * Concrete settings store. Adds typed getters/setters over the
 * {@link BaseSettings} properties bag and transparent per-key AES
 * encryption: values written through {@link #setEncrypted(String, String)}
 * are stored with a marker prefix and decrypted automatically on read.
 */
public final class Settings extends BaseSettings {
    /**
     * Marker prefix identifying encrypted values inside the file.
     */
    private static final String PREFIX = "1!$-";

    /**
     * Constructs a Settings instance with the specified file path and no
     * secret key (encryption disabled).
     *
     * @param filePath the path to the settings file
     */
    public Settings(String filePath) {
        super(filePath, null);
    }

    /**
     * Constructs a Settings instance with the specified file path and
     * secret key.
     *
     * @param filePath  the path to the settings file
     * @param secretKey the secret key for value encryption, may be null
     */
    public Settings(String filePath, String secretKey) {
        super(filePath, secretKey);
    }

    /**
     * Sets a property with the given key and value, then saves the
     * updated properties to the file.
     *
     * @param key   the key for the property
     * @param value the value to be associated with the key
     */
    public void set(String key, String value) {
        properties.setProperty(key, value);
        saveToFile();
    }

    /**
     * Sets an encrypted property with the given key and value, then saves
     * it to the file.
     *
     * @param key   the key for the property
     * @param value the value to be encrypted and stored
     * @throws Exception if encryption fails or no secret key is set
     */
    public void setEncrypted(String key, String value) throws Exception {
        if (secretKey == null) {
            throw new NullSecretKeyException();
        }
        final String encryptedValue = encrypt(value);
        set(key, PREFIX + encryptedValue);
    }

    /**
     * Sets an integer property by converting the value to a string.
     *
     * @param key   the key for the property
     * @param value the integer value to be stored
     */
    public void setInteger(String key, int value) {
        set(key, String.valueOf(value));
    }

    /**
     * Sets a double property by converting the value to a string.
     *
     * @param key   the key for the property
     * @param value the double value to be stored
     */
    public void setDouble(String key, double value) {
        set(key, String.valueOf(value));
    }

    /**
     * Sets a long property by converting the value to a string.
     *
     * @param key   the key for the property
     * @param value the long value to be stored
     */
    public void setLong(String key, long value) {
        set(key, String.valueOf(value));
    }

    /**
     * Sets a boolean property by converting the value to a string.
     *
     * @param key   the key for the property
     * @param value the boolean value to be stored
     */
    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    /**
     * Retrieves the value associated with the specified key, decrypting
     * it transparently when it carries the encryption marker.
     *
     * @param key the key for the property
     * @return the value associated with the key, or {@code null}
     */
    public String get(String key) {
        String val = properties.getProperty(key);
        if (isEncrypted(val)) {
            if (secretKey == null) {
                throw new NullSecretKeyException();
            }
            val = val.replace(PREFIX, "");
            try {
                val = decrypt(val);
            } catch (Exception ignored) {
                val = null;
            }
        }
        return val;
    }

    /**
     * Retrieves the value for the key or the supplied default when the
     * key is absent or blank.
     *
     * @param key the key for the property
     * @param def the default value
     * @return the stored value or {@code def}
     */
    public String get(String key, String def) {
        final String val = get(key);
        return (val == null || val.isBlank()) ? def : val;
    }

    /**
     * Retrieves an integer value, falling back to the default when the
     * key is absent or unparsable.
     *
     * @param key the key for the property
     * @param def the default value
     * @return the stored integer or {@code def}
     */
    public int getInteger(String key, int def) {
        try {
            return Integer.parseInt(get(key));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Retrieves a double value, falling back to the default when the key
     * is absent or unparsable.
     *
     * @param key the key for the property
     * @param def the default value
     * @return the stored double or {@code def}
     */
    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Retrieves a long value, falling back to the default when the key is
     * absent or unparsable.
     *
     * @param key the key for the property
     * @param def the default value
     * @return the stored long or {@code def}
     */
    public long getLong(String key, long def) {
        try {
            return Long.parseLong(get(key));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Retrieves a boolean value, falling back to the default when the key
     * is absent.
     *
     * @param key the key for the property
     * @param def the default value
     * @return the stored boolean or {@code def}
     */
    public boolean getBoolean(String key, boolean def) {
        final String val = get(key);
        return (val == null) ? def : Boolean.parseBoolean(val);
    }

    /**
     * Retrieves an optional value for the key.
     *
     * @param key the key for the property
     * @return an Optional holding the value, empty when absent/blank
     */
    public Optional<String> getOpt(String key) {
        final String val = get(key);
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    /**
     * Checks whether a non-blank value exists for the key.
     *
     * @param key the key for the property
     * @return {@code true} when a usable value is stored
     */
    public boolean isSet(String key) {
        final String val = properties.getProperty(key);
        return val != null && !val.isBlank();
    }

    /**
     * Checks whether a raw stored value carries the encryption marker.
     *
     * @param value the raw stored value
     * @return {@code true} when the value is encrypted
     */
    private boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
