package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import me.jellysquid.mods.sodium.common.config.SodiumConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public enum Mixins {
    ANGELICA_STARTUP(new Builder("Angelica Startup").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.startup.MixinInitGLStateManager"
            ,"angelica.startup.MixinSplashProgress"
        )
    ),

    ANGELICA(new Builder("Angelica").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.MixinEntityRenderer"

        )
    ),

    ANGELICA_FONT_RENDERER(new Builder("Angelica Font Renderer").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableFontRenderer).addMixinClasses(
             "angelica.fontrenderer.MixinGuiIngameForge"
            ,"angelica.fontrenderer.MixinFontRenderer"
        )
    ),

    ANGELICA_ENABLE_DEBUG(new Builder("Angelica Debug").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaMod.lwjglDebug).addMixinClasses(
            "angelica.debug.MixinSplashProgress"
        )
    ),
    // Not compatible with the lwjgl debug callbacks, so disable if that's enabled
    ARCHAIC_SPLASH(new Builder("ArchaicFix Splash").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.showSplashMemoryBar && !AngelicaMod.lwjglDebug).addMixinClasses(
              "angelica.archaic.MixinSplashProgress"
             ,"angelica.archaic.AccessorSplashProgress"
        )
    ),

    ARCHAIC_CORE(new Builder("Archaic Core").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.archaic.MixinBlockFence"
            ,"angelica.archaic.MixinFMLClientHandler"
            ,"angelica.archaic.MixinGuiIngameForge"
            ,"angelica.archaic.MixinNetHandlerPlayClient"
            ,"angelica.archaic.MixinThreadDownloadImageData"
        )
    ),

    IRIS_STARTUP(new Builder("Start Iris").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris).addMixinClasses(
             "shaders.startup.MixinGameSettings"
            ,"shaders.startup.MixinGuiMainMenu"
            ,"shaders.startup.MixinInitRenderer"
            ,"shaders.startup.MixinAbstractTexture"
            ,"shaders.startup.MixinTextureAtlasSprite"
        )
    ),

    SODIUM_STARTUP(new Builder("Start Sodium").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableSodium && !AngelicaConfig.enableIris).addMixinClasses(
            "sodium.startup.MixinInitDebug"
        )
    ),

    SODIUM(new Builder("Sodium").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableSodium).addMixinClasses(
            "sodium.MixinChunkProviderClient"
            ,"sodium.MixinBlock"
            ,"sodium.MixinChunk"
            ,"sodium.MixinChunkProviderServer"
            ,"sodium.MixinEntity"
            ,"sodium.MixinRenderManager"
            ,"sodium.MixinExtendedBlockStorage"
            ,"sodium.MixinEntityRenderer"
            ,"sodium.MixinFMLClientHandler"
            ,"sodium.MixinGameSettings"
            ,"sodium.MixinFrustrum"
            ,"sodium.MixinMaterial"
            ,"sodium.MixinMinecraft"
            ,"sodium.MixinNibbleArray"
            ,"sodium.MixinRenderBlocks"
            ,"sodium.MixinRenderGlobal"
            ,"sodium.MixinWorldClient"
            ,"sodium.MixinTessellator"
            ,"sodium.MixinGuiIngameForge"
            ,"sodium.MixinEffectRenderer"
            ,"sodium.MixinRenderingRegistry"
            // TODO Doesn't work correctly
            //,"sodium.MixinTextureAtlasSprite"
            //,"sodium.MixinTextureMap"
            //,"sodium.MixinEntityFX"
            ,"sodium.MixinLongHashMap"
            ,"sodium.MixinRender"
        )
    ),

    SODIUM_JABBA_COMPAT(new Builder("Sodium Jabba Compat").addTargetedMod(TargetedMod.JABBA).setSide(Side.CLIENT)
        .setPhase(Phase.LATE).setApplyIf(() -> AngelicaConfig.enableSodium)
        .addMixinClasses("compat.MixinJabba")
    ),

    // Required for Sodium's FluidRenderer, so it treats vanilla liquids as IFluidBlocks
    SODIUM_WISHLIST(new Builder("Sodiumer").addTargetedMod(TargetedMod.VANILLA).setSide(Side.BOTH)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableSodiumFluidRendering).addMixinClasses(
        "sodium.MixinBlockLiquid")),

    IRIS_RENDERING(new Builder("Iris Shaders").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris).addMixinClasses(
             "shaders.MixinEntityRenderer"
            ,"shaders.MixinFramebuffer"
            ,"shaders.MixinItem"
            ,"shaders.MixinLocale"
            ,"shaders.MixinOpenGlHelper"
            ,"shaders.MixinRender"
            ,"shaders.MixinRenderGlobal"
        )
    ),

    IRIS_ACCESSORS(new Builder("Iris Accessors").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris).addMixinClasses(
             "shaders.accessors.MinecraftAccessor"
            ,"shaders.accessors.EntityRendererAccessor"
            ,"shaders.accessors.SimpleTextureAccessor"
            ,"shaders.accessors.TextureAtlasSpriteAccessor"
            ,"shaders.accessors.TextureMapAccessor"
            ,"shaders.accessors.AnimationMetadataSectionAccessor"
        )
    ),


    ANGELICA_TEXTURE(new Builder("Textures").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris || AngelicaConfig.enableSodium).addMixinClasses(
             "angelica.textures.MixinTextureAtlasSprite"
            ,"angelica.textures.MixinTextureUtil"
        )),

    HUD_CACHING(new Builder("Renders the HUD elements 20 times per second maximum to improve performance")
        .addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableHudCaching).addMixinClasses(
            "angelica.hudcaching.GuiIngameForgeAccessor",
            "angelica.hudcaching.MixinEntityRenderer_HUDCaching",
            "angelica.hudcaching.MixinFramebuffer_HUDCaching",
            "angelica.hudcaching.MixinGuiIngame_HUDCaching",
            "angelica.hudcaching.MixinGuiIngameForge_HUDCaching",
            "angelica.hudcaching.MixinRenderItem")

    ),

    // TODO: Iris
