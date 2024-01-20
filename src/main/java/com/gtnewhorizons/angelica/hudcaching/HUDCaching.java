package com.gtnewhorizons.angelica.hudcaching;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

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
        framebuffer.framebufferColor[0] = 0.0F;
        framebuffer.framebufferColor[1] = 0.0F;
        framebuffer.framebufferColor[2] = 0.0F;
        framebuffer.framebufferColor[3] = 0.0F;
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

    static {
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

        GLStateManager.enableDepthTest();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        renderer.setupOverlayRendering();
        GLStateManager.enableBlend();
        
        if (dirty) {
            dirty = false;
            checkFramebufferSizes(mc.displayWidth, mc.displayHeight);
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(false);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GLStateManager.disableLighting();
            GLStateManager.disableFog();
            renderingCacheOverride = true;
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            renderingCacheOverride = false;
            mc.getFramebuffer().bindFramebuffer(false);
            GLStateManager.enableBlend();
        }
        
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
        
        Tessellator tessellator = TessellatorManager.get();
        
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
                gui.callFunc_130015_b(renderPortalCapturedTicks, width, height);
            }
            if (renderCrosshairsCaptured) {
            	mc.getTextureManager().bindTexture(Gui.icons);
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
                GLStateManager.enableAlphaTest();
                drawTexturedModalRect(tessellator, (width >> 1) - 7, (height >> 1) - 7);
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            }
        }

        // render cached frame
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        framebuffer.bindFramebufferTexture();
        drawTexturedRect(tessellator, (float) resolution.getScaledWidth_double(), (float) resolution.getScaledHeight_double());
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        GLStateManager.enableDepthTest();
    }

    private static void checkFramebufferSizes(int width, int height) {
        if (framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            framebuffer.createBindFramebuffer(width, height);
            framebuffer.framebufferWidth = width;
            framebuffer.framebufferHeight = height;
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        }
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

    private static void drawTexturedModalRect(Tessellator tessellator, int x, int y) {
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + 16, 100.0, 0.0, 0.0625);
        tessellator.addVertexWithUV(x + 16, y + 16, 100.0, 0.0625, 0.0625);
        tessellator.addVertexWithUV(x + 16, y, 100.0, 0.0625, 0.0);
        tessellator.addVertexWithUV(x, y, 100.0, 0.0, 0.0);
        tessellator.draw();
    }

}
