package com.gtnewhorizons.angelica.hudcaching;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.GuiIngameForgeAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.RenderGameOverlayEventAccessor;
import com.kentington.thaumichorizons.common.ThaumicHorizons;
import cpw.mods.fml.common.eventhandler.EventPriority;
import net.dries007.holoInventory.client.Renderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.GuiIngameForge;
import thaumcraft.common.Thaumcraft;
import xaero.common.core.XaeroMinimapCore;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

// See LICENSE-HUDCaching.md for license information.

public class HUDCaching {

    // Singleton instance
    public static final HUDCaching INSTANCE = new HUDCaching();

    // Instance variables (converted from static)
    private Minecraft mc;
    public Framebuffer framebuffer;
    private boolean dirty = true;
    private long nextHudRefresh;
    public boolean renderingCacheOverride;
    /*
     * Some HUD features cause problems/inaccuracies when being rendered into cache.
     * We capture those and render them later
     */
    // Vignette texture has no alpha
    public boolean renderVignetteCaptured;
    // Helmet & portal are chances other mods render vignette
    // For example Thaumcraft renders warp effect during this
    public boolean renderHelmetCaptured;
    public float renderPortalCapturedTicks;
    // Crosshairs need to be blended with the scene
    public boolean renderCrosshairsCaptured;

    // Static final variables that don't need to be instance-specific
    private final static RenderGameOverlayEvent fakeTextEvent = new RenderGameOverlayEvent.Text(new RenderGameOverlayEvent(0, null, 0, 0), null, null);
    private final static RenderGameOverlayEvent.Post fakePostEvent = new RenderGameOverlayEvent.Post(new RenderGameOverlayEvent(0, null, 0, 0), RenderGameOverlayEvent.ElementType.HELMET);

    // Private constructor for singleton
    private HUDCaching() {}

    // Lazy initialization of Minecraft
    private Minecraft getMinecraft() {
        if (mc == null) {
            mc = Minecraft.getMinecraft();
        }
        return mc;
    }


    // highest so it runs before the GLSM load event
    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings("unused")
    public void onJoinWorld(WorldEvent.Load event) {
        if (event.world.isRemote) {
            LOGGER.info("World loaded - Initializing HUDCaching");
            framebuffer = new Framebuffer(0, 0, true);
            framebuffer.setFramebufferColor(0, 0, 0, 0);
        }
    }

    // Static wrapper method for backward compatibility
    @SuppressWarnings("unused")
    public static void renderCachedHud(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        INSTANCE.renderCachedHudImpl(renderer, ingame, partialTicks, hasScreen, mouseX, mouseY);
    }

    // Instance implementation of renderCachedHud
    private void renderCachedHudImpl(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        if (ModStatus.isXaerosMinimapLoaded && ingame instanceof GuiIngameForge) {
            // this used to be called by asming into renderGameOverlay, but we removed it
            XaeroMinimapCore.beforeIngameGuiRender(partialTicks);
        }

        if (!OpenGlHelper.isFramebufferEnabled() || framebuffer == null) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        if (System.currentTimeMillis() > nextHudRefresh) {
            dirty = true;
        }

        if (dirty) {
            dirty = false;
            nextHudRefresh = System.currentTimeMillis() + (1000 / AngelicaConfig.hudCachingFPS);
            resetFramebuffer(getMinecraft().displayWidth, getMinecraft().displayHeight);
            framebuffer.bindFramebuffer(false);
            renderingCacheOverride = true;
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            renderingCacheOverride = false;
            getMinecraft().getFramebuffer().bindFramebuffer(false);
        } else {
            renderer.setupOverlayRendering();
        }

        final ScaledResolution resolution = new ScaledResolution(getMinecraft(), getMinecraft().displayWidth, getMinecraft().displayHeight);
        final int width = resolution.getScaledWidth();
        final int height = resolution.getScaledHeight();
        GLStateManager.enableBlend();

        // reset the color that may be applied by some items
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // render bits that were captured when rendering into cache
        final GuiIngameAccessor gui = (GuiIngameAccessor) ingame;
        if (renderVignetteCaptured) {
            gui.callRenderVignette(getMinecraft().thePlayer.getBrightness(partialTicks), width, height);
        } else {
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        }
        if (ingame instanceof GuiIngameForge) {
            final GuiIngameForgeAccessor guiForge = ((GuiIngameForgeAccessor) ingame);
            if (renderHelmetCaptured) {
                guiForge.callRenderHelmet(resolution, partialTicks, hasScreen, mouseX, mouseY);
                if (ModStatus.isHoloInventoryLoaded) {
                    Renderer.INSTANCE.angelicaOverride = false;
                    // only settings the partial ticks as mouseX and mouseY are not used in renderEvent
                    ((RenderGameOverlayEventAccessor) fakePostEvent).setPartialTicks(partialTicks);
                    Renderer.INSTANCE.renderEvent(fakePostEvent);
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
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setResolution(resolution);
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

        // render cached frame
        final Tessellator tessellator = TessellatorManager.get();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        framebuffer.bindFramebufferTexture();
        drawTexturedRect(tessellator, (float) resolution.getScaledWidth_double(), (float) resolution.getScaledHeight_double());

        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        getMinecraft().getTextureManager().bindTexture(Gui.icons);
    }

    /**
     * We are skipping certain render calls when rendering into cache,
     * however, we cannot skip the GL state changes. This will fix
     * the state before we start rendering
     */
    public static void fixGLStateBeforeRenderingCache() {
        INSTANCE.fixGLStateBeforeRenderingCacheImpl();
    }

    private void fixGLStateBeforeRenderingCacheImpl() {
        GLStateManager.glDepthMask(true);
        GLStateManager.enableDepthTest();
        GLStateManager.enableAlphaTest();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.disableBlend();
    }

    private void resetFramebuffer(int width, int height) {
        if (framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            framebuffer.createBindFramebuffer(width, height);
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        } else {
            framebuffer.framebufferClear();
        }
        // copy depth buffer from MC
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, getMinecraft().getFramebuffer().framebufferObject);
        OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, framebuffer.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private void drawTexturedRect(Tessellator tessellator, float width, float height) {
        GLStateManager.enableTexture2D();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, height, 0.0, 0, 0);
        tessellator.addVertexWithUV(width, height, 0.0, 1, 0);
        tessellator.addVertexWithUV(width, 0, 0.0, 1, 1);
        tessellator.addVertexWithUV(0, 0, 0.0, 0, 1);
        tessellator.draw();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    // moved to here due to the method being called from a mixin
    public static void disableHoloInventory() {
        INSTANCE.disableHoloInventoryImpl();
    }

    private void disableHoloInventoryImpl() {
        if (ModStatus.isHoloInventoryLoaded) {
            Renderer.INSTANCE.angelicaOverride = true;
        }
    }

    @SuppressWarnings("unused") // called via ASM
    public static class HUDCachingHooks {
        public static boolean shouldReturnEarly() {
            return INSTANCE.renderingCacheOverride;
        }
    }
}
