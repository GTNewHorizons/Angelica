package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import mist475.mcpatcherforge.config.MCPatcherForgeConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum Mixins {
    ANGELICA_STARTUP(new Builder("Angelica Startup").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.startup.MixinInitGLStateManager"
            ,"angelica.startup.MixinSplashProgress"
        )
    ),

    ANGELICA(new Builder("Angelica").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.MixinActiveRenderInfo"
            ,"angelica.MixinEntityRenderer"
            ,"angelica.MixinMinecraft"
            ,"angelica.optimizations.MixinRendererLivingEntity"
        )
    ),
    ANGELICA_VBO(
        new Builder("Angelica VBO").addTargetedMod(TargetedMod.VANILLA).setApplyIf(() -> AngelicaConfig.enableVBO).setSide(Side.CLIENT)
            .setPhase(Phase.EARLY).addMixinClasses(
                 "angelica.vbo.MixinGLAllocation"
                ,"angelica.vbo.MixinModelRenderer"
                ,"angelica.vbo.MixinRenderGlobal"
                ,"angelica.vbo.MixinWavefrontObject"
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
             "angelica.debug.MixinProfiler"
            ,"angelica.debug.MixinSplashProgress"
            ,"angelica.debug.MixinTextureManager"
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
            ,"shaders.startup.MixinTextureMap"
        )
    ),

    SODIUM(new Builder("Sodium").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableSodium).addMixinClasses(
             "sodium.MixinChunkProviderClient"
            ,"sodium.MixinBlock"
            ,"sodium.AccessorBiomeColorEvent"
            ,"sodium.MixinBiomeGenBase"
            ,"sodium.MixinChunk"
            ,"sodium.MixinChunkProviderServer"
            ,"sodium.MixinClientRegistry"
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
            ,"sodium.MixinTileEntity"
            ,"sodium.MixinGuiIngameForge"
            ,"sodium.MixinEffectRenderer"
            ,"sodium.MixinTileEntityRendererDispatcher"
            ,"sodium.MixinLongHashMap"
            ,"sodium.MixinRender"
            ,"sodium.MixinRenderingRegistry"
        )
    ),

    SODIUM_DYN_SURROUND(new Builder("Sodium without Dynamic Surroundings").addTargetedMod(TargetedMod.VANILLA)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_MIST).addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_ORIGINAL).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableSodium).addMixinClasses(
            "sodium.MixinEntityRenderer$WeatherQuality"
        )
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
        	"angelica.hudcaching.GuiIngameAccessor",
        	"angelica.hudcaching.GuiIngameForgeAccessor",
            "angelica.hudcaching.MixinEntityRenderer_HUDCaching",
            "angelica.hudcaching.MixinFramebuffer_HUDCaching",
            "angelica.hudcaching.MixinGuiIngame_HUDCaching",
            "angelica.hudcaching.MixinGuiIngameForge_HUDCaching",
            "angelica.hudcaching.MixinRenderItem")

    ),

    OPTIMIZE_WORLD_UPDATE_LIGHT(new Builder("Optimize world updateLightByType method").setPhase(Phase.EARLY)
        .setSide(Side.BOTH).addTargetedMod(TargetedMod.VANILLA).addExcludedMod(TargetedMod.ARCHAICFIX).setApplyIf(() -> AngelicaConfig.optimizeWorldUpdateLight)
        .addMixinClasses("angelica.lighting.MixinWorld_FixLightUpdateLag")),


    SPEEDUP_VANILLA_ANIMATIONS(new Builder("Speedup Vanilla Animations").setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations).setSide(Side.CLIENT).addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
            "angelica.animation.MixinTextureAtlasSprite",
            "angelica.animation.MixinTextureMap",
            "angelica.animation.MixinBlockFire",
            "angelica.animation.MixinMinecraftForgeClient",
            "angelica.animation.MixinChunkCache",
            "angelica.animation.MixinRenderBlocks",
            "angelica.animation.MixinRenderBlockFluid",
            "angelica.animation.MixinWorldRenderer",
            "angelica.animation.MixinRenderItem")),

    SPEEDUP_CAMPFIRE_BACKPORT_ANIMATIONS(new Builder("Add animation speedup support to Campfire Backport").setPhase(Phase.LATE)
            .addTargetedMod(TargetedMod.CAMPFIRE_BACKPORT).setSide(Side.CLIENT)
            .setApplyIf(() -> AngelicaConfig.speedupAnimations)
            .addMixinClasses("client.campfirebackport.MixinRenderBlockCampfire")),

    IC2_FLUID_RENDER_FIX(new Builder("IC2 Fluid Render Fix").setPhase(Phase.EARLY).setSide(Side.CLIENT)
        .addTargetedMod(TargetedMod.IC2).setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addMixinClasses("angelica.textures.ic2.MixinRenderLiquidCell")),

    OPTIMIZE_TEXTURE_LOADING(new Builder("Optimize Texture Loading").setPhase(Phase.EARLY)
        .addMixinClasses("angelica.textures.MixinTextureUtil_OptimizeMipmap").addTargetedMod(TargetedMod.VANILLA)
        .setApplyIf(() -> AngelicaConfig.optimizeTextureLoading).setSide(Side.CLIENT)),

    NOTFINE_OPTIMIZATION(new Builder("NotFine Optimizations").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineOptimizations).addMixinClasses(
             "notfine.faceculling.MixinBlock"
            ,"notfine.faceculling.MixinBlockSlab"
            ,"notfine.faceculling.MixinBlockSnow"
            ,"notfine.faceculling.MixinBlockStairs"
        )),

    NOTFINE_FEATURES(new Builder("NotFine Features").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures).addMixinClasses(
             //"notfine.clouds.MixinEntityRenderer"
            //,"notfine.clouds.MixinGameSettings"
            //,"notfine.clouds.MixinRenderGlobal"
            //,"notfine.clouds.MixinWorldType"
            "notfine.glint.MixinRenderBiped"
            ,"notfine.glint.MixinRenderItem"
            ,"notfine.glint.MixinRenderPlayer"
            ,"notfine.gui.MixinGuiSlot"
            ,"notfine.leaves.MixinBlockLeaves"
            ,"notfine.leaves.MixinBlockLeavesBase"
            //,"notfine.particles.MixinBlockEnchantmentTable"
            //,"notfine.particles.MixinEffectRenderer"
            //,"notfine.particles.MixinWorldClient"
            //,"notfine.particles.MixinWorldProvider"
            ,"notfine.renderer.MixinRenderGlobal"
            ,"notfine.settings.MixinGameSettings"
            //,"notfine.toggle.MixinGuiIngame"
            //,"notfine.toggle.MixinEntityRenderer"
            //,"notfine.toggle.MixinRender" --> No Sodium below
            //,"notfine.toggle.MixinRenderItem"
        )),

    NOTFINE_FEATURES_NO_SODIUM(new Builder("NotFine Features").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures && !AngelicaConfig.enableSodium).addMixinClasses(
            "notfine.toggle.MixinRender"
        )),

    NOTFINE_FEATURES_NO_MCPF_CIT(new Builder("NotFine Features which clash with mcpf cit (compat handled in MCPF_NF mixin)").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures &&
            !(AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().customItemTexturesEnabled)
        ).addMixinClasses(
            "notfine.glint.MixinItemRenderer",
            "notfine.glint.MixinRenderItemGlint"
        )),

    NOTFINE_LATE_TWILIGHT_FOREST_LEAVES(new Builder("NotFine Mod Leaves").addTargetedMod(TargetedMod.TWILIGHT_FOREST).setSide(Side.CLIENT)
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
        )),

    MCPATCHERFORGE_NOTFINE_COMPAT(new Builder("Notfine features and MCPF features where the 2 would clash").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableNotFineFeatures &&
            AngelicaConfig.enableMCPatcherForgeFeatures &&
            MCPatcherForgeConfig.instance().customItemTexturesEnabled)
        .addMixinClasses(addPrefix(
            "notfine_mcpf_compat.",
            "MixinRenderItem",
            "MixinItemRenderer"))),

    MCPATCHERFORGE_BASE_MOD(new Builder("Base MCPatcher mixins").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix(
            "mcpatcherforge.",
            "base.MixinBlockGrass",
                "base.MixinBlockMycelium",

                "base.MixinAbstractTexture",
                "base.MixinTextureAtlasSprite",

                "base.MixinSimpleReloadableResourceManager",

                "base.MixinMinecraft"

                // TODO merge renderpass changes into Sodium renderer
                // "renderpass.MixinEntityRenderer",
                // "renderpass.MixinRenderBlocks",
                // "renderpass.MixinRenderGlobal",
                // "renderpass.MixinWorldRenderer"

                         )
        )),

    MCPATCHERFORGE_CUSTOM_COLOURS(new Builder("Custom colors").setSide(Mixins.Side.CLIENT)
        .setPhase(Mixins.Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().customColorsEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
        addPrefix(
        "mcpatcherforge.cc.",
             "block.material.MixinMapColor",

            "block.MixinBlock",
            "block.MixinBlockDoublePlant",
            "block.MixinBlockGrass",
            "block.MixinBlockLeaves",
            "block.MixinBlockLilyPad",
            "block.MixinBlockLiquid",
            "block.MixinBlockOldLeaf",
            "block.MixinBlockRedstoneWire",
            "block.MixinBlockReed",
            "block.MixinBlockStem",
            "block.MixinBlockTallGrass",
            "block.MixinBlockVine",

            "client.particle.MixinEntityAuraFX",
            "client.particle.MixinEntityBubbleFX",
            "client.particle.MixinEntityDropParticleFX",
            "client.particle.MixinEntityPortalFX",
            "client.particle.MixinEntityRainFX",
            "client.particle.MixinEntityRedDustFX",
            "client.particle.MixinEntitySplashFX",
            "client.particle.MixinEntitySuspendFX",

            "client.renderer.entity.MixinRenderWolf",
            "client.renderer.entity.MixinRenderXPOrb",

            "client.renderer.tileentity.MixinTileEntitySignRenderer",

            "client.renderer.MixinEntityRenderer",
            "client.renderer.MixinItemRenderer",
            "client.renderer.MixinRenderBlocks",
            "client.renderer.MixinRenderGlobal",

            "entity.MixinEntityList",

            "item.crafting.MixinRecipesArmorDyes",

            "item.MixinItemArmor",
            "item.MixinItemBlock",
            "item.MixinItemMonsterPlacer",

            "potion.MixinPotion",
            "potion.MixinPotionHelper",

            "world.MixinWorld",
            "world.MixinWorldProvider",
            "world.MixinWorldProviderEnd",
            "world.MixinWorldProviderHell"))),

    MCPATCHERFORGE_CUSTOM_ITEM_TEXTURES(new Mixins.Builder("Custom Item Textures").setSide(Mixins.Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().customItemTexturesEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
        addPrefix(
        "mcpatcherforge.cit.",
            "client.renderer.entity.MixinRenderBiped",
            "client.renderer.entity.MixinRenderEntityLiving",
            "client.renderer.entity.MixinRenderItem",
            "client.renderer.entity.MixinRenderPlayer",
            "client.renderer.entity.MixinRenderSnowball",
            "client.renderer.MixinItemRenderer",
            "item.MixinItem",
            "nbt.MixinNBTTagCompound",
            "nbt.MixinNBTTagList"))),

    MCPATCHERFORGE_CUSTOM_ITEM_TEXTURES_NO_NF(new Mixins.Builder("Custom Item Textures without notfine features").setSide(Mixins.Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> (AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().customItemTexturesEnabled) && !AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix(
             "mcpatcherforge.cit.client.renderer.",
            "entity.MixinRenderItemRenderDroppedItem",
            "MixinItemRenderer_NO_NF"
                ))),

    MCPATCHERFORGE_CONNECTED_TEXTURES(new Builder("Connected Textures").setSide(Side.CLIENT)
        .setPhase(Mixins.Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().connectedTexturesEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm.MixinRenderBlocks")),

    MCPATCHERFORGE_EXTENDED_HD(new Builder("Extended hd").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().extendedHDEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
        addPrefix("mcpatcherforge.hd.", "MixinFontRenderer", "MixinTextureClock", "MixinTextureCompass", "MixinTextureManager"))),

    MCPATCHERFORGE_RANDOM_MOBS(new Builder("Random Mobs").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().randomMobsEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
        addPrefix(
        "mcpatcherforge.mob.",
            "MixinRender",
            "MixinRenderEnderman",
            "MixinRenderFish",
            "MixinRenderLiving",
            "MixinRenderMooshroom",
            "MixinRenderSheep",
            "MixinRenderSnowMan",
            "MixinRenderSpider",
            "MixinRenderWolf",
            "MixinEntityLivingBase"))),

    MCPATCHERFORGE_SKY(new Builder("Sky").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.instance().betterSkiesEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.sky.", "MixinEffectRenderer", "MixinRenderGlobal"
        ))),

    MCPATCHERFORGE_CC_NO_CTM(new Builder("Custom colors, no connected textures").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(
            () -> AngelicaConfig.enableMCPatcherForgeFeatures
                && (!MCPatcherForgeConfig.instance().connectedTexturesEnabled
                && MCPatcherForgeConfig.instance().customColorsEnabled))
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCTM")),

    MCPATCHERFORGE_CTM_AND_CC(new Builder("Connected textures and Custom Colors enabled").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(
            () -> AngelicaConfig.enableMCPatcherForgeFeatures
                && MCPatcherForgeConfig.instance().connectedTexturesEnabled
                && MCPatcherForgeConfig.instance().customColorsEnabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinRenderBlocks")),

    MCPATCHERFORGE_CTM_NO_CC(new Builder("Connected textures, no custom colours").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(
            () -> AngelicaConfig.enableMCPatcherForgeFeatures
                && (MCPatcherForgeConfig.instance().connectedTexturesEnabled
                && !MCPatcherForgeConfig.instance().customColorsEnabled))
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC")),

    MCPATCHERFORGE_CTM_OR_CC(new Builder("Connected textures or Custom Colors enabled").setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(
            () -> AngelicaConfig.enableMCPatcherForgeFeatures
                && (MCPatcherForgeConfig.instance().connectedTexturesEnabled
                || MCPatcherForgeConfig.instance().customColorsEnabled))
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinTextureMap")),

    NOVIS_OCULIS(new Builder("Non-Tessellator Quad provider")
        .setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.injectQPRendering)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
            "angelica.models.MixinBlockStone",
            "angelica.models.MixinBlockAir",
            "angelica.models.MixinBlockWorkbench",
            "angelica.models.MixinBlockLeaves")),

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

    @SuppressWarnings("SimplifyStreamApiCallChains")
    private static String[] addPrefix(String prefix, String... values) {
        return Arrays.stream(values)
            .map(s -> prefix + s)
            .collect(Collectors.toList())
            .toArray(new String[values.length]);
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
