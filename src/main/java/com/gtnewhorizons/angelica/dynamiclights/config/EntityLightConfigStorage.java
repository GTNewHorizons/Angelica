package com.gtnewhorizons.angelica.dynamiclights.config;

import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

public class EntityLightConfigStorage implements OptionStorage<EntityLightConfigStorage> {

    public static final EntityLightConfigStorage INSTANCE = new EntityLightConfigStorage();

    private EntityLightConfigStorage() {}

    @Override
    public EntityLightConfigStorage getData() {
        return this;
    }

    @Override
    public void save() {
        EntityLightConfig.save();
    }
}
