package com.gtnewhorizons.angelica.helpers;

import com.google.common.collect.ListMultimap;
import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.VersionRange;

import java.io.File;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("UnstableApiUsage")
public class LoadControllerHelper {
    private static LoadController loadController;
    private static ListMultimap<String, ModContainer> packageOwners;

    static {
        loadController = ObfuscationReflectionHelper.getPrivateValue(Loader.class, Loader.instance(), "modController");
        packageOwners = ObfuscationReflectionHelper.getPrivateValue(LoadController.class, loadController, "packageOwners");
    }

    private static ConcurrentHashMap<Class<?>, ModContainer> owningModForClass = new ConcurrentHashMap<>();

    public static ModContainer getOwningMod(Class<?> clz) {
        ModContainer container = owningModForClass.computeIfAbsent(clz, c -> {
            if(clz.getName().startsWith("net.minecraft."))
                return Loader.instance().getMinecraftModContainer();
            int lastDot = clz.getName().lastIndexOf('.');
            if(lastDot == -1)
                return NONE;
            String pkgName = clz.getName().substring(0, lastDot);
            if(packageOwners.containsKey(pkgName))
                return packageOwners.get(pkgName).get(0);
            else
                return NONE;
        });
        if(container == NONE)
            return null;
        return container;
    }

    private static final ModContainer NONE = new ModContainer() {
        @Override
        public String getModId() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getVersion() {
            return null;
        }

        @Override
        public File getSource() {
            return null;
        }

        @Override
        public ModMetadata getMetadata() {
            return null;
        }

        @Override
        public void bindMetadata(MetadataCollection mc) {

        }

        @Override
        public void setEnabledState(boolean enabled) {

        }

        @Override
        public Set<ArtifactVersion> getRequirements() {
            return null;
        }

        @Override
        public List<ArtifactVersion> getDependencies() {
            return null;
        }

        @Override
        public List<ArtifactVersion> getDependants() {
            return null;
        }

        @Override
        public String getSortingRules() {
            return null;
        }

        @Override
        public boolean registerBus(EventBus bus, LoadController controller) {
            return false;
        }

        @Override
        public boolean matches(Object mod) {
            return false;
        }

        @Override
        public Object getMod() {
            return null;
        }

        @Override
        public ArtifactVersion getProcessedVersion() {
            return null;
        }

        @Override
        public boolean isImmutable() {
            return false;
        }

        @Override
        public String getDisplayVersion() {
            return null;
        }

        @Override
        public VersionRange acceptableMinecraftVersionRange() {
            return null;
        }

        @Override
        public Certificate getSigningCertificate() {
            return null;
        }

        @Override
        public Map<String, String> getCustomModProperties() {
            return null;
        }

        @Override
        public Class<?> getCustomResourcePackClass() {
            return null;
        }

        @Override
        public Map<String, String> getSharedModDescriptor() {
            return null;
        }

        @Override
        public Disableable canBeDisabled() {
            return null;
        }

        @Override
        public String getGuiClassName() {
            return null;
        }

        @Override
        public List<String> getOwnedPackages() {
            return null;
        }
    };

}
