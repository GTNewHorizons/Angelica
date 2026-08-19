package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.hooks.events.*;
import net.minecraftforge.eventbus.api.bus.EventBus;

public final class GLSMHooks {

    public static DeferredBlendHandler blendHandler;
    public static DeferredAlphaHandler alphaHandler;
    public static DeferredDepthColorHandler depthColorHandler;
    public static GlintColorHandler glintColorHandler;
    public static ShaderWorkSubmitter shaderWorkSubmitter;
    public static ShaderTransformPostProcessor postTransformProcessor;
    public static PerFrameUniformBlock perFrameUniformBlock;
    public static PerFrameUniformBlock perPassUniformBlock;

    // EventBus instances
    public static final EventBus<TextureBindEvent> TEXTURE_BIND = EventBus.create(TextureBindEvent.class);
    public static final EventBus<TextureDeleteEvent> TEXTURE_DELETE = EventBus.create(TextureDeleteEvent.class);
    public static final EventBus<TextureUnitStateEvent> TEXTURE_UNIT_STATE = EventBus.create(TextureUnitStateEvent.class);
    public static final EventBus<ProgramChangeEvent> PROGRAM_CHANGE = EventBus.create(ProgramChangeEvent.class);
    public static final EventBus<ProgramDeleteEvent> PROGRAM_DELETE = EventBus.create(ProgramDeleteEvent.class);
    public static final EventBus<BlendFuncChangeEvent> BLEND_FUNC_CHANGE = EventBus.create(BlendFuncChangeEvent.class);
    public static final EventBus<FogStateChangeEvent> FOG_STATE_CHANGE = EventBus.create(FogStateChangeEvent.class);
    public static final EventBus<LightmapCoordsEvent> LIGHTMAP_COORDS = EventBus.create(LightmapCoordsEvent.class);
    public static final EventBus<AlphaStateChangeEvent> ALPHA_STATE_CHANGE = EventBus.create(AlphaStateChangeEvent.class);
    public static final EventBus<ShaderColorChangeEvent> SHADER_COLOR_CHANGE = EventBus.create(ShaderColorChangeEvent.class);
    public static final EventBus<ForeignDrawEndEvent> FOREIGN_DRAW_END = EventBus.create(ForeignDrawEndEvent.class);
    public static final EventBus<VanillaBlendChangeEvent> VANILLA_BLEND_CHANGE = EventBus.create(VanillaBlendChangeEvent.class);
    public static final EventBus<LoadingCheckpointEvent> LOADING_CHECKPOINT = EventBus.create(LoadingCheckpointEvent.class);

    // Reusable event instances
    public static final TextureBindEvent textureBindEvent = new TextureBindEvent();
    public static final TextureDeleteEvent textureDeleteEvent = new TextureDeleteEvent();
    public static final TextureUnitStateEvent textureUnitStateEvent = new TextureUnitStateEvent();
    public static final ProgramChangeEvent programChangeEvent = new ProgramChangeEvent();
    public static final ProgramDeleteEvent programDeleteEvent = new ProgramDeleteEvent();
    public static final BlendFuncChangeEvent blendFuncChangeEvent = new BlendFuncChangeEvent();
    public static final FogStateChangeEvent fogStateChangeEvent = new FogStateChangeEvent();
    public static final LightmapCoordsEvent lightmapCoordsEvent = new LightmapCoordsEvent();
    public static final AlphaStateChangeEvent alphaStateChangeEvent = new AlphaStateChangeEvent();
    public static final ShaderColorChangeEvent shaderColorChangeEvent = new ShaderColorChangeEvent();
    public static final ForeignDrawEndEvent foreignDrawEndEvent = new ForeignDrawEndEvent();
    public static final VanillaBlendChangeEvent vanillaBlendChangeEvent = new VanillaBlendChangeEvent();
    public static final LoadingCheckpointEvent loadingCheckpointEvent = new LoadingCheckpointEvent();

    private GLSMHooks() {}
}
