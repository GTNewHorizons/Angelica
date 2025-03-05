/*
 * Based off SplashProgress from Forge 14.23.5.2860.
 */
package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.client.SplashProgress;
import net.minecraft.client.gui.FontRenderer;
import org.apache.commons.lang3.StringUtils;
import org.lwjglx.opengl.Display;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPopMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPushMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glScalef;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glTranslatef;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;

@SuppressWarnings("deprecation")
@Mixin(targets = { "cpw/mods/fml/client/SplashProgress$3" })
public class MixinSplashProgress {
    @Unique
    private static final int angelica$memoryGoodColor = 0x78CB34;
    @Unique
    private static final int angelica$memoryWarnColor = 0xE6E84A;
    @Unique
    private static final int angelica$memoryLowColor = 0xE42F2F;
    @Unique
    private static float angelica$memoryColorPercent;
    @Unique
    private static long angelica$memoryColorChangeTime;
    @Unique
    private static FontRenderer angelica$fontRenderer = null;
    @Unique
    private static Boolean angelica$isArchaicFixPresent = null;

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", ordinal = 1, remap = false, shift = At.Shift.AFTER), remap = false, require = 0)
    private void injectDrawMemoryBar(CallbackInfo ci) {
        // NOTE: Disable this if you're checking for off thread GL calls via `-Dangelica.assertMainThread=true`
        if (angelica$fontRenderer == null) {
            try {
                Field f = SplashProgress.class.getDeclaredField("fontRenderer");
                f.setAccessible(true);
                angelica$fontRenderer = (FontRenderer) f.get(null);
            } catch (ReflectiveOperationException e) {
                AngelicaTweaker.LOGGER.error(e);
                return;
            }
        }

        // This runs before Loader.isModLoaded() works
        if (angelica$isArchaicFixPresent == null) {
            try {
                Class.forName("org.embeddedt.archaicfix.ArchaicCore");
                angelica$isArchaicFixPresent = true;
            } catch (Exception e) {
                angelica$isArchaicFixPresent = false;
            }
        }

        glPushMatrix();
        glTranslatef(320 - (float) Display.getWidth() / 2 + 4, 240 + (float) Display.getHeight() / 2 - textHeight2, 0);
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        angelica$fontRenderer.drawString("Angelica " + Tags.VERSION, 0, angelica$isArchaicFixPresent ? -10 : 0, 0x000000);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        if (AngelicaConfig.showSplashMemoryBar) {
            glPushMatrix();
            glTranslatef(320 - (float) barWidth / 2, 20, 0);
            angelica$drawMemoryBar();
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

    @Unique
    private void angelica$drawMemoryBar() {
        final int maxMemory = angelica$bytesToMb(Runtime.getRuntime().maxMemory());
        final int totalMemory = angelica$bytesToMb(Runtime.getRuntime().totalMemory());
        final int freeMemory = angelica$bytesToMb(Runtime.getRuntime().freeMemory());
        final int usedMemory = totalMemory - freeMemory;
        final float usedMemoryPercent = usedMemory / (float) maxMemory;

        glPushMatrix();
        // title - message
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        angelica$fontRenderer.drawString("Memory Used / Total", 0, 0, 0x000000);
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
        if (usedMemoryPercent > angelica$memoryColorPercent || (time - angelica$memoryColorChangeTime > 1000)) {
            angelica$memoryColorChangeTime = time;
            angelica$memoryColorPercent = usedMemoryPercent;
        }

        final int memoryBarColor;
        if (angelica$memoryColorPercent < 0.75f) {
            memoryBarColor = angelica$memoryGoodColor;
        } else if (angelica$memoryColorPercent < 0.85f) {
            memoryBarColor = angelica$memoryWarnColor;
        } else {
            memoryBarColor = angelica$memoryLowColor;
        }
        setColor(angelica$memoryLowColor);
        glPushMatrix();
        glTranslatef((barWidth - 2f) * totalMemory / maxMemory - 2, 0, 0);
        drawBox(2, barHeight - 2);
        glPopMatrix();
        setColor(memoryBarColor);
        drawBox((barWidth - 2) * (usedMemory) / (maxMemory), barHeight - 2);

        // progress text
        final String progress = angelica$getMemoryString(usedMemory) + " / " + angelica$getMemoryString(maxMemory);
        glTranslatef(((float)barWidth - 2) / 2 - angelica$fontRenderer.getStringWidth(progress), 2, 0);
        setColor(AccessorSplashProgress.getFontColor());
        glScalef(2, 2, 1);
        glEnable(GL_TEXTURE_2D);
        angelica$fontRenderer.drawString(progress, 0, 0, 0x000000);
        glPopMatrix();
    }

    @Unique
    private String angelica$getMemoryString(int memory) {
        return StringUtils.leftPad(Integer.toString(memory), 4, ' ') + " MB";
    }

    @Unique
    private static int angelica$bytesToMb(long bytes) {
        return (int) (bytes / 1024L / 1024L);
    }
}
