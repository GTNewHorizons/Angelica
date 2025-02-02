package me.jellysquid.mods.sodium.client.gui.widgets;

import com.gtnewhorizons.angelica.compat.mojang.Drawable;
import com.gtnewhorizons.angelica.compat.mojang.Element;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

@Lwjgl3Aware
public abstract class AbstractWidget implements Drawable, Element {
    protected final FontRenderer font;

    protected AbstractWidget() {
        this.font = Minecraft.getMinecraft().fontRenderer;
    }

    protected void drawString(String str, int x, int y, int color) {
        this.font.drawString(str, x, y, color);
    }

    protected void drawRect(double x1, double y1, double x2, double y2, int color) {
        final float a = (float) (color >> 24 & 255) / 255.0F;
        final float r = (float) (color >> 16 & 255) / 255.0F;
        final float g = (float) (color >> 8 & 255) / 255.0F;
        final float b = (float) (color & 255) / 255.0F;

        this.drawQuads(vertices -> addQuad(vertices, x1, y1, x2, y2, a, r, g, b));
    }

    protected void drawQuads(Consumer<Tessellator> consumer) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.defaultBlendFunc();

        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        consumer.accept(tessellator);
        tessellator.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    protected static void addQuad(Tessellator consumer, double x1, double y1, double x2, double y2, float a, float r, float g, float b) {
        consumer.setColorRGBA_F(r, g, b, a);
        consumer.addVertex(x2, y1, 0.0D);
        consumer.addVertex(x1, y1, 0.0D);
        consumer.addVertex(x1, y2, 0.0D);
        consumer.addVertex(x2, y2, 0.0D);
    }

    protected void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    protected int getTextWidth(String text) {
        return this.font.getStringWidth(text);
    }
}
