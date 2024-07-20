package com.gtnewhorizons.angelica.hudcaching;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.RenderGameOverlayEventAccessor;
import com.kentington.thaumichorizons.common.ThaumicHorizons;
import cpw.mods.fml.common.eventhandler.EventPriority;
import net.dries007.holoInventory.client.Renderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.GuiIngameAccessor;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.GuiIngameForgeAccessor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.GuiIngameForge;
import thaumcraft.common.Thaumcraft;
import xaero.common.core.XaeroMinimapCore;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

// See LICENSE+HUDCaching.md for license information.

public class HUDCaching {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Framebuffer framebuffer;
    private static boolean dirty = true;
    private static long nextHudRefresh;

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
    private final static RenderGameOverlayEvent fakePreEvent = new RenderGameOverlayEvent.Pre(new RenderGameOverlayEvent(0, null, 0, 0), RenderGameOverlayEvent.ElementType.HELMET);

    public static final HUDCaching INSTANCE = new HUDCaching();


    private HUDCaching() {}

    /* TODO START REMOVE DEBUG STUFF */

    private final List<Long> updateTimeList = new ArrayList<>(21);
    private static boolean isEnabled = true;

    // moved initialization to when its registered to avoid an empty Debug category, which can cause crashes when
    // opening the controls menu and hudcaching is disabled in the config
    private static KeyBinding toggle;

    public static void registerKeyBindings() {
        toggle = new KeyBinding("Toggle HUDCaching", 0, "Debug");
        ClientRegistry.registerKeyBinding(toggle);
    }

    @SubscribeEvent
    public void onKeypress(InputEvent.KeyInputEvent event) {
        if (toggle != null && toggle.isPressed()) {
            isEnabled = !isEnabled;
            final String msg = isEnabled ? "Enabled HUDCaching" : "Disabled HUDCaching";
            if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }

    /* TODO END REMOVE DEBUG STUFF */

    // highest so it runs before the GLSM load event
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onJoinWorld(WorldEvent.Load event) {
        if (event.world.isRemote){
            LOGGER.info("World loaded - Initializing HUDCaching");
            HUDCaching.framebuffer = new Framebuffer(0, 0, true);
            HUDCaching.framebuffer.setFramebufferColor(0, 0, 0, 0);
        }
    }

    @SuppressWarnings("unused")
    public static void renderCachedHud(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        if (ModStatus.isXaerosMinimapLoaded && ingame instanceof GuiIngameForge) {
            // this used to be called by asming into renderGameOverlay, but we removed it
            XaeroMinimapCore.beforeIngameGuiRender(partialTicks);
        }

        if (!OpenGlHelper.isFramebufferEnabled() || !isEnabled || framebuffer == null) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        if (System.currentTimeMillis() > nextHudRefresh) {
            dirty = true;
        }

        if (dirty) {
            dirty = false;
            nextHudRefresh = System.currentTimeMillis() + (1000 / AngelicaConfig.hudCachingFPS);
            resetFramebuffer(mc.displayWidth, mc.displayHeight);
            framebuffer.bindFramebuffer(false);
            renderingCacheOverride = true;
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            renderingCacheOverride = false;
            mc.getFramebuffer().bindFramebuffer(false);
        } else {
        	renderer.setupOverlayRendering();
        }

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        GLStateManager.enableBlend();

        // reset the color that may be applied by some items
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // render bits that were captured when rendering into cache
        GuiIngameAccessor gui = (GuiIngameAccessor) ingame;
        if (renderVignetteCaptured)
        {
            gui.callRenderVignette(mc.thePlayer.getBrightness(partialTicks), width, height);
        } else {
        	GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        }
        if (ingame instanceof GuiIngameForge) {
        	GuiIngameForgeAccessor guiForge = ((GuiIngameForgeAccessor) ingame);
        	if (renderHelmetCaptured) {
        		guiForge.callRenderHelmet(resolution, partialTicks, hasScreen, mouseX, mouseY);
                if (ModStatus.isHoloInventoryLoaded){
                    Renderer.INSTANCE.angelicaOverride = false;
                    // only settings the partial ticks as mouseX and mouseY are not used in renderEvent
                    ((RenderGameOverlayEventAccessor) fakePreEvent).setPartialTicks(partialTicks);
                    Renderer.INSTANCE.renderEvent(fakePreEvent);
                }
        	}
        	if (renderPortalCapturedTicks > 0) {
        		guiForge.callRenderPortal(width, height, partialTicks);
        	}
        	if (renderCrosshairsCaptured) {
        		guiForge.callRenderCrosshairs(width, height);
        	}
            if (ModStatus.isThaumcraftLoaded || ModStatus.isThaumicHorizonsLoaded){
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setPartialTicks(partialTicks);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setResolution(resolution);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setMouseX(mouseX);
                ((RenderGameOverlayEventAccessor) fakeTextEvent).setMouseY(mouseY);
                if (ModStatus.isThaumcraftLoaded){
                    Thaumcraft.instance.renderEventHandler.renderOverlay(fakeTextEvent);
                }
                if (ModStatus.isThaumicHorizonsLoaded){
                    ThaumicHorizons.instance.renderEventHandler.renderOverlay(fakeTextEvent);
                }
            }
        } else {
            if (renderHelmetCaptured)
            {
                gui.callRenderPumpkinBlur(width, height);
            }
            if (renderPortalCapturedTicks > 0)
            {
                gui.callRenderPortal(renderPortalCapturedTicks, width, height);
            }
        }

        // render cached frame
        Tessellator tessellator = TessellatorManager.get();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        framebuffer.bindFramebufferTexture();
        drawTexturedRect(tessellator, (float) resolution.getScaledWidth_double(), (float) resolution.getScaledHeight_double());

        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        mc.getTextureManager().bindTexture(Gui.icons);
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

    private static void resetFramebuffer(int width, int height) {
        if (framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
        	framebuffer.createBindFramebuffer(width, height);
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        } else {
        	framebuffer.framebufferClear();
        }
        // copy depth buffer from MC
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
        OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, framebuffer.framebufferObject);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void drawTexturedRect(Tessellator tessellator, float width, float height) {
        GLStateManager.enableTexture();
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
        if (ModStatus.isHoloInventoryLoaded){
            Renderer.INSTANCE.angelicaOverride = isEnabled;
        }
    }

}
