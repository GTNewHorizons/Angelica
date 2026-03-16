package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.hooks.events.BlendFuncChangeEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.FogStateChangeEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.LightmapCoordsEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.ProgramChangeEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.TextureBindEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.TextureDeleteEvent;
import com.gtnewhorizons.angelica.glsm.hooks.events.TextureUnitStateEvent;
import net.minecraftforge.eventbus.api.bus.EventBus;

public final class GLSMHooks {

    public static DeferredBlendHandler blendHandler;
    public static DeferredAlphaHandler alphaHandler;
    public static DeferredDepthColorHandler depthColorHandler;

    // EventBus instances
    public static final EventBus<TextureBindEvent> TEXTURE_BIND = EventBus.create(TextureBindEvent.class);
    public static final EventBus<TextureDeleteEvent> TEXTURE_DELETE = EventBus.create(TextureDeleteEvent.class);
    public static final EventBus<TextureUnitStateEvent> TEXTURE_UNIT_STATE = EventBus.create(TextureUnitStateEvent.class);
    public static final EventBus<ProgramChangeEvent> PROGRAM_CHANGE = EventBus.create(ProgramChangeEvent.class);
    public static final EventBus<BlendFuncChangeEvent> BLEND_FUNC_CHANGE = EventBus.create(BlendFuncChangeEvent.class);
    public static final EventBus<FogStateChangeEvent> FOG_STATE_CHANGE = EventBus.create(FogStateChangeEvent.class);
    public static final EventBus<LightmapCoordsEvent> LIGHTMAP_COORDS = EventBus.create(LightmapCoordsEvent.class);

    // Reusable event instances
    public static final TextureBindEvent textureBindEvent = new TextureBindEvent();
    public static final TextureDeleteEvent textureDeleteEvent = new TextureDeleteEvent();
    public static final TextureUnitStateEvent textureUnitStateEvent = new TextureUnitStateEvent();
    public static final ProgramChangeEvent programChangeEvent = new ProgramChangeEvent();
    public static final BlendFuncChangeEvent blendFuncChangeEvent = new BlendFuncChangeEvent();
    public static final FogStateChangeEvent fogStateChangeEvent = new FogStateChangeEvent();
    public static final LightmapCoordsEvent lightmapCoordsEvent = new LightmapCoordsEvent();

    private GLSMHooks() {}
}
