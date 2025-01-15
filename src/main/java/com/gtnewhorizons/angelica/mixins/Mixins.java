package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import jss.notfine.config.MCPatcherForgeConfig;
import jss.notfine.config.NotFineConfig;

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
        )
    ),

    ANGELICA(new Builder("Angelica").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).addMixinClasses(
             "angelica.MixinActiveRenderInfo"
            ,"angelica.MixinEntityRenderer"
            ,"angelica.MixinGameSettings"
            ,"angelica.MixinMinecraft"
            ,"angelica.MixinMinecraftServer"
            ,"angelica.optimizations.MixinRendererLivingEntity"
            ,"angelica.MixinFMLClientHandler"
            ,"angelica.bugfixes.MixinRenderGlobal_DestroyBlock"
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
    ANGELICA_DYNAMIC_LIGHTS(new Builder("Angelica Dynamic Lights").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableDynamicLights).addMixinClasses(
            "angelica.dynamiclights.MixinEntityRenderer"
            ,"angelica.dynamiclights.MixinEntity"
            ,"angelica.dynamiclights.MixinWorld"
            ,"angelica.dynamiclights.MixinItemRenderer"
        )
    ),

    ANGELICA_FIX_FLUID_RENDERER_CHECKING_BLOCK_AGAIN(
        new Builder("Fix RenderBlockFluid reading the block type from the world access multiple times")
            .setPhase(Phase.EARLY).addMixinClasses("angelica.bugfixes.MixinRenderBlockFluid").setSide(Side.BOTH)
            .setApplyIf(() -> AngelicaConfig.fixFluidRendererCheckingBlockAgain)
            .addTargetedMod(TargetedMod.VANILLA)),

    ANGELICA_LIMIT_DROPPED_ITEM_ENTITIES(new Builder("Dynamically modifies the render distance of dropped items entities to preserve performance")
        .setPhase(Phase.EARLY).addMixinClasses("angelica.optimizations.MixinRenderGlobal_ItemRenderDist").setSide(Side.CLIENT)
        .setApplyIf(() -> AngelicaConfig.dynamicItemRenderDistance)
        .addTargetedMod(TargetedMod.VANILLA)),

    ANGELICA_ITEM_DISPLAY_LIST_OPTIMIZATION(new Builder("Optimized item rendering by wrapping them with display lists")
        .setPhase(Phase.EARLY).addMixinClasses("angelica.itemrenderer.MixinItemRenderer").setSide(Side.CLIENT)
        .setApplyIf(() -> AngelicaConfig.optimizeInWorldItemRendering_WIP)
        .addTargetedMod(TargetedMod.VANILLA)),

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
             "sodium.MixinBlock"
            ,"sodium.MixinBlockFluidBase"
            ,"sodium.AccessorBiomeColorEvent"
            ,"sodium.MixinBiomeGenBase"
            ,"sodium.MixinChunk"
            ,"sodium.MixinChunkProviderServer"
            ,"sodium.MixinClientRegistry"
            ,"sodium.MixinEntity_RenderDist"
            ,"sodium.MixinEntityItem_RenderDist"
            ,"sodium.MixinRenderManager"
            ,"sodium.MixinExtendedBlockStorage"
            ,"sodium.MixinEntityRenderer"
            ,"sodium.MixinFMLClientHandler"
            ,"sodium.MixinForgeHooksClient"
            ,"sodium.MixinGameSettings"
            ,"sodium.MixinFrustrum"
            ,"sodium.MixinMaterial"
            ,"sodium.MixinMinecraft"
            ,"sodium.MixinNibbleArray"
            ,"sodium.MixinRenderBlocks"
            ,"sodium.MixinRenderGlobal"
            ,"sodium.MixinWorldClient"
            ,"sodium.MixinTileEntity"
            ,"sodium.MixinTileEntityMobSpawner"
            ,"sodium.MixinEffectRenderer"
            ,"sodium.MixinTileEntityRendererDispatcher"
            ,"sodium.MixinLongHashMap"
            ,"sodium.MixinRenderingRegistry"
            ,"sodium.MixinPlayerManager"
        )
    ),

    IRIS_RENDERING(new Builder("Iris Shaders").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris).addMixinClasses(
             "shaders.MixinEntityRenderer"
            ,"shaders.MixinGuiIngameForge"
            ,"shaders.MixinFramebuffer"
            ,"shaders.MixinItem"
            ,"shaders.MixinItemRenderer"
            ,"shaders.MixinLocale"
            ,"shaders.MixinOpenGlHelper"
            ,"shaders.MixinRender"
            ,"shaders.MixinRendererLivingEntity"
            ,"shaders.MixinRenderGlobal"
            ,"shaders.MixinTileEntityBeaconRenderer"
        )
    ),

    IRIS_ACCESSORS(new Builder("Iris Accessors").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris).addMixinClasses(
             "shaders.accessors.MixinMinecraft"
            ,"shaders.accessors.MixinEntityRenderer"
            ,"shaders.accessors.MixinSimpleTexture"
            ,"shaders.accessors.MixinTextureAtlasSprite"
            ,"shaders.accessors.MixinTextureMap"
            ,"shaders.accessors.MixinAnimationMetadataSection"
        )
    ),

    ANGELICA_TEXTURE(new Builder("Textures").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableIris || AngelicaConfig.enableSodium).addMixinClasses(
             "angelica.textures.MixinTextureAtlasSprite"
            ,"angelica.textures.MixinTextureUtil"
        )),

    ANGELICA_ZOOM(new Builder("Zoom").addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT)
        .setPhase(Phase.EARLY).setApplyIf(() -> AngelicaConfig.enableZoom)
        .addMixinClasses(
            "angelica.zoom.MixinEntityRenderer_Zoom",
            "angelica.zoom.MixinMinecraft_Zoom",
            "angelica.zoom.MixinMouseFilter"
        )),

    HUD_CACHING(new Builder("Renders the HUD elements 20 times per second maximum to improve performance")
        .addTargetedMod(TargetedMod.VANILLA).setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableHudCaching).addMixinClasses(
        	"angelica.hudcaching.MixinGuiIngame",
        	"angelica.hudcaching.MixinGuiIngameForge",
            "angelica.hudcaching.MixinRenderGameOverlayEvent",
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

    SCALED_RESOUTION_UNICODE_FIX(new Builder("Removes unicode languages gui scaling being forced to even values").setPhase(Phase.EARLY)
        .setSide(Side.CLIENT).addTargetedMod(TargetedMod.VANILLA)
        .setApplyIf(() -> AngelicaConfig.removeUnicodeEvenScaling)
        .addMixinClasses("angelica.bugfixes.MixinScaledResolution_UnicodeFix")),

    EXTRA_UTILITIES_THREAD_SAFETY(new Builder("Enable thread safety fixes in Extra Utilities").setPhase(Phase.LATE)
        .addTargetedMod(TargetedMod.EXTRAUTILS).setSide(Side.CLIENT)
        .setApplyIf(() -> CompatConfig.fixExtraUtils)
        .addMixinClasses(
            "client.extrautils.MixinRenderBlockConnectedTextures",
            "client.extrautils.MixinRenderBlockConnectedTexturesEthereal",
            "client.extrautils.MixinIconConnectedTexture")),

    MFR_THREAD_SAFETY(new Builder("Enable thread safety fixes for MineFactory Reloaded").setPhase(Phase.LATE)
            .addTargetedMod(TargetedMod.MINEFACTORY_RELOADED).setSide(Side.CLIENT)
            .setApplyIf(() -> CompatConfig.fixMinefactoryReloaded)
            .addMixinClasses("client.minefactoryreloaded.MixinRedNetCableRenderer")),

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

    //From NotFine
    NOTFINE_BASE_MOD(new Builder("NotFine")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("notfine.",
            "clouds.MixinEntityRenderer",
            "clouds.MixinGameSettings",
            //"clouds.MixinRenderGlobal",
            "clouds.MixinWorldType",

            "fix.MixinRenderItem",

            "gui.MixinGuiSlot",

            "glint.MixinRenderBiped",
            "glint.MixinRenderPlayer",

            "optimization.MixinRenderItemFrame",

            "leaves.MixinBlockLeaves",
            "leaves.MixinBlockLeavesBase",

            "particles.MixinBlockEnchantmentTable",
            "particles.MixinEffectRenderer",
            "particles.MixinWorldClient",

            "renderer.MixinRenderGlobal",

            "toggle.MixinEntityRenderer",
            "toggle.MixinGuiIngame",
            "toggle.MixinRender",
            "toggle.MixinRenderItem",

            "interpolatedtexturemap.MixinTextureMap"
        ))
    ),
    BETTER_FACE_CULLING(new Builder("Better face culling")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("notfine.faceculling.",
            "MixinBlock",
            "MixinBlockCactus",
            "MixinBlockCarpet",
            "MixinBlockEnchantmentTable",
            "MixinBlockFarmland",
            "MixinBlockSlab",
            "MixinBlockSnow",
            "MixinBlockStairs",
            "MixinRenderBlocks"
        ))
    ),
    NOTFINE_NO_DYNAMIC_SURROUNDINGS(new Builder("NotFine no Dynamic Surroundings")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        .addTargetedMod(TargetedMod.VANILLA)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_MIST)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_ORIGINAL)
        .addMixinClasses("notfine.toggle.MixinEntityRenderer$RenderRainSnow")
    ),
    NOTFINE_NO_CUSTOM_ITEM_TEXTURES(new Builder("NotFine no Custom Item Textures")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> !AngelicaConfig.enableMCPatcherForgeFeatures || !MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("notfine.glint.",
            "MixinItemRenderer",
            "MixinRenderItem"
        ))
    ),
    NOTFINE_NATURA(new Builder("NotFine Natura compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.NATURA)
        .addMixinClasses(addPrefix("notfine.leaves.natura.",
            "MixinBerryBush",
            "MixinNetherBerryBush"
        ))
    ),
    NOTFINE_THAUMCRAFT(new Builder("NotFine Thaumcraft compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.THAUMCRAFT)
        .addMixinClasses("notfine.leaves.thaumcraft.MixinBlockMagicalLeaves")
    ),
    THAUMCRAFT_BETTER_FACE_CULLING(new Builder("Better face culling Thaumcraft compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addTargetedMod(TargetedMod.THAUMCRAFT)
        .addMixinClasses(addPrefix("notfine.faceculling.thaumcraft.",
            "MixinBlockWoodenDevice",
            "MixinBlockStoneDevice",
            "MixinBlockTable"
        ))
    ),
    NOTFINE_TINKERS_CONSTRUCT(new Builder("NotFine Tinker's Construct compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.TINKERS_CONSTRUCT)
        .addMixinClasses("notfine.leaves.tconstruct.MixinOreberryBush")
    ),
    NOTFINE_WITCHERY(new Builder("NotFine Witchery compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.WITCHERY)
        .addMixinClasses("notfine.leaves.witchery.MixinBlockWitchLeaves")
    ),
    NOTFINE_TWILIGHT_FOREST(new Builder("NotFine Twilight Forest compat")
        .setSide(Side.CLIENT).setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addTargetedMod(TargetedMod.TWILIGHT_FOREST)
        .addMixinClasses(addPrefix("notfine.leaves.twilightforest.",
            "MixinBlockTFLeaves",
            "MixinBlockTFLeaves3",
            // TODO: Verify 2.3.8.18 or later to support non NH builds?
            "MixinBlockTFMagicLeaves"
        ))
    ),
    MCPATCHER_FORGE(new Builder("MCPatcher Forge")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.",
            "base.MixinBlockGrass",
            "base.MixinBlockMycelium",

            "base.MixinAbstractTexture",
            "base.MixinTextureAtlasSprite",

            "base.MixinSimpleReloadableResourceManager",

            "base.MixinMinecraft"
        ))
    ),
    MCPATCHER_FORGE_RENDERPASS(new Builder("MCPatcher Forge Renderpass")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.renderPass)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.",
            "renderpass.MixinEntityRenderer",
            "renderpass.MixinRenderBlocks",
            "renderpass.MixinRenderGlobal",
            "renderpass.MixinWorldRenderer"
        ))
    ),
    MCPATCHER_FORGE_CUSTOM_COLORS(new Builder("MCP:F Custom Colors")
        .setSide(Mixins.Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomColors.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.cc.",
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
            "world.MixinWorldProviderHell"
        ))
    ),
    MCPATCHER_FORGE_CUSTOM_ITEM_TEXTURES(new Builder("MCP:F Custom Item Textures")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.cit.",
            "client.renderer.entity.MixinRenderBiped",
            "client.renderer.entity.MixinRenderEntityLiving",
            "client.renderer.entity.MixinRenderItem",
            "client.renderer.entity.MixinRenderPlayer",
            "client.renderer.entity.MixinRenderSnowball",
            "client.renderer.MixinItemRenderer",
            "client.renderer.MixinRenderGlobal",
            "entity.MixinEntityLivingBase",
            "item.MixinItem",
            "nbt.MixinNBTTagCompound",
            "nbt.MixinNBTTagList",
            "world.MixinWorld"
        ))
    ),
    MCPATCHER_FORGE_CONNECTED_TEXTURES(new Builder("MCP:F Connected Textures")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_EXTENDED_HD(new Builder("MCP:F Extended hd")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.hd.",
            "MixinTextureClock",
            "MixinTextureCompass",
            "MixinTextureManager"
        ))
    ),
    MCPATCHER_FORGE_EXTENDED_HD_FONT(new Builder("MCP:F Extended HD Font")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> (AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled && MCPatcherForgeConfig.ExtendedHD.hdFont))
        .addTargetedMod(TargetedMod.VANILLA)
        .addExcludedMod(TargetedMod.COFHCORE)
        .addMixinClasses("mcpatcherforge.hd.MixinFontRenderer")
    ),
    MCPATCHER_FORGE_RANDOM_MOBS(new Builder("MCP:F Random Mobs")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.RandomMobs.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.mob.",
            "MixinRender",
            "MixinRenderEnderman",
            "MixinRenderFish",
            "MixinRenderLiving",
            "MixinRenderMooshroom",
            "MixinRenderSheep",
            "MixinRenderSnowMan",
            "MixinRenderSpider",
            "MixinRenderWolf",
            "MixinEntityLivingBase"
        ))
    ),
    MCPATCHER_FORGE_SKY(new Builder("MCP:F Sky")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.BetterSkies.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(addPrefix("mcpatcherforge.sky.",
            "MixinEffectRenderer",
            "MixinRenderGlobal"
        ))
    ),
    MCPATCHER_FORGE_CC_NO_CTM(new Builder("MCP:F Custom Colors, no Connected Textures")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
            && !MCPatcherForgeConfig.ConnectedTextures.enabled
            && MCPatcherForgeConfig.CustomColors.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.cc_ctm.MixinRenderBlocksNoCTM")
    ),
    MCPATCHER_FORGE_CTM_NO_CC(new Builder("MCP:F Connected Textures, no Custom Colours")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
            && MCPatcherForgeConfig.ConnectedTextures.enabled
            && !MCPatcherForgeConfig.CustomColors.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC")
    ),
    MCPATCHER_FORGE_CTM_AND_CC(new Builder("MCP:F Connected Textures and Custom Colors")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
            && MCPatcherForgeConfig.ConnectedTextures.enabled
            && MCPatcherForgeConfig.CustomColors.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_CTM_OR_CC(new Builder("MCP:F Connected Textures or Custom Colors")
        .setSide(Side.CLIENT).setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
            && MCPatcherForgeConfig.ConnectedTextures.enabled
            || MCPatcherForgeConfig.CustomColors.enabled)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses("mcpatcherforge.ctm_cc.MixinTextureMap")
    ),
    //End from NotFine

    QPR(new Builder("Adds a QuadProvider field to blocks without populating it")
        .setSide(Side.CLIENT)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        .addTargetedMod(TargetedMod.VANILLA)
        .addMixinClasses(
            "angelica.models.MixinBlock",
            "angelica.models.MixinBlockOldLeaf")),

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
        NotFineConfig.loadSettings();
        //This may be possible to handle differently or fix.
        if(loadedCoreMods.contains("cofh.asm.LoadingPlugin")) {
            MCPatcherForgeConfig.ExtendedHD.hdFont = false;
        }
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
