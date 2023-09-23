package org.embeddedt.archaicfix.config;

/**
 * A really basic wrapper for config to simplify handling them in external code.
 */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(Throwable cause) {
        super(cause);
    }
}
