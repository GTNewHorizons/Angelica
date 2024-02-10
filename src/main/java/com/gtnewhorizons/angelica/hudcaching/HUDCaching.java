package com.gtnewhorizons.angelica.hudcaching;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.GuiIngameAccessor;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.GuiIngameForgeAccessor;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
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

// See LICENSE+HUDCaching.md for license information.

public class HUDCaching {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Framebuffer framebuffer;
    static {
    	framebuffer = new Framebuffer(0, 0, true);
    	framebuffer.setFramebufferColor(0, 0, 0, 0);
    }
    private static boolean dirty = true;

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

    public static final HUDCaching INSTANCE = new HUDCaching();


    private HUDCaching() {}

    /* TODO START REMOVE DEBUG STUFF */

    private final List<Long> updateTimeList = new ArrayList<>(21);
    private static boolean isEnabled = true;
    private static final KeyBinding toggle = new KeyBinding("Toggle HUDCaching", 0, "Debug");

    public static void registerKeyBindings() {
        ClientRegistry.registerKeyBinding(toggle);
    }

    //    @SubscribeEvent
//    public void onFrame(RenderGameOverlayEvent.Post event) {
//        if (event.type == RenderGameOverlayEvent.ElementType.TEXT) {
//            final long currentTimeMillis = System.currentTimeMillis();
//            updateTimeList.removeIf(time -> currentTimeMillis - time > 1000L);
//            String text = EnumChatFormatting.GREEN + "HUD Fps : " + updateTimeList.size();
//            mc.fontRenderer.drawStringWithShadow(
//                    text,
//                    event.resolution.getScaledWidth() / 4,
//                    event.resolution.getScaledHeight() / 4,
//                    0xFFFFFF);
//            updateTimeList.add(currentTimeMillis);
//        }
//    }

    @SubscribeEvent
    public void onKeypress(InputEvent.KeyInputEvent event) {
        if (toggle.isPressed()) {
            isEnabled = !isEnabled;
            final String msg = isEnabled ? "Enabled HUDCaching" : "Disabled HUDCaching";
            if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }

    /* TODO END REMOVE DEBUG STUFF */

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            dirty = true;
        }
    }

    @SuppressWarnings("unused")
    public static void renderCachedHud(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {

        if (!OpenGlHelper.isFramebufferEnabled() || !isEnabled) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        if (dirty) {
            dirty = false;
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
        	}
        	if (renderPortalCapturedTicks > 0) {
        		guiForge.callRenderPortal(width, height, partialTicks);
        	}
        	if (renderCrosshairsCaptured) {
        		guiForge.callRenderCrosshairs(width, height);
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

}
