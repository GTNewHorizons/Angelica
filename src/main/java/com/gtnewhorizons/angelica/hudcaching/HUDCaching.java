package com.gtnewhorizons.angelica.hudcaching;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching.GuiIngameForgeAccessor;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
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
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

// TODO add license from Sk1er/patcher with special credit to Moulberry https://github.com/Moulberry

public class HUDCaching {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Framebuffer framebuffer;
    private static boolean dirty = true;
    public static boolean renderingCacheOverride;

    static {
        final HUDCaching hudCaching = new HUDCaching();
        FMLCommonHandler.instance().bus().register(hudCaching);
        MinecraftForge.EVENT_BUS.register(hudCaching);// TODO remove debug stuff, unsued registration
    }

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

    // TODO draw vignette

    @SuppressWarnings("unused")
    public static void renderCachedHud(EntityRenderer renderer, GuiIngame ingame, float partialTicks, boolean p_73830_2_, int p_73830_3_, int p_73830_4_) {

        if (!OpenGlHelper.isFramebufferEnabled() || !isEnabled) {
            ingame.renderGameOverlay(partialTicks, p_73830_2_, p_73830_3_, p_73830_4_);
            return;
        }

        GLStateManager.enableDepthTest();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        renderer.setupOverlayRendering();
        GLStateManager.enableBlend();

        if (framebuffer != null) {
            Tessellator tessellator = TessellatorManager.get();
            if (ingame instanceof GuiIngameForge) {
                ((GuiIngameForgeAccessor) ingame).callRenderCrosshairs(width, height);
            } else if (GuiIngameForge.renderCrosshairs) {
                mc.getTextureManager().bindTexture(Gui.icons);
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
                GLStateManager.enableAlphaTest();
                drawTexturedModalRect(tessellator, (width >> 1) - 7, (height >> 1) - 7);
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            }

            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            framebuffer.bindFramebufferTexture();
            drawTexturedRect(tessellator, (float) resolution.getScaledWidth_double(), (float) resolution.getScaledHeight_double());
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }

        if (framebuffer == null || dirty) {
            dirty = false;
            framebuffer = checkFramebufferSizes(framebuffer, mc.displayWidth, mc.displayHeight);
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(false);
            GLStateManager.disableBlend();
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GLStateManager.disableLighting();
            GLStateManager.disableFog();
            renderingCacheOverride = true;
            ingame.renderGameOverlay(partialTicks, p_73830_2_, p_73830_3_, p_73830_4_);
            renderingCacheOverride = false;
            mc.getFramebuffer().bindFramebuffer(false);
            GLStateManager.enableBlend();
        }

        GLStateManager.enableDepthTest();
    }

    private static Framebuffer checkFramebufferSizes(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null || framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            if (framebuffer == null) {
                framebuffer = new Framebuffer(width, height, true);
                framebuffer.framebufferColor[0] = 0.0F;
                framebuffer.framebufferColor[1] = 0.0F;
                framebuffer.framebufferColor[2] = 0.0F;
            } else {
                framebuffer.createBindFramebuffer(width, height);
            }
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        }
        return framebuffer;
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
