package me.jellysquid.mods.sodium.common.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Documentation of these options: https://github.com/jellysquid3/sodium-fabric/wiki/Configuration-File
 */
public class SodiumConfig {
    private static final Logger LOGGER = LogManager.getLogger("EmbeddiumConfig");

    private static final String JSON_KEY_SODIUM_OPTIONS = "sodium:options";

    private static final Set<String> SYSTEM_OPTIONS = Stream.of(
            "core",
            "features.chunk_rendering"
    ).map(SodiumConfig::getMixinRuleName).collect(Collectors.toSet());

    private final Map<String, Option> options = new HashMap<>();

    private SodiumConfig() {
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("core", true); // TODO: Don't actually allow the user to disable this

        this.addMixinRule("features.block", true);
        this.addMixinRule("features.buffer_builder", true);
        this.addMixinRule("features.buffer_builder.fast_advance", true);
        this.addMixinRule("features.buffer_builder.fast_sort", true);
        this.addMixinRule("features.buffer_builder.intrinsics", true);
        this.addMixinRule("features.chunk_rendering", true);
        this.addMixinRule("features.debug", true);
        this.addMixinRule("features.entity", true);
        this.addMixinRule("features.entity.fast_render", true);
        this.addMixinRule("features.entity.smooth_lighting", true);
        this.addMixinRule("features.gui", true);
        this.addMixinRule("features.gui.fast_loading_screen", true);
        this.addMixinRule("features.gui.fast_status_bars", true);
        this.addMixinRule("features.gui.fast_fps_pie", true);
        this.addMixinRule("features.gui.font", true);
        this.addMixinRule("features.item", true);
        this.addMixinRule("features.matrix_stack", true);
        this.addMixinRule("features.model", true);
        this.addMixinRule("features.optimized_bamboo", true);
        this.addMixinRule("features.options", true);
        this.addMixinRule("features.particle", true);
        this.addMixinRule("features.particle.cull", true);
        this.addMixinRule("features.particle.fast_render", true);
        this.addMixinRule("features.render_layer", true);
        this.addMixinRule("features.render_layer.leaves", true);
        this.addMixinRule("features.sky", true);
        this.addMixinRule("features.texture_tracking", true);
        this.addMixinRule("features.world_ticking", true);
        this.addMixinRule("features.fast_biome_colors", true);

    }

    /**
     * Defines a Mixin rule which can be configured by users and other mods.
     * @throws IllegalStateException If a rule with that name already exists
     * @param mixin The name of the mixin package which will be controlled by this rule
     * @param enabled True if the rule will be enabled by default, otherwise false
     */
    private void addMixinRule(String mixin, boolean enabled) {
        String name = getMixinRuleName(mixin);

        if (this.options.putIfAbsent(name, new Option(name, enabled, false)) != null) {
            throw new IllegalStateException("Mixin rule already defined: " + mixin);
        }
    }

    private void readProperties(Properties props) {
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            Option option = this.options.get(key);

            if (option == null) {
                LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            boolean enabled;

            if (value.equalsIgnoreCase("true")) {
                enabled = true;
            } else if (value.equalsIgnoreCase("false")) {
                enabled = false;
            } else {
                LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring", value, key);
                continue;
            }

            // TODO: Sodium Config
//            if(!enabled && FMLEnvironment.production && SYSTEM_OPTIONS.contains(key)) {
//                LOGGER.warn("Configuration key '{}' is a required option and cannot be disabled", key);
//                continue;
//            }

            option.setEnabled(enabled, true);
        }
    }

    /**
     * Returns the effective option for the specified class name. This traverses the package path of the given mixin
     * and checks each root for configuration rules. If a configuration rule disables a package, all mixins located in
     * that package and its children will be disabled. The effective option is that of the highest-priority rule, either
     * a enable rule at the end of the chain or a disable rule at the earliest point in the chain.
     *
     * @return Null if no options matched the given mixin name, otherwise the effective option for this Mixin
     */
    public Option getEffectiveOptionForMixin(String mixinClassName) {
        int lastSplit = 0;
        int nextSplit;

        Option rule = null;

        while ((nextSplit = mixinClassName.indexOf('.', lastSplit)) != -1) {
            String key = getMixinRuleName(mixinClassName.substring(0, nextSplit));

            Option candidate = this.options.get(key);

            if (candidate != null) {
                rule = candidate;

                if (!rule.isEnabled()) {
                    return rule;
                }
            }

            lastSplit = nextSplit + 1;
        }

        return rule;
    }

    /**
     * Loads the configuration file from the specified location. If it does not exist, a new configuration file will be
     * created. The file on disk will then be updated to include any new options.
     */
    public static SodiumConfig load(File file) {
        if (!file.exists()) {
            try {
                writeDefaultConfig(file);
            } catch (IOException e) {
                LOGGER.warn("Could not write default configuration file", e);
            }

            return new SodiumConfig();
        }

        Properties props = new Properties();

        try (FileInputStream fin = new FileInputStream(file)){
            props.load(fin);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config file", e);
        }

        SodiumConfig config = new SodiumConfig();
        config.readProperties(props);

        return config;
    }

    private static void writeDefaultConfig(File file) throws IOException {
        File dir = file.getParentFile();

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create parent directories");
            }
        } else if (!dir.isDirectory()) {
            throw new IOException("The parent file is not a directory");
        }

        try (Writer writer = new FileWriter(file)) {
            writer.write("# This is the configuration file for Sodium.\n");
            writer.write("#\n");
            writer.write("# You can find information on editing this file and all the available options here:\n");
            writer.write("# https://github.com/jellysquid3/sodium-fabric/wiki/Configuration-File\n");
            writer.write("#\n");
            writer.write("# By default, this file will be empty except for this notice.\n");
        }
    }

    private static String getMixinRuleName(String name) {
        return "mixin." + name;
    }

    public int getOptionCount() {
        return this.options.size();
    }

    public int getOptionOverrideCount() {
        return (int) this.options.values()
                .stream()
                .filter(Option::isOverridden)
                .count();
    }
}
