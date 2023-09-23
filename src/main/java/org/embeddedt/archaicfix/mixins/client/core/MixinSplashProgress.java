/*
 * Based off SplashProgress from Forge 14.23.5.2860.
 */
package org.embeddedt.archaicfix.mixins.client.core;

import static org.lwjgl.opengl.GL11.*;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.client.SplashProgress;
import net.minecraft.client.gui.FontRenderer;
import org.apache.commons.lang3.StringUtils;
import org.embeddedt.archaicfix.ArchaicLogger;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(targets = { "cpw/mods/fml/client/SplashProgress$3" })
public class MixinSplashProgress {
    private static final int memoryGoodColor = 0x78CB34;
    private static final int memoryWarnColor = 0xE6E84A;
    private static final int memoryLowColor = 0xE42F2F;
    private static float memoryColorPercent;
    private static long memoryColorChangeTime;
    private static FontRenderer fontRenderer = null;
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", ordinal = 1, remap = false, shift = At.Shift.AFTER), remap = false, require = 0)
    private void injectDrawMemoryBar(CallbackInfo ci) {
        if(fontRenderer == null) {
            try {
                Field f = SplashProgress.class.getDeclaredField("fontRenderer");
                f.setAccessible(true);
                fontRenderer = (FontRenderer)f.get(null);
            } catch(ReflectiveOperationException e) {
                ArchaicLogger.LOGGER.error(e);
                return;
            }
        }
        glPushMatrix();
        glTranslatef(320 - Display.getWidth() / 2 + 4, 240 + Display.getHeight() / 2 - textHeight2, 0);
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        fontRenderer.drawString("ArchaicFix " + Tags.VERSION, 0, 0, 0x000000);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        if(ArchaicConfig.showSplashMemoryBar) {
            glPushMatrix();
            glTranslatef(320 - (float) barWidth / 2, 20, 0);
            drawMemoryBar();
            glPopMatrix();
        }
    }

    @Shadow
    private void setColor(int color) {

    }

    @Shadow
    private void drawBox(int w, int h) {

    }
    @Shadow
    private int barWidth, barHeight, textHeight2;

    private void drawMemoryBar() {
        int maxMemory = bytesToMb(Runtime.getRuntime().maxMemory());
        int totalMemory = bytesToMb(Runtime.getRuntime().totalMemory());
        int freeMemory = bytesToMb(Runtime.getRuntime().freeMemory());
        int usedMemory = totalMemory - freeMemory;
        float usedMemoryPercent = usedMemory / (float) maxMemory;

        glPushMatrix();
        // title - message
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        fontRenderer.drawString("Memory Used / Total", 0, 0, 0x000000);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        // border
        glPushMatrix();
        glTranslatef(0, textHeight2, 0);
        setColor(AccessorSplashProgress.getBarBorderColor());
        drawBox(barWidth, barHeight);
        // interior
        setColor(AccessorSplashProgress.getBarBackgroundColor());
        glTranslatef(1, 1, 0);
        drawBox(barWidth - 2, barHeight - 2);
        // slidy part

        long time = System.currentTimeMillis();
        if (usedMemoryPercent > memoryColorPercent || (time - memoryColorChangeTime > 1000))
        {
            memoryColorChangeTime = time;
            memoryColorPercent = usedMemoryPercent;
        }

        int memoryBarColor;
        if (memoryColorPercent < 0.75f)
        {
            memoryBarColor = memoryGoodColor;
        }
        else if (memoryColorPercent < 0.85f)
        {
            memoryBarColor = memoryWarnColor;
        }
        else
        {
            memoryBarColor = memoryLowColor;
        }
        setColor(memoryLowColor);
        glPushMatrix();
        glTranslatef((barWidth - 2) * (totalMemory) / (maxMemory) - 2, 0, 0);
        drawBox(2, barHeight - 2);
        glPopMatrix();
        setColor(memoryBarColor);
        drawBox((barWidth - 2) * (usedMemory) / (maxMemory), barHeight - 2);

        // progress text
        String progress = getMemoryString(usedMemory) + " / " + getMemoryString(maxMemory);
        glTranslatef(((float)barWidth - 2) / 2 - fontRenderer.getStringWidth(progress), 2, 0);
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        fontRenderer.drawString(progress, 0, 0, 0x000000);
        glPopMatrix();
    }

    private String getMemoryString(int memory)
    {
        return StringUtils.leftPad(Integer.toString(memory), 4, ' ') + " MB";
    }
    private static int bytesToMb(long bytes)
    {
        return (int) (bytes / 1024L / 1024L);
    }
}
