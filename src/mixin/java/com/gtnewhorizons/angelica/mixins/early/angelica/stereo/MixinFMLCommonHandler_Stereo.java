package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Duplicate {@code RenderTickEvent.END} per-eye. WAILA and other END-phase subscribers draw their
 * overlays in this event, which fires from {@code FMLCommonHandler.onRenderTickEnd} <em>after</em>
 * {@code EntityRenderer.updateCameraAndRender} returns and our stereo viewport restore has run —
 * without this redirect they draw at full viewport so a bottom-right-anchored overlay (e.g. WAILA
 * tooltip) lands at the right edge of the right eye instead of one copy per eye.
 */
@Mixin(value = FMLCommonHandler.class, priority = 1100, remap = false)
public abstract class MixinFMLCommonHandler_Stereo {

    @Redirect(
        method = "onRenderTickEnd",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z"
        )
    )
    private boolean angelica$stereoRenderTickEnd(EventBus bus, Event event) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        final boolean stereoActive = mode != null && mode.isActive()
            && StereoState.INSTANCE.getFrameHudMode() == StereoHudMode.DUPLICATE
            && mode.isSideBySide() && mode.isHalf();
        if (!stereoActive) {
            return bus.post(event);
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final int eyeW = fullW / 2;
        final int eyeH = fullH;

        // LEFT eye
        GL11.glViewport(0, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(0, 0, eyeW, eyeH);
        final boolean result = bus.post(event);
        StereoState.INSTANCE.exitGuiPass();

        // RIGHT eye: build a fresh event — Forge phase-tracking refuses re-posting the same instance.
        GL11.glViewport(eyeW, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(eyeW, 0, eyeW, eyeH);
        if (event instanceof TickEvent.RenderTickEvent) {
            final TickEvent.RenderTickEvent original = (TickEvent.RenderTickEvent) event;
            final TickEvent.RenderTickEvent copy =
                new TickEvent.RenderTickEvent(original.phase, original.renderTickTime);
            bus.post(copy);
        }
        StereoState.INSTANCE.exitGuiPass();

        GL11.glViewport(0, 0, fullW, fullH);
        return result;
    }
}
