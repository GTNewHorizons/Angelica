package com.prupe.mcpatcher.mal.resource;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;

final public class PropertiesFile {

    private final MCLogger logger;
    private final ResourceLocation resource;
    private final String prefix;
    private final Properties properties;
    private int errorCount;

    public static PropertiesFile get(MCLogger logger, ResourceLocation resource) {
        Properties properties = TexturePackAPI.getProperties(resource);
        if (properties == null) {
            return null;
        } else {
            return new PropertiesFile(logger, resource, properties);
        }
    }

    public static PropertiesFile getNonNull(MCLogger logger, ResourceLocation resource) {
        PropertiesFile propertiesFile = get(logger, resource);
        if (propertiesFile == null) {
            return new PropertiesFile(logger, resource, new Properties());
        } else {
            return propertiesFile;
        }
    }

    public PropertiesFile(MCLogger logger, ResourceLocation resource) {
        this(logger, resource, new Properties());
    }

    public PropertiesFile(MCLogger logger, ResourceLocation resource, Properties properties) {
        this.logger = logger;
        this.resource = resource;
        prefix = resource == null ? "" : (resource + ": ").replace("%", "%%");
        this.properties = properties;
    }

    public String getString(String key, String defaultValue) {
        return MCPatcherUtils.getStringProperty(properties, key, defaultValue);
    }

    public ResourceLocation getResourceLocation(String key, String defaultValue) {
        String value = getString(key, defaultValue);
        return TexturePackAPI.parseResourceLocation(resource, value);
    }

    public int getInt(String key, int defaultValue) {
        return MCPatcherUtils.getIntProperty(properties, key, defaultValue);
    }

    public int getHex(String key, int defaultValue) {
        return MCPatcherUtils.getHexProperty(properties, key, defaultValue);
    }

    public float getFloat(String key, float defaultValue) {
        return MCPatcherUtils.getFloatProperty(properties, key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return MCPatcherUtils.getDoubleProperty(properties, key, defaultValue);
    }

    public int[] getIntList(String key, int minValue, int maxValue, String defaultValue) {
        String value = getString(key, defaultValue);
        if (value == null) {
            return null;
        } else {
            return MCPatcherUtils.parseIntegerList(value, minValue, maxValue);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return MCPatcherUtils.getBooleanProperty(properties, key, defaultValue);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public void finer(String format, Object... params) {
        logger.finer(prefix + format, params);
    }

    public void fine(String format, Object... params) {
        logger.fine(prefix + format, params);
    }

    public void config(String format, Object... params) {
        logger.config(format, params);
    }

    public void warning(String format, Object... params) {
        logger.warning(prefix + format, params);
    }

    public boolean error(String format, Object... params) {
        logger.error(prefix + format, params);
        errorCount++;
        return false;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public boolean valid() {
        return getErrorCount() == 0;
    }

    @SuppressWarnings("unchecked")
    public Set<Map.Entry<String, String>> entrySet() {
        return (Set<Map.Entry<String, String>>) (Set) properties.entrySet();
    }

    public ResourceLocation getResource() {
        return resource;
    }

    @Override
    public String toString() {
        return resource.toString();
    }

    @Override
    public int hashCode() {
        return resource.hashCode();
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (!(that instanceof PropertiesFile)) {
            return false;
        } else {
            return resource.equals(((PropertiesFile) that).resource);
        }
    }
}