//    SHADERSMOD_COMPAT_PR_ILLUMINATION(
//            new Builder("ProjectRed Illumination compat").addTargetedMod(TargetedMod.PROJECTRED_ILLUMINATION)
//                    .setSide(Side.CLIENT).addMixinClasses("compat.MixinRenderHalo")),
//
//    SHADERSMOD_COMPAT_SMART_RENDER(new Builder("Smart Render compat").addTargetedMod(TargetedMod.SMART_RENDER).setSide(Side.CLIENT)
//            .addMixinClasses("compat.MixinModelRotationRenderer"))

    NOTFINE_OPTIMIZATION(new Builder("NotFine Optimizations").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineOptimizations).addMixinClasses(
            "notfine.faceculling.MixinBlock"
            ,"notfine.faceculling.MixinBlockSlab"
            ,"notfine.faceculling.MixinBlockSnow"
            ,"notfine.faceculling.MixinBlockStairs"
        )),

    NOTFINE_FEATURES(new Builder("NotFine Features").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures).addMixinClasses(
            "notfine.glint.MixinItemRenderer"
            ,"notfine.glint.MixinRenderBiped"
            ,"notfine.glint.MixinRenderItem"
            ,"notfine.glint.MixinRenderPlayer"
            ,"notfine.gui.MixinGuiSlot"
            ,"notfine.renderer.MixinRenderGlobal"
            ,"notfine.settings.MixinGameSettings"
        )),
    NOTFINE_LATE_TWILIGHT_FORESTLEAVES(new Builder("NotFine Mod Leaves").addTargetedMod(TargetedMod.TWILIGHT_FOREST).setSide(Side.CLIENT)
        .setPhase(Phase.LATE).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures).addMixinClasses(
             "notfine.leaves.twilightforest.MixinBlockTFLeaves"
            ,"notfine.leaves.twilightforest.MixinBlockTFLeaves3"
            // TODO: Verify 2.3.8.18 or later to support non NH builds?
            ,"notfine.leaves.twilightforest.MixinBlockTFMagicLeaves"
        )),
    NOTFINE_LATE_THAUMCRAFT_LEAVES(new Builder("NotFine Mod Leaves").addTargetedMod(TargetedMod.THAUMCRAFT).setSide(Side.CLIENT)
        .setPhase(Phase.LATE).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures).addMixinClasses(
             "notfine.leaves.thaumcraft.MixinBlockMagicalLeaves"
        )),
    NOTFINE_LATE_WITCHERY_LEAVES(new Builder("NotFine Mod Leaves").addTargetedMod(TargetedMod.WITCHERY).setSide(Side.CLIENT)
        .setPhase(Phase.LATE).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures).addMixinClasses(
             "notfine.leaves.witchery.MixinBlockWitchLeaves"
        ))
    ;

    private final List<String> mixinClasses;
    private final Supplier<Boolean> applyIf;
    private final Phase phase;
    private final Side side;
    private final List<TargetedMod> targetedMods;
    private final List<TargetedMod> excludedMods;

    Mixins(Builder builder) {
        this.mixinClasses = builder.mixinClasses;
        this.applyIf = builder.applyIf;
        this.side = builder.side;
        this.targetedMods = builder.targetedMods;
        this.excludedMods = builder.excludedMods;
        this.phase = builder.phase;
        if (this.targetedMods.isEmpty()) {
            throw new RuntimeException("No targeted mods specified for " + this.name());
        }
        if (this.applyIf == null) {
            throw new RuntimeException("No ApplyIf function specified for " + this.name());
        }
    }

    public static List<String> getEarlyMixins(Set<String> loadedCoreMods) {
        final List<String> mixins = new ArrayList<>();
        final List<String> notLoading = new ArrayList<>();
        for (Mixins mixin : Mixins.values()) {
            if (mixin.phase == Mixins.Phase.EARLY) {
                if (mixin.shouldLoad(loadedCoreMods, Collections.emptySet())) {
                    mixins.addAll(mixin.mixinClasses);
                } else {
                    notLoading.addAll(mixin.mixinClasses);
                }
            }
        }
        AngelicaTweaker.LOGGER.info("Not loading the following EARLY mixins: {}", notLoading);
        return mixins;
    }

    public static List<String> getLateMixins(Set<String> loadedMods) {
        final List<String> mixins = new ArrayList<>();
        final List<String> notLoading = new ArrayList<>();
        for (Mixins mixin : Mixins.values()) {
            if (mixin.phase == Mixins.Phase.LATE) {
                if (mixin.shouldLoad(Collections.emptySet(), loadedMods)) {
                    mixins.addAll(mixin.mixinClasses);
                } else {
                    notLoading.addAll(mixin.mixinClasses);
                }
            }
        }
        AngelicaTweaker.LOGGER.info("Not loading the following LATE mixins: {}", notLoading.toString());
        return mixins;
    }

    private boolean shouldLoadSide() {
        return side == Side.BOTH || (side == Side.SERVER && FMLLaunchHandler.side().isServer())
                || (side == Side.CLIENT && FMLLaunchHandler.side().isClient());
    }

    private boolean allModsLoaded(List<TargetedMod> targetedMods, Set<String> loadedCoreMods, Set<String> loadedMods) {
        if (targetedMods.isEmpty()) return false;

        for (TargetedMod target : targetedMods) {
            if (target == TargetedMod.VANILLA) continue;

            // Check coremod first
            if (!loadedCoreMods.isEmpty() && target.coreModClass != null
                    && !loadedCoreMods.contains(target.coreModClass))
                return false;
            else if (!loadedMods.isEmpty() && target.modId != null && !loadedMods.contains(target.modId)) return false;
        }

        return true;
    }

    private boolean noModsLoaded(List<TargetedMod> targetedMods, Set<String> loadedCoreMods, Set<String> loadedMods) {
        if (targetedMods.isEmpty()) return true;

        for (TargetedMod target : targetedMods) {
            if (target == TargetedMod.VANILLA) continue;

            // Check coremod first
            if (!loadedCoreMods.isEmpty() && target.coreModClass != null
                    && loadedCoreMods.contains(target.coreModClass))
                return false;
            else if (!loadedMods.isEmpty() && target.modId != null && loadedMods.contains(target.modId)) return false;
        }

        return true;
    }

    private boolean shouldLoad(Set<String> loadedCoreMods, Set<String> loadedMods) {
        return (shouldLoadSide() && applyIf.get()
                && allModsLoaded(targetedMods, loadedCoreMods, loadedMods)
                && noModsLoaded(excludedMods, loadedCoreMods, loadedMods));
    }

    private static class Builder {

        private final List<String> mixinClasses = new ArrayList<>();
        private Supplier<Boolean> applyIf = () -> true;
        private Side side = Side.BOTH;
        private Phase phase = Phase.LATE;
        private final List<TargetedMod> targetedMods = new ArrayList<>();
        private final List<TargetedMod> excludedMods = new ArrayList<>();

        public Builder(@SuppressWarnings("unused") String description) {}

        public Builder addMixinClasses(String... mixinClasses) {
            this.mixinClasses.addAll(Arrays.asList(mixinClasses));
            return this;
        }

        public Builder setPhase(Phase phase) {
            this.phase = phase;
            return this;
        }

        public Builder setSide(Side side) {
            this.side = side;
            return this;
        }

        public Builder setApplyIf(Supplier<Boolean> applyIf) {
            this.applyIf = applyIf;
            return this;
        }

        public Builder addTargetedMod(TargetedMod mod) {
            this.targetedMods.add(mod);
            return this;
        }

        public Builder addExcludedMod(TargetedMod mod) {
            this.excludedMods.add(mod);
            return this;
        }
    }

    private enum Side {
        BOTH,
        CLIENT,
        SERVER
    }

    private enum Phase {
        EARLY,
        LATE,
    }
}
