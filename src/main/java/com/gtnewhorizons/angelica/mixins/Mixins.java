package com.gtnewhorizons.angelica.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import jss.notfine.config.MCPatcherForgeConfig;
import jss.notfine.config.NotFineConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Mixins implements IMixins {

    ANGELICA_STARTUP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.startup.MixinInitGLStateManager"
        )
    ),

    ANGELICA_CHAT_CACHE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.chat.MixinChatLine"
            , "angelica.chat.MixinGuiNewChat"
        )
    ),

    ANGELICA(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.MixinActiveRenderInfo"
            , "angelica.MixinEntityRenderer"
            , "angelica.MixinFMLClientHandler"
            , "angelica.MixinForgeHooksClient_CoreProfile"
            , "angelica.MixinGameSettings"
            , "angelica.MixinMinecraft"
            , "angelica.MixinMinecraft_FrameHook"
            , "angelica.MixinMinecraft_IconifyGuard"
            , "angelica.MixinMinecraftServer"
            , "angelica.MixinSimpleReloadableResourceManager"
            , "angelica.bugfixes.MixinItemRenderer_EdgeDepth"
            , "angelica.bugfixes.MixinModelCreeper_AuraBodyInflate"
            , "angelica.bugfixes.MixinModelSkeleton_LegPelvisZFight"
            , "angelica.bugfixes.MixinModelWither_ArmorCentering"
            , "angelica.bugfixes.MixinRenderBlocks_CrossedSquaresNormal"
            , "angelica.bugfixes.MixinRenderCreeper_AuraDepth"
            , "angelica.bugfixes.MixinRenderGlobal_DeferredEntityOverlay"
            , "angelica.bugfixes.MixinRenderGlobal_DestroyBlock"
            , "angelica.bugfixes.MixinRenderMooshroom_MushroomTint"
            , "angelica.bugfixes.MixinRendererLivingEntity_DeferredEntityOverlay"
            , "angelica.bugfixes.MixinRenderWither_ArmorCentering"
            , "angelica.debug.MixinMinecraft_FPSCap"
            , "angelica.ffp.MixinTessellator_CoreProfile"
            , "angelica.glsm.MixinSplashProgressCaching"
            , "angelica.gui.MixinGuiOptions"
            , "angelica.optimizations.MixinRendererLivingEntity"
            , "angelica.rendering.MixinRenderGlobal_SelectionBox"
            , "angelica.gui.MixinGuiIngameForge_ModernF3"
        )
    ),

    STARMINER_RENDERER_LIVING_ENTITY_OPTIMIZATION(new MixinBuilder()
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.STARMINER)
        .addClientMixins("client.starminer.MixinTransformClientHelper")
    ),

    ANGELICA_ENTITY_OVERLAYS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.entityOverlayFixes)
        .addExcludedMod(TargetedMod.CUSTOM_PLAYER_MODELS)
        .addClientMixins(
            "angelica.bugfixes.MixinRendererLivingEntity_EyeDepth"
        )
    ),

    ANGELICA_DAMAGE_OVERLAY(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.entityModernDamageOverlay)
        .addExcludedMod(TargetedMod.CUSTOM_PLAYER_MODELS)
        .addClientMixins(
            "angelica.bugfixes.MixinRendererLivingEntity_OverlayTint"
        )
    ),

    ANGELICA_SDL_GPU_DISPLAY(new MixinBuilder("SDL-GPU-aware Display.create path")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> SystemProperties.USE_SDL_GPU && SDLGPUGate.isSDLGPUAvailable())
        .addClientMixins(
            "sdlgpu.MixinForgeHooksClient_SDLGPUDisplay",
            "sdlgpu.MixinMinecraft_SDLGPUIcons"
        )
    ),

    ANGELICA_SDL_GPU_SHADOW_VOXEL_PREPASS(new MixinBuilder("Voxelization compute pre-pass before shadow raster (SDL_GPU)")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> SystemProperties.USE_SDL_GPU && SDLGPUGate.isSDLGPUAvailable())
        .addClientMixins("sdlgpu.MixinDefaultChunkRenderer_ShadowVoxelization")
    ),

    ANGELICA_VBO_CLOUDS(
        new MixinBuilder()
            .setApplyIf(() -> AngelicaConfig.enableVBOClouds)
            .setPhase(Phase.EARLY)
            .addClientMixins("angelica.vbo.MixinRenderGlobal")
    ),

    ANGELICA_VBO_CLOUDS_FAR_PLANE(
        new MixinBuilder()
            .setApplyIf(() -> AngelicaConfig.enableVBOClouds && !AngelicaConfig.enableNotFineFeatures)
            .setPhase(Phase.EARLY)
            .addClientMixins("notfine.clouds.MixinEntityRenderer")
    ),

    ANGELICA_PANORAMA_BLUR(
        new MixinBuilder("Replace main menu panorama with modern equivalent")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> AngelicaConfig.enablePanoramaBlurShader)
            .addClientMixins("angelica.gui.MixinGuiMainMenu")
    ),

    ANGELICA_GL_SPLASH_TEXT(
        new MixinBuilder("Rewrite 'OpenGL 1.2!' splash to reflect the actual GL context")
            .setPhase(Phase.EARLY)
            .addClientMixins("angelica.gui.MixinGuiMainMenuSplash")
    ),

    ANGELICA_FONT_RENDERER(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableFontRenderer)
        .addClientMixins(
            "angelica.fontrenderer.MixinGuiIngameForge"
            , "angelica.fontrenderer.MixinFontRenderer"
            , "angelica.fontrenderer.MixinMCResourceAccessor"
        )
    ),

    ANGELICA_TESR_SIGN_CACHE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableFontRenderer && AngelicaConfig.enableTESRSignCache)
        .addClientMixins(
            "angelica.tesr.MixinTileEntitySignRenderer"
        )
    ),

    ANGELICA_TESR_CHEST_CACHE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableTESRChestCache)
        .addClientMixins(
            "angelica.tesr.MixinTileEntityChestRenderer",
            "angelica.tesr.MixinTileEntityEnderChestRenderer"
        )
    ),

    ANGELICA_TESR_SKULL_CACHE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableTESRSkullCache)
        .addClientMixins(
            "angelica.tesr.MixinTileEntitySkullRenderer"
        )
    ),

    ANGELICA_ENTITY_BATCHING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableEntityBatching)
        .addClientMixins(
            "angelica.entity.MixinModelRenderer",
            "angelica.entity.MixinRenderGlobal_EntityBatch",
            "angelica.entity.MixinRenderManager_BatchEligibility",
            "angelica.entity.MixinRender_BatchEligibility",
            "angelica.entity.MixinTextureManager",
            "angelica.tesr.MixinTileEntitySpecialRenderer_BatchEligibility"
        )
    ),

    ANGELICA_SKIP_END_FRAME_FLUSH(new MixinBuilder("Skip the end-of-frame glFlush before the buffer swap")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.skipEndOfFrameFlush)
        .addClientMixins(
            "angelica.MixinMinecraft_SkipEndFrameFlush"
        )
    ),

    THAUMCRAFT_TESR_JAR_CACHE(new MixinBuilder("Batch TC4 jar liquid via the retained TESR mesh cache")
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableTESRJarCache)
        .addRequiredMod(TargetedMod.THAUMCRAFT)
        .addClientMixins(
            "client.thaumcraft.MixinTileJarRenderer"
        )
    ),

    ANGELICA_TESR_BEACON_CACHE(new MixinBuilder("Batch the vanilla beacon beam via the retained TESR mesh cache")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableTESRBeaconCache)
        .addClientMixins(
            "angelica.tesr.MixinTileEntityBeaconRenderer"
        )
    ),

    ANGELICA_TESR_PROVIDER_DISPATCH(new MixinBuilder("Route TesrMeshProvider renderers through the batched mesh cache, and observe renderers that mix model parts with their own draws")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableTESRProviderDispatch || AngelicaConfig.enableEntityBatching)
        .addClientMixins(
            "angelica.tesr.MixinTileEntityRendererDispatcher"
        )
    ),

    ANGELICA_TRACY(new MixinBuilder("Tracy profiler zones from vanilla Profiler sections")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaMod.tracyEnabled)
        .addCommonMixins(
            "angelica.tracy.MixinProfiler_Tracy"
            , "angelica.tracy.MixinNetHandlerPlayServer_Tracy"
            , "angelica.tracy.MixinMinecraftServer_Tracy"
        )
        .addClientMixins(
            "angelica.tracy.MixinMinecraft_Tracy"
            , "angelica.tracy.MixinRenderGlobal_Tracy"
        )
    ),

    ANGELICA_ENABLE_DEBUG(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaMod.lwjglDebug)
        .addClientMixins(
            "angelica.debug.MixinSplashProgress"
        )
    ),
    ANGELICA_DEBUG_MARKERS(new MixinBuilder("RenderDoc/RGP debug groups + object labels")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaMod.lwjglDebug || CaptureGate.enabledAtStartup())
        .addClientMixins(
            "angelica.debug.MixinProfiler"
            , "angelica.debug.MixinTextureManager"
            , "celeritas.debug.MixinGLDebug"
        )
    ),
    ANGELICA_DYNAMIC_LIGHTS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableDynamicLights)
        .addClientMixins(
            "angelica.dynamiclights.MixinEntityRenderer"
            , "angelica.dynamiclights.MixinEntity"
            , "angelica.dynamiclights.MixinWorld"
            , "angelica.dynamiclights.MixinItemRenderer"
            , "angelica.dynamiclights.MixinEntityCreeper"
            , "angelica.dynamiclights.MixinEntityTNTPrimed"
        )
    ),

    ANGELICA_FIX_BLOCK_CRACK(
            new MixinBuilder("Block corners and edges between chunks might have \"cracks\" in them. This option fixes it")
                    .setPhase(Phase.EARLY)
                    .addClientMixins("angelica.bugfixes.MixinRenderBlocks_CrackFix")
                    .addExcludedMod(TargetedMod.FALSETWEAKS)
                    .setApplyIf(() -> AngelicaConfig.blockCrackFix)),

    ANGELICA_FIX_FLUID_RENDERER_CHECKING_BLOCK_AGAIN(
        new MixinBuilder("Fix RenderBlockFluid reading the block type from the world access multiple times")
            .setPhase(Phase.EARLY)
            .addClientMixins("angelica.bugfixes.MixinRenderBlockFluid")
            .setApplyIf(() -> AngelicaConfig.fixFluidRendererCheckingBlockAgain)),

    ANGELICA_LIMIT_DROPPED_ITEM_ENTITIES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.optimizations.MixinRenderGlobal_ItemRenderDist")
        .setApplyIf(() -> AngelicaConfig.dynamicItemRenderDistance)),

    ANGELICA_ITEM_RENDERER_OPTIMIZATION(new MixinBuilder("Optimizes in-world item rendering")
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.itemrenderer.MixinItemRenderer",
            "angelica.itemrenderer.MixinRenderItemFrame",
            "angelica.itemrenderer.MixinRenderBlocks"
        )
        .setApplyIf(() -> AngelicaConfig.optimizeInWorldItemRendering)),

    ANGELICA_OPTIMIZE_GLALLOCATION(new MixinBuilder("Replace HashMap with fastutil Int2IntMap in GLAllocation")
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.optimizations.MixinGLAllocation")),

    ANGELICA_DEFERRED_TESSELLATOR_BATCH(new MixinBuilder("Deferred tessellator batching for particles to reduce draw calls")
        .setPhase(Phase.EARLY)
        .addClientMixins("angelica.particles.MixinEffectRenderer_DeferredBatch")),

    // Not compatible with the lwjgl debug callbacks, so disable if that's enabled
    ARCHAIC_SPLASH(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.showSplashMemoryBar && !AngelicaMod.lwjglDebug)
        .addClientMixins(
            "angelica.archaic.MixinSplashProgress$3",
            "angelica.archaic.AccessorSplashProgress"
        )
    ),

    ARCHAIC_CORE(new MixinBuilder()
        .addExcludedMod(TargetedMod.ARCHAICFIX)
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.archaic.MixinBlockFence"
            , "angelica.archaic.MixinFMLClientHandler"
            , "angelica.archaic.MixinGuiIngameForge"
            , "angelica.archaic.MixinNetHandlerPlayClient"
            , "angelica.archaic.MixinThreadDownloadImageData"
        )
    ),

    IRIS_STARTUP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
            "shaders.startup.MixinGameSettings"
            , "shaders.startup.MixinMinecraft"
            , "shaders.startup.MixinInitRenderer"
            , "shaders.startup.MixinAbstractTexture"
            , "shaders.startup.MixinTextureAtlasSprite"
            , "shaders.startup.MixinTextureMap"
        )
    ),

    RENDERING_INFRASTRUCTURE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "rendering.MixinBlock"
            , "rendering.MixinBlockFluidBase"
            , "rendering.MixinBlockLiquid"
            , "rendering.BlockLiquidFlowInvoker"
            , "rendering.AccessorBiomeColorEvent"
            , "rendering.MixinBiomeGenBase"
            , "rendering.MixinChunk"
            , "rendering.MixinChunkProviderServer"
            , "rendering.MixinClientRegistry"
            , "rendering.MixinEntity_RenderDist"
            , "rendering.MixinEntityItem_RenderDist"
            , "rendering.MixinEntityRenderer"
            , "rendering.MixinFMLClientHandler"
            , "rendering.MixinGameSettings"
            , "rendering.MixinLongHashMap"
            , "rendering.MixinMinecraft"
            , "rendering.MixinPlayerManager"
            , "rendering.MixinRenderBlocks"
            , "rendering.MixinRenderingRegistry"
            , "rendering.MixinTessellator"
            , "rendering.MixinTileEntity"
            , "rendering.MixinTileEntityMobSpawner"
            , "rendering.MixinTileEntityRendererDispatcher"
            , "rendering.MixinRenderBlocksEmissive"
        )
    ),

    CELERITAS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
              "celeritas.terrain.MixinChunkProviderClient"
            , "celeritas.terrain.MixinMinecraft_ChunkUpdates"
            , "celeritas.terrain.MixinRenderGlobal"
            , "celeritas.terrain.MixinRenderListManager"
            , "celeritas.terrain.MixinRenderSectionManager"
            , "celeritas.terrain.MixinWorldClient"
            , "celeritas.frustum.MixinClippingHelper"
            , "celeritas.frustum.MixinClippingHelperImpl"
            , "celeritas.frustum.MixinFrustrum"
            , "celeritas.features.culling.MixinEffectRenderer"
            , "celeritas.features.mipmaps.MixinTextureAtlasSprite"
            , "celeritas.features.mipmaps.MixinTextureMetadataSection"
            , "celeritas.features.mipmaps.MixinTextureMetadataSectionSerializer"
            , "celeritas.features.textures.MixinTextureMap"
            , "celeritas.features.textures.MixinTextureAtlasSprite"
            , "celeritas.biome_blending.MixinBlockGrass"
            , "celeritas.biome_blending.MixinBlockLeaves"
            , "celeritas.biome_blending.MixinBlockLiquid"
            , "celeritas.threading.MixinForgeHooksClient"
            , "celeritas.terrain.MixinChunk"
            , "celeritas.terrain.MixinWorldClient_WorkerAccess"
            , "celeritas.terrain.MixinWorld_WorkerMutationGuard"
            , "celeritas.terrain.MixinTileEntity_AwaitingDescriptor"
            , "celeritas.terrain.MixinNetHandlerPlayClient_DescriptorRepair"
            , "celeritas.terrain.MixinWorld_AwaitingDescriptor"
            , "celeritas.terrain.MixinRenderRegion"
            , "celeritas.terrain.MixinSectionRenderDataStorage"
            , "celeritas.terrain.MixinDefaultChunkRenderer"
            , "celeritas.terrain.MixinDefaultChunkShaderInterface"
        )
    ),

    CELERITAS_COLORED_LIGHT(new MixinBuilder("Colored light infrastructure for celeritas light pipeline")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> {
            BlockLightProvider.freezeMixinConfig();
            return BlockLightProvider.coloredLightEnabled();
        })
        .addClientMixins(
            "celeritas.light.MixinQuadLightData",
            "celeritas.light.MixinLightDataAccess",
            "celeritas.light.MixinLightDataCache",
            "celeritas.light.MixinAoFaceData",
            "celeritas.light.MixinSmoothLightPipeline",
            "celeritas.light.MixinFlatLightPipeline"
        )
    ),

    IRIS_SHADERS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
              "shaders.MixinDroppedItemGlintEdges"
            , "shaders.MixinHeldItemGlintEdges"
            , "shaders.MixinEntityPickupFX"
            , "shaders.MixinEntityRenderer"
            , "shaders.MixinGuiIngameForge"
            , "shaders.MixinFramebuffer"
            , "shaders.MixinItem"
            , "shaders.MixinLocale"
            , "shaders.MixinRender"
            , "shaders.MixinRenderBiped"
            , "shaders.MixinRenderBlocks_FaceNormals"
            , "shaders.MixinRenderBlocks_FallingShading"
            , "shaders.MixinRenderBlocks_ItemId"
            , "shaders.MixinRenderDragon"
            , "shaders.MixinRenderEntityFlame"
            , "shaders.MixinRenderFallingBlock"
            , "shaders.MixinRendererLivingEntity"
            , "shaders.MixinRenderGlobal"
            , "shaders.AccessorEntityHorse"
            , "shaders.MixinRenderHorse"
            , "shaders.MixinRenderItem"
            , "shaders.MixinRenderNameTag"
            , "shaders.MixinRenderPlayerArmor"
            , "shaders.MixinTileEntityBeaconRenderer"
            , "shaders.MixinWorldNbtCache"
            , "shaders.MixinNetHandlerPlayClient_NbtRemesh"
            , "shaders.MixinRenderEndPortal"
            , "shaders.MixinTileEntityRendererDispatcher"
            , "shaders.MixinGlProgram"
            , "shaders.MixinTextureManager_ReloadCount"
            , "shaders.AccessorModelBox"
            , "shaders.MixinModelBiped"
        )
    ),

    IRIS_SHADERS_RENDER_MANAGER(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addExcludedMod(TargetedMod.DRAGON_API)
        .addClientMixins(
            "shaders.MixinRenderManager"
        )
    ),

    IRIS_SHADERS_RENDER_MANAGER_DAPI(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addRequiredMod(TargetedMod.DRAGON_API)
        .addClientMixins(
            "shaders.MixinRenderManagerDAPI"
        )
    ),

    DRAGONAPI_SHADER_REGISTRY_PARSE_ERROR(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addRequiredMod(TargetedMod.DRAGON_API)
        .addClientMixins(
            "dragonapi.MixinShaderRegistry_ParseError"
        )
    ),

    IRIS_RENDERING_NOBACKHAND(new MixinBuilder("Iris Hand Shaders")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addExcludedMod(TargetedMod.BACKHAND)
        .addClientMixins(
            "shaders.MixinItemRenderer"
        )
    ),

    IRIS_RENDERING_BACKHAND(new MixinBuilder("Iris Hand Shaders (Backhand)")
        .addRequiredMod(TargetedMod.BACKHAND)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins(
            "shaders.MixinItemRendererBackhand"
        )
    ),

    IRIS_SHADERS_CHISEL_BEACON(new MixinBuilder("Iris Chisel Beacon Beam")
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addRequiredMod(TargetedMod.CHISEL)
        .addClientMixins(
            "chisel.MixinRenderCarvableBeacon"
        )
    ),

    ANGELICA_TEXTURE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins(
            "angelica.textures.MixinTextureAtlasSprite"
            , "angelica.textures.MixinTextureUtil"
        )),

    ANGELICA_ZOOM(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableZoom)
        .addClientMixins(
            "angelica.zoom.MixinEntityRenderer_Zoom",
            "angelica.zoom.MixinMinecraft_Zoom",
            "angelica.zoom.MixinMouseFilter"
        )),

    HUD_CACHING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableHudCaching)
        .addClientMixins(
            "angelica.hudcaching.MixinGuiIngame",
            "angelica.hudcaching.MixinGuiIngameForge",
            "angelica.hudcaching.MixinRenderGameOverlayEvent",
            "angelica.hudcaching.MixinEntityRenderer_HUDCaching",
            "angelica.hudcaching.MixinFramebuffer_HUDCaching",
            "angelica.hudcaching.MixinGuiIngame_HUDCaching",
            "angelica.hudcaching.MixinGuiIngameForge_HUDCaching",
            "angelica.hudcaching.MixinRenderItem")
    ),

    OPTIMIZE_WORLD_UPDATE_LIGHT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.ARCHAICFIX)
        .addExcludedMod(TargetedMod.SUPERNOVA)
        .setApplyIf(() -> AngelicaConfig.optimizeWorldUpdateLight)
        .addCommonMixins("angelica.lighting.MixinWorld_FixLightUpdateLag")),

    SPEEDUP_VANILLA_ANIMATIONS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins(
            "angelica.animation.MixinTextureAtlasSprite",
            "angelica.animation.MixinTextureMap",
            "angelica.animation.MixinChunkCache",
            "angelica.animation.MixinRenderBlocks")),

    SCALED_RESOUTION_UNICODE_FIX(new MixinBuilder("Removes unicode languages gui scaling being forced to even values")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.removeUnicodeEvenScaling)
        .addClientMixins("angelica.bugfixes.MixinScaledResolution_UnicodeFix")),

    FARSEEK_WORLDSLICE_COMPAT(new MixinBuilder("Let Farseek resolve celeritas' WorldSlice so Streams' water renders properly")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.FARSEEK)
        .addClientMixins("client.farseek.MixinFarseekIBlockAccessValue")),

    SECURITYCRAFT_COMPAT(new MixinBuilder("Fix reflection in SecurityCraft for compat with Angelica")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.SECURITYCRAFT)
        .setApplyIf(() -> CompatConfig.fixSecurityCraft)
        .addClientMixins(
            "client.securitycraft.MixinBlockReinforcedFenceGate",
            "client.securitycraft.MixinBlockReinforcedIronBars"
        )),

    EXTRA_UTILITIES_THREAD_SAFETY(new MixinBuilder("Enable thread safety fixes in Extra Utilities")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.EXTRAUTILS)
        .setApplyIf(() -> CompatConfig.fixExtraUtils)
        .addClientMixins(
            "client.extrautils.MixinRenderBlockConnectedTextures",
            "client.extrautils.MixinRenderBlockConnectedTexturesEthereal",
            "client.extrautils.MixinIconConnectedTexture")),

    MFR_THREAD_SAFETY(new MixinBuilder("Enable thread safety fixes for MineFactory Reloaded")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.MINEFACTORY_RELOADED)
        .setApplyIf(() -> CompatConfig.fixMinefactoryReloaded)
        .addClientMixins("client.minefactoryreloaded.MixinRedNetCableRenderer")),

    NTM_SPACE_COMPAT(new MixinBuilder("Multiple fixes for NTM:Space")
            .setPhase(Phase.LATE)
            .addRequiredMod(TargetedMod.NTM_SPACE)
            .setApplyIf(() -> CompatConfig.fixNTMSpace && AngelicaConfig.enableIris)
            .addClientMixins(
                    "client.ntmSpace.MixinSkyProviderCelestial",
                    "client.ntmSpace.MixinSkyProviderOrbit",
                    "client.ntmSpace.MixinSkyProviderLaytheSunset"
            )),

    SPEEDUP_CAMPFIRE_BACKPORT_ANIMATIONS(new MixinBuilder("Add animation speedup support to Campfire Backport")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.CAMPFIRE_BACKPORT)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins("client.campfirebackport.MixinRenderBlockCampfire")),

    IC2_FLUID_RENDER_FIX(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addRequiredMod(TargetedMod.IC2)
        .setApplyIf(() -> AngelicaConfig.speedupAnimations)
        .addClientMixins("angelica.textures.ic2.MixinRenderLiquidCell")),

    REPLACE_FFP(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.replaceFFPUploads)
        .addClientMixins(
            "angelica.vbo.MixinFramebuffer",
            "angelica.vbo.MixinWavefrontObject")),

    //From NotFine
    NOTFINE_BASE_MOD(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addClientMixins(addPrefix("notfine.",
            "clouds.MixinEntityRenderer",
            "clouds.MixinGameSettings",
            //"clouds.MixinRenderGlobal",
            "clouds.MixinWorldProvider",

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
    BETTER_FACE_CULLING(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addClientMixins(addPrefix("notfine.faceculling.",
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
    BOP_FOG_BIOME_CACHE(new MixinBuilder()
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.BIOMES_O_PLENTY)
        .addClientMixins("client.biomesoplenty.MixinFogHandlerBiomeCache")
    ),

    NOTFINE_BOP_FOG(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.BIOMES_O_PLENTY)
        .addClientMixins("notfine.toggle.biomesoplenty.MixinFogHandler")
    ),
    NOTFINE_NO_DYNAMIC_SURROUNDINGS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_MIST)
        .addExcludedMod(TargetedMod.DYNAMIC_SURROUNDINGS_ORIGINAL)
        .addClientMixins("notfine.toggle.MixinEntityRenderer$RenderRainSnow")
    ),
    NOTFINE_NO_CUSTOM_ITEM_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.DRAGON_API)
        .setApplyIf(() -> !AngelicaConfig.enableMCPatcherForgeFeatures || !MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addClientMixins(addPrefix("notfine.glint.",
            "MixinItemRenderer",
            "MixinRenderItem"
        ))
    ),
    NOTFINE_NATURA_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.NATURA)
        .addClientMixins(addPrefix("notfine.leaves.natura.",
            "MixinBerryBush",
            "MixinNetherBerryBush"
        ))
    ),
    NOTFINE_THAUMCRAFT_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.THAUMCRAFT)
        .addClientMixins("notfine.leaves.thaumcraft.MixinBlockMagicalLeaves")
    ),
    THAUMCRAFT_BETTER_FACE_CULLING(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> NotFineConfig.betterBlockFaceCulling)
        .addRequiredMod(TargetedMod.THAUMCRAFT)
        .addClientMixins(addPrefix("notfine.faceculling.thaumcraft.",
            "MixinBlockWoodenDevice",
            "MixinBlockStoneDevice",
            "MixinBlockTable"
        ))
    ),
    NOTFINE_TINKERS_CONSTRUCT_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.TINKERS_CONSTRUCT)
        .addClientMixins("notfine.leaves.tconstruct.MixinOreberryBush")
    ),
    NOTFINE_WITCHERY_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.WITCHERY)
        .addClientMixins("notfine.leaves.witchery.MixinBlockWitchLeaves")
    ),
    NOTFINE_TWILIGHT_FOREST_COMPAT(new MixinBuilder()
        .setPhase(Phase.LATE)
        .setApplyIf(() -> AngelicaConfig.enableNotFineFeatures)
        .addRequiredMod(TargetedMod.TWILIGHT_FOREST)
        .addClientMixins(addPrefix("notfine.leaves.twilightforest.",
            "MixinBlockTFLeaves",
            "MixinBlockTFLeaves3",
            // TODO: Verify 2.3.8.18 or later to support non NH builds?
            "MixinBlockTFMagicLeaves"
        ))
    ),
    ET_FUTURUM_ELYTRA_CAPE(new MixinBuilder("Set custom item ID for elytra with cape texture")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetedMod.ET_FUTURUM_REQUIEM)
        .setApplyIf(() -> AngelicaConfig.enableIris)
        .addClientMixins("client.etfuturum.MixinLayerBetterElytra")
    ),
    MCPATCHER_FORGE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures)
        .addClientMixins(addPrefix("mcpatcherforge.",
            "base.MixinBlockGrass",
            "base.MixinBlockMycelium",
            "base.MixinAbstractTexture",
            "base.MixinTextureAtlasSprite",
            "base.MixinSimpleReloadableResourceManager",
            "base.MixinMinecraft"
        ))
    ),
    MCPATCHER_FORGE_RENDERPASS_BASE(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> NotFineConfig.renderPass)
        .addClientMixins(addPrefix("mcpatcherforge.renderpass.",
            "MixinEntityRenderer",
            "MixinRenderBlocks",
            "MixinWorldRenderer"
        ))
    ),
    MCPATCHER_FORGE_CUSTOM_COLORS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.cc.",
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
    MCPATCHER_FORGE_CUSTOM_ITEM_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.DRAGON_API)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.CustomItemTextures.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.cit.",
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
    MCPATCHER_FORGE_CONNECTED_TEXTURES(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ConnectedTextures.enabled)
        .addClientMixins("mcpatcherforge.ctm.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_EXTENDED_HD(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.hd.",
            "MixinTextureClock",
            "MixinTextureCompass",
            "MixinTextureManager"
        ))
    ),
    MCPATCHER_FORGE_EXTENDED_HD_FONT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> (AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.ExtendedHD.enabled && MCPatcherForgeConfig.ExtendedHD.hdFont))
        .addExcludedMod(TargetedMod.COFHCORE)
        .addClientMixins("mcpatcherforge.hd.MixinFontRenderer")
    ),
    MCPATCHER_FORGE_RANDOM_MOBS(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.RandomMobs.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.mob.",
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
    MCPATCHER_FORGE_SKY(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures && MCPatcherForgeConfig.BetterSkies.enabled)
        .addClientMixins(addPrefix("mcpatcherforge.sky.",
            "MixinEffectRenderer",
            "MixinRenderGlobal"
        ))
    ),
    MCPATCHER_FORGE_CC_NO_CTM(new MixinBuilder("MCP:F Custom Colors, no Connected Textures")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && !MCPatcherForgeConfig.ConnectedTextures.enabled
                          && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCTM")
    ),
    MCPATCHER_FORGE_CTM_NO_CC(new MixinBuilder("MCP:F Connected Textures, no Custom Colours")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          && !MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC")
    ),
    MCPATCHER_FORGE_CTM_AND_CC(new MixinBuilder("MCP:F Connected Textures and Custom Colors")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          && MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinRenderBlocks")
    ),
    MCPATCHER_FORGE_CTM_OR_CC(new MixinBuilder("MCP:F Connected Textures or Custom Colors")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> AngelicaConfig.enableMCPatcherForgeFeatures
                          && MCPatcherForgeConfig.ConnectedTextures.enabled
                          || MCPatcherForgeConfig.CustomColors.enabled)
        .addClientMixins("mcpatcherforge.ctm_cc.MixinTextureMap")
    ),
    //End from NotFine
    ;

    private final MixinBuilder builder;

    private static String[] addPrefix(String prefix, String... values) {
        final List<String> list = new ArrayList<>(values.length);
        for (String s : values) {
            list.add(prefix + s);
        }
        return list.toArray(new String[values.length]);
    }
}
