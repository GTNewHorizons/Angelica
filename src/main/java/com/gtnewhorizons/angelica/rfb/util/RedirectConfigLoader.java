package com.gtnewhorizons.angelica.rfb.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import com.gtnewhorizons.angelica.rfb.plugin.AngelicaRfbPlugin;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Loads redirect configurations from CSV files.
 */
public class RedirectConfigLoader {
    private static final Logger LOGGER = LogManager.getLogger("RedirectConfigLoader");
    private static final String REDIRECTS_PATH = "redirects/";
    private static final String METHOD_REDIRECTS_FILE = REDIRECTS_PATH + "method_redirects.csv";
    private static final String GLCAP_REDIRECTS_FILE = REDIRECTS_PATH + "glcap_redirects.csv";

    // CSV column names for method_redirects.csv
    private static final String SOURCE_CLASS = "source_class";
    private static final String SOURCE_METHOD = "source_method";
    private static final String SOURCE_DESCRIPTOR = "source_descriptor";
    private static final String TARGET_CLASS = "target_class";
    private static final String TARGET_METHOD = "target_method";
    private static final String TARGET_DESCRIPTOR = "target_descriptor";

    // CSV column names for glcap_redirects.csv
    private static final String LWJGL_CLASS = "lwjgl_class";
    private static final String ENUM_NAME = "enum_name";
    private static final String GL_CONSTANT_HEX = "gl_constant_hex";
    private static final String GL_CONSTANT_INT = "gl_constant_int";
    private static final String METHOD_NAME = "method_name";

    /**
     * Java Record representing a method redirect
     */
    public record MethodRedirect(String targetClass, String targetMethod, String targetDescriptor) {
        @Override
        public String toString() {
            return targetClass + "." + targetMethod + targetDescriptor;
        }
    }

    /**
     * Loads method redirects from method_redirects.csv.
     * Format: source_class,source_method,source_descriptor,target_class,target_method,target_descriptor
     *
     * @return Map of (source_class -> Map of (source_method+descriptor -> MethodRedirect object))
     */
    public static Map<String, Map<String, MethodRedirect>> loadMethodRedirects() {
        final Map<String, Map<String, MethodRedirect>> redirects = new Object2ObjectOpenHashMap<>();

        try (InputStream is = RedirectConfigLoader.class.getClassLoader().getResourceAsStream(METHOD_REDIRECTS_FILE);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    LOGGER.warn("Inconsistent method redirect entry: {}", record.toString());
                    continue;
                }

                if (!record.isMapped(SOURCE_CLASS) || !record.isMapped(SOURCE_METHOD) ||
                    !record.isMapped(SOURCE_DESCRIPTOR) || !record.isMapped(TARGET_CLASS) ||
                    !record.isMapped(TARGET_METHOD) || !record.isMapped(TARGET_DESCRIPTOR)) {
                    LOGGER.warn("Method redirect entry missing required fields: {}", record.toString());
                    continue;
                }

                final String sourceClass = record.get(SOURCE_CLASS);
                final String sourceMethod = record.get(SOURCE_METHOD);
                final String sourceDescriptor = record.get(SOURCE_DESCRIPTOR);
                final String targetClass = record.get(TARGET_CLASS);
                final String targetMethod = record.get(TARGET_METHOD);
                final String targetDescriptor = record.get(TARGET_DESCRIPTOR);

                final Map<String, MethodRedirect> classMethods = redirects.computeIfAbsent(sourceClass,
                                                          k -> new Object2ObjectOpenHashMap<>());
                final String methodKey = sourceMethod + sourceDescriptor;
                final MethodRedirect methodTarget = new MethodRedirect(targetClass, targetMethod, targetDescriptor);

                classMethods.put(methodKey, methodTarget);
                if(AngelicaRfbPlugin.VERBOSE) {
                    LOGGER.debug("Added method redirect: {}.{}{} → {}.{}{}",
                        sourceClass, sourceMethod, sourceDescriptor, targetClass, targetMethod, targetDescriptor);
                }
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.error("Failed to load method redirects from {}", METHOD_REDIRECTS_FILE, e);
        }

        LOGGER.info("Loaded {} class method redirects", redirects.size());
        return redirects;
    }

    /**
     * Enhanced GL capability redirects loader from glcap_redirects.csv.
     * Format: lwjgl_class,enum_name,gl_constant_hex,gl_constant_int,method_name
     *
     * @return Map of (GL constant integer -> method name string)
     */
    public static Int2ObjectOpenHashMap<String> loadGlCapRedirects() {
        final Int2ObjectOpenHashMap<String> redirects = new Int2ObjectOpenHashMap<>();

        try (InputStream is = RedirectConfigLoader.class.getClassLoader().getResourceAsStream(GLCAP_REDIRECTS_FILE);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    LOGGER.warn("Inconsistent GL capability redirect entry: {}", record.toString());
                    continue;
                }

                if (!record.isMapped(LWJGL_CLASS) || !record.isMapped(ENUM_NAME) ||
                    !record.isMapped(GL_CONSTANT_HEX) || !record.isMapped(GL_CONSTANT_INT) ||
                    !record.isMapped(METHOD_NAME)) {
                    LOGGER.warn("GL capability redirect entry missing required fields: {}", record.toString());
                    continue;
                }

                final String lwjglClass = record.get(LWJGL_CLASS);
                final String enumName = record.get(ENUM_NAME);
                final String constantHex = record.get(GL_CONSTANT_HEX);
                final String constantInt = record.get(GL_CONSTANT_INT);
                final String methodName = record.get(METHOD_NAME);

                try {
                    final int glConstant = Integer.parseInt(constantInt);

                    redirects.put(glConstant, methodName);
                    if(AngelicaRfbPlugin.VERBOSE) {
                        LOGGER.debug("Added GL capability redirect: {} ({}, {}, {}) → {}", enumName, lwjglClass, constantHex, glConstant, methodName);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid GL constant '{}' in redirect entry: {}", constantInt, record.toString());
                }
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.error("Failed to load GL capability redirects from {}", GLCAP_REDIRECTS_FILE, e);
        }

        LOGGER.info("Loaded {} GL capability redirects", redirects.size());
        return redirects;
    }
}
