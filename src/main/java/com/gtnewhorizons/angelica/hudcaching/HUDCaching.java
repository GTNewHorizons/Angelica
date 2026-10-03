package com.gtnewhorizons.angelica.hudcaching;

import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.CustomFramebuffer;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.holoinventory.HoloInventoryReflectionCompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.event.ClientEvent;
import com.gtnewhorizons.angelica.event.ClientEventType;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.stacks.RetainedState;
import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameForgeAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.RenderGameOverlayEventAccessor;
import com.gtnewhorizons.angelica.render.PanoramaRenderer;
import com.kentington.thaumichorizons.common.ThaumicHorizons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import thaumcraft.common.Thaumcraft;
import xaero.common.core.XaeroMinimapCore;

// See LICENSE-HUDCaching.md for license information.

public class HUDCaching {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public static CustomFramebuffer framebuffer;
    private static boolean dirty = true;
    private static long nextHudRefresh;

    private static final RetainedState hudExitState = new RetainedState();
    private static StateSet allState;
    private static GlProgram<Object> compositeProgram;
    private static int compositeVao;

    private static final Tracy.ZoneId Z_CACHE_WRAP = Tracy.zoneId("hudCacheWrap", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DRAW = Tracy.zoneId("hudDraw", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_CAPTURED_BITS = Tracy.zoneId("hudCapturedBits", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_COMPOSITE = Tracy.zoneId("hudComposite", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_APPLY_RETAINED = Tracy.zoneId("hudApplyRetained", Tracy.COLOR_CLIENT);

    public static boolean renderingCacheOverride;

    /*
     * Some HUD features cause problems/inaccuracies when being rendered into cache.
     * We capture those and render them later
     */
    // Vignette texture has no alpha
    public static boolean renderVignetteCaptured;
    // Helmet & portal are chances other mods render vignette
    // For example Thaumcraft renders warp effect during this
    public static boolean renderHelmetCaptured;
    public static float renderPortalCapturedTicks;
    // Crosshairs need to be blended with the scene
    public static boolean renderCrosshairsCaptured;

    private final static RenderGameOverlayEvent fakeTextEvent = new RenderGameOverlayEvent.Text(new RenderGameOverlayEvent(0, null, 0, 0), null, null);
    private final static RenderGameOverlayEvent.Post fakePostEvent = new RenderGameOverlayEvent.Post(new RenderGameOverlayEvent(0, null, 0, 0), RenderGameOverlayEvent.ElementType.HELMET);

    private static ScaledResolution resolution;
    private static int resolutionWidth, resolutionHeight, resolutionGuiScale;
    private static boolean resolutionUnicode;

    private static ScaledResolution resolution() {
        final int width = mc.displayWidth, height = mc.displayHeight, guiScale = mc.gameSettings.guiScale;
        final boolean unicode = mc.func_152349_b();
        if (resolution == null || width != resolutionWidth || height != resolutionHeight || guiScale != resolutionGuiScale || unicode != resolutionUnicode) {
            resolutionWidth = width;
            resolutionHeight = height;
            resolutionGuiScale = guiScale;
            resolutionUnicode = unicode;
            resolution = new ScaledResolution(mc, width, height);
        }
        return resolution;
    }

    private static void setupOverlay(ScaledResolution scaled) {
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0.0D, scaled.getScaledWidth_double(), scaled.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);
    }

    public static void init() {
        framebuffer = new CustomFramebuffer(CustomFramebuffer.DEPTH_TEXTURE | CustomFramebuffer.STENCIL_BUFFER);
    }

    public static void resetAfterCrash() {
        renderingCacheOverride = false;
        GLStateManager.setHudCacheOverride(false);
        dirty = true;
    }

    private static void initComposite() {
        compositeProgram = PanoramaRenderer.loadProgram("angelica:hud_composite", "angelica:panorama_blur", "angelica:hud_composite", ctx -> ctx);
        compositeProgram.bind();
        GLStateManager.glUniform1i(GLStateManager.glGetUniformLocation(compositeProgram.handle(), "u_Depth"), 1);
        compositeProgram.unbind();
        compositeVao = GLStateManager.glGenVertexArrays();
    }

    private static void composite() {
        if (compositeProgram == null) initComposite();
        final int program = GLStateManager.getActiveProgram();
        final int vao = GLStateManager.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int stateDepth = GLStateManager.pushState(allState);
        try {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.enableDepthTest();
            GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
            GLStateManager.glDepthMask(true);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.disableCull();
            GLStateManager.disableScissorTest();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.depthAttachment);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
            compositeProgram.bind();
            GLStateManager.glBindVertexArray(compositeVao);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glUseProgram(program);
            GLStateManager.popStateTo(stateDepth);
        }
    }

    public static void renderCachedHud(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        ClientEvent.post(ClientEventType.PRE_RENDER_GUI, renderer, partialTicks);
        ClientEvent.post(ClientEventType.PRE_RENDER_HUD, renderer, partialTicks);
        if (ModStatus.isXaerosMinimapLoaded && ingame instanceof GuiIngameForge) {
            // this used to be called by asming into renderGameOverlay, but we removed it
            XaeroMinimapCore.beforeIngameGuiRender(partialTicks);
        }

        GLStateManager.disableLighting();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.disableTexture();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();

        if (!AngelicaConfig.hudCachingActive || mc.displayWidth < 16 || mc.displayHeight < 16) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            ClientEvent.post(ClientEventType.POST_RENDER_HUD, renderer, partialTicks);
            return;
        }

        if (System.currentTimeMillis() > nextHudRefresh) {
            dirty = true;
        }
        if (allState == null) allState = StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS);

        if (dirty) {
            dirty = false;
            nextHudRefresh = System.currentTimeMillis() + (1000 / AngelicaConfig.hudCachingFPS);
            if (Tracy.ENABLED) Tracy.beginZone(Z_CACHE_WRAP);
            final int outerDepth = GLStateManager.pushState(allState);
            try {
                if (framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
                    framebuffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
                } else {
                    framebuffer.clearBindFramebuffer();
                }
                final int hudDepth = GLStateManager.pushState(allState);
                renderingCacheOverride = true;
                GLStateManager.setHudCacheOverride(true);
                if (Tracy.ENABLED) Tracy.beginZone(Z_DRAW);
                try {
                    ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
                } finally {
                    if (Tracy.ENABLED) Tracy.endZone();
                }
                GLStateManager.setHudCacheOverride(false);
                GLStateManager.popStateTo(hudDepth + 1);
                GLStateManager.retainModifiedState(hudDepth, hudExitState);
            } finally {
                renderingCacheOverride = false;
                GLStateManager.setHudCacheOverride(false);
                GLStateManager.popStateTo(outerDepth);
                mc.getFramebuffer().bindFramebuffer(false);
                if (Tracy.ENABLED) Tracy.endZone();
            }
        }

        final ScaledResolution scaled = resolution();
        setupOverlay(scaled);
        int width = scaled.getScaledWidth();
        int height = scaled.getScaledHeight();
        if (Tracy.ENABLED) Tracy.beginZone(Z_CAPTURED_BITS);
        GLStateManager.enableBlend();

        // reset the color that may be applied by some items
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // render bits that were captured when rendering into cache
        GuiIngameAccessor gui = (GuiIngameAccessor) ingame;
        if (renderVignetteCaptured) {
            gui.callRenderVignette(mc.thePlayer.getBrightness(partialTicks), width, height);
        } else {
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        }

        if (ingame instanceof GuiIngameForge) {
            GuiIngameForgeAccessor guiForge = ((GuiIngameForgeAccessor) ingame);
            if (renderHelmetCaptured) {
                guiForge.callRenderHelmet(scaled, partialTicks, hasScreen, mouseX, mouseY);
                if (ModStatus.isHoloInventoryLoaded) {
                    HoloInventoryReflectionCompat.setAngelicaOverride(false);
                    // only settings the partial ticks as mouseX and mouseY are not used in renderEvent
                    ((RenderGameOverlayEventAccessor) fakePostEvent).setPartialTicks(partialTicks);
                    HoloInventoryReflectionCompat.renderEvent(fakePostEvent);
                }
            }
            if (renderPortalCapturedTicks > 0) {
                guiForge.callRenderPortal(width, height, partialTicks);
            }
            if (renderCrosshairsCaptured) {
                if (ModStatus.isXaerosMinimapLoaded) {
                    // this fixes the crosshair going invisible when no lines are being drawn under the minimap
                    GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
                guiForge.callRenderCrosshairs(width, height);
            }
            if (ModStatus.isThaumcraftLoaded || ModStatus.isThaumicHorizonsLoaded) {
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setPartialTicks(partialTicks);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setResolution(scaled);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setMouseX(mouseX);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setMouseY(mouseY);
                if (ModStatus.isThaumcraftLoaded) {
                    Thaumcraft.instance.renderEventHandler.renderOverlay(fakeTextEvent);
                }
                if (ModStatus.isThaumicHorizonsLoaded) {
                    ThaumicHorizons.instance.renderEventHandler.renderOverlay(fakeTextEvent);
                }
            }
        } else {
            if (renderHelmetCaptured) {
                gui.callRenderPumpkinBlur(width, height);
            }
            if (renderPortalCapturedTicks > 0) {
                gui.callRenderPortal(renderPortalCapturedTicks, width, height);
            }
        }

        if (Tracy.ENABLED) Tracy.endZone();

        if (Tracy.ENABLED) Tracy.beginZone(Z_COMPOSITE);
        composite();
        if (Tracy.ENABLED) Tracy.endZone();
        if (Tracy.ENABLED) Tracy.beginZone(Z_APPLY_RETAINED);
        GLStateManager.applyRetainedState(hudExitState);
        if (Tracy.ENABLED) Tracy.endZone();

        ClientEvent.post(ClientEventType.POST_RENDER_HUD, renderer, partialTicks);
    }

    /**
     * We are skipping certain render calls when rendering into cache,
     * however, we cannot skip the GL state changes. This will fix
     * the state before we start rendering
     */
    public static void fixGLStateBeforeRenderingCache() {
        GLStateManager.glDepthMask(true);
        GLStateManager.enableDepthTest();
        GLStateManager.enableAlphaTest();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.disableBlend();
    }

    public static void disableHoloInventory() {
        if (ModStatus.isHoloInventoryLoaded) {
            HoloInventoryReflectionCompat.setAngelicaOverride(true);
        }
    }

    @SuppressWarnings("unused") // called via ASM
    public static class HUDCachingHooks {
        public static boolean shouldReturnEarly() {
            return renderingCacheOverride;
        }
    }
}
