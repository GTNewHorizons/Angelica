package com.gtnewhorizons.angelica.client.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfig;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityLightConfigStorage;
import com.gtnewhorizons.angelica.dynamiclights.config.EntityTypeEntry;
import jss.notfine.core.Settings;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DynamicLightsOptionPages {

    public static OptionPage dynamicLights() {
        if (!DynamicLights.configEnabled) {
            return null;
        }

        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
            .add(Settings.DYNAMIC_LIGHTS.option)
            .add(Settings.DYNAMIC_LIGHTS_SHADER_FORCE.option)
            .build());

        groups.add(OptionGroup.createBuilder()
            .add(Settings.DYNAMIC_LIGHTS_FRUSTUM_CULLING.option)
            .add(Settings.DYNAMIC_LIGHTS_ADAPTIVE_TICKING.option)
            .add(Settings.DYNAMIC_LIGHTS_CULL_TIMEOUT.option)
            .build());

        groups.add(OptionGroup.createBuilder()
            .add(Settings.DYNAMIC_LIGHTS_SLOW_DIST.option)
            .add(Settings.DYNAMIC_LIGHTS_SLOWER_DIST.option)
            .add(Settings.DYNAMIC_LIGHTS_BACKGROUND_DIST.option)
            .build());

        // Entity type toggles - grouped by mod ID
        List<EntityTypeEntry> allEntities = EntityLightConfig.getAllEntityTypes();
        Map<String, List<EntityTypeEntry>> byMod = new LinkedHashMap<>();

        for (EntityTypeEntry entry : allEntities) {
            byMod.computeIfAbsent(entry.getModId(), _ -> new ArrayList<>()).add(entry);
        }

        // Create a group for each mod's entities
        for (Map.Entry<String, List<EntityTypeEntry>> modEntry : byMod.entrySet()) {
            String modId = modEntry.getKey();
            List<EntityTypeEntry> entities = modEntry.getValue();

            OptionGroup.Builder groupBuilder = OptionGroup.createBuilder();

            for (EntityTypeEntry entity : entities) {
                Option<Boolean> option = createEntityOption(entity);
                if (option != null) {
                    groupBuilder.add(option);
                }
            }

            // Only add group if it has options
            OptionGroup group = groupBuilder.build();
            if (!group.getOptions().isEmpty()) {
                groups.add(group);
            }
        }

        return new OptionPage(I18n.format("options.dynamiclights.page"), ImmutableList.copyOf(groups));
    }

    private static Option<Boolean> createEntityOption(EntityTypeEntry entry) {
        final String className = entry.getClassName();

        // Resolve class once during option creation, not on every get/set
        Class<?> resolvedClass;
        try {
            resolvedClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // Class not loaded, skip this option
            return null;
        }

        final Class<?> entityClass = resolvedClass;

        return OptionImpl.createBuilder(Boolean.class, EntityLightConfigStorage.INSTANCE)
            .setName(entry.getDisplayName() + " (" + entry.getModId() + ")")
            .setTooltip(I18n.format("options.dynamiclights.entity.tooltip", entry.getDisplayName()))
            .setControl(TickBoxControl::new)
            .setBinding(
                (_, value) -> EntityLightConfig.setEntityTypeEnabled(entityClass, value),
                storage -> EntityLightConfig.isEntityTypeEnabled(entityClass)
            )
            .setImpact(OptionImpact.LOW)
            .build();
    }
}
