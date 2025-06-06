/*
 * Based off SplashProgress from Forge 14.23.5.2860.
 */
package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.client.font.SplashFontRendererRef;
import com.gtnewhorizons.angelica.config.AngelicaConfig;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslatef;

@Mixin(targets = { "cpw/mods/fml/client/SplashProgress$3" })
public class MixinSplashProgress$3 {

    private static final int MEMORY_GOOD_COLOR = 0x78CB34;
    private static final int MEMORY_WARN_COLOR = 0xE6E84A;
    private static final int MEMORY_LOW_COLOR = 0xE42F2F;
    private static float memoryColorPercent;
    private static long memoryColorChangeTime;

    @Unique
    private static Boolean angelica$isArchaicFixPresent = null;

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", ordinal = 1, remap = false, shift = At.Shift.AFTER), remap = false, require = 0)
    private void injectDrawMemoryBar(CallbackInfo ci) {
        // This runs before Loader.isModLoaded() works
        if (angelica$isArchaicFixPresent == null) {
            try {
                Class.forName("org.embeddedt.archaicfix.ArchaicCore");
                angelica$isArchaicFixPresent = true;
            }
            catch (Exception e) {
                angelica$isArchaicFixPresent = false;
            }
        }

        glPushMatrix();
        glTranslatef(320 - Display.getWidth() / 2 + 4, 240 + Display.getHeight() / 2 - textHeight2, 0);
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        SplashFontRendererRef.fontRenderer.drawString("Angelica " + Tags.VERSION, 0, angelica$isArchaicFixPresent ? -10 : 0, 0x000000);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        if(AngelicaConfig.showSplashMemoryBar) {
            glPushMatrix();
            glTranslatef(320 - (float) barWidth / 2, 20, 0);
            drawMemoryBar();
            glPopMatrix();
        }
    }

    @Shadow(remap = false)
    private void setColor(int color) {}

    @Shadow(remap = false)
    private void drawBox(int w, int h) {}

    @Final
    @Shadow(remap = false)
    private int barWidth, barHeight, textHeight2;

    private void drawMemoryBar() {
        final int maxMemory = bytesToMb(Runtime.getRuntime().maxMemory());
        final int totalMemory = bytesToMb(Runtime.getRuntime().totalMemory());
        final int freeMemory = bytesToMb(Runtime.getRuntime().freeMemory());
        final int usedMemory = totalMemory - freeMemory;
        final float usedMemoryPercent = usedMemory / (float) maxMemory;

        glPushMatrix();
        // title - message
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        SplashFontRendererRef.fontRenderer.drawString("Memory Used / Total", 0, 0, 0x000000);
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

        final long time = System.currentTimeMillis();
        if (usedMemoryPercent > memoryColorPercent || (time - memoryColorChangeTime > 1000)) {
            memoryColorChangeTime = time;
            memoryColorPercent = usedMemoryPercent;
        }

        final int memoryBarColor;
        if (memoryColorPercent < 0.75f) {
            memoryBarColor = MEMORY_GOOD_COLOR;
        } else if (memoryColorPercent < 0.85f) {
            memoryBarColor = MEMORY_WARN_COLOR;
        } else {
            memoryBarColor = MEMORY_LOW_COLOR;
        }
        setColor(MEMORY_LOW_COLOR);
        glPushMatrix();
        glTranslatef((barWidth - 2) * (totalMemory) / (maxMemory) - 2, 0, 0);
        drawBox(2, barHeight - 2);
        glPopMatrix();
        setColor(memoryBarColor);
        drawBox((barWidth - 2) * (usedMemory) / (maxMemory), barHeight - 2);

        // progress text
        final String progress = getMemoryString(usedMemory) + " / " + getMemoryString(maxMemory);
        glTranslatef(((float)barWidth - 2) / 2 - SplashFontRendererRef.fontRenderer.getStringWidth(progress), 2, 0);
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        SplashFontRendererRef.fontRenderer.drawString(progress, 0, 0, 0x000000);
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
