package com.prupe.mcpatcher.cit;

import java.awt.image.BufferedImage;

import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.prupe.mcpatcher.mal.resource.BlendMethod;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

final class Enchantment extends OverrideBase {

    private static final float ITEM_2D_THICKNESS = 0.0625f;

    static float baseArmorWidth;
    static float baseArmorHeight;

    private static boolean lightingWasEnabled;

    final int layer;
    final BlendMethod blendMethod;
    private final float rotation;
    private final double speed;
    final float duration;

    private boolean armorScaleSet;
    private float armorScaleX;
    private float armorScaleY;

    static void beginOuter2D() {
        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.01f);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
    }

    static void endOuter2D() {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    static void beginOuter3D() {
        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.01f);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        lightingWasEnabled = GLStateManager.glGetBoolean(GL11.GL_LIGHTING);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
    }

    static void endOuter3D() {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        if (lightingWasEnabled) {
            GLStateManager.glEnable(GL11.GL_LIGHTING);
        }
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    Enchantment(PropertiesFile properties) {
        super(properties);

        if (properties.valid() && textureName == null && alternateTextures == null) {
            properties.error("no source texture specified");
        }

        layer = properties.getInt("layer", 0);
        String value = properties.getString("blend", "add");
        blendMethod = BlendMethod.parse(value);
        if (blendMethod == null) {
            properties.error("unknown blend type %s", value);
        }
        rotation = properties.getFloat("rotation", 0.0f);
        speed = properties.getDouble("speed", 0.0);
        duration = properties.getFloat("duration", 1.0f);

        String valueX = properties.getString("armorScaleX", "");
        String valueY = properties.getString("armorScaleY", "");
        if (!valueX.isEmpty() && !valueY.isEmpty()) {
            try {
                armorScaleX = Float.parseFloat(valueX);
                armorScaleY = Float.parseFloat(valueY);
                armorScaleSet = true;
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    String getType() {
        return "enchantment";
    }

    void render2D(float intensity, float x0, float y0, float x1, float y1, float z) {
        if (intensity <= 0.0f) {
            return;
        }
        if (intensity > 1.0f) {
            intensity = 1.0f;
        }
        if (!bindTexture(CITUtils.lastOrigIcon)) {
            return;
        }
        begin(intensity);
        final Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x0, y0, z, 0.0f, 0.0f);
        tess.addVertexWithUV(x0, y1, z, 0.0f, 1.0f);
        tess.addVertexWithUV(x1, y1, z, 1.0f, 1.0f);
        tess.addVertexWithUV(x1, y0, z, 1.0f, 0.0f);
        tess.draw();
        end();
    }

    void render3D(float intensity, int width, int height) {
        if (intensity <= 0.0f) {
            return;
        }
        if (intensity > 1.0f) {
            intensity = 1.0f;
        }
        if (!bindTexture(CITUtils.lastOrigIcon)) {
            return;
        }
        begin(intensity);
        ItemRenderer.renderItemIn2D(Tessellator.instance, 1.0f, 0.0f, 0.0f, 1.0f, width, height, ITEM_2D_THICKNESS);
        end();
    }

    boolean bindTexture(IIcon icon) {
        ResourceLocation texture;
        if (alternateTextures != null && icon != null) {
            texture = alternateTextures.get(icon.getIconName());
            if (texture == null) {
                texture = textureName;
            }
        } else {
            texture = textureName;
        }
        if (texture == null) {
            return false;
        } else {
            TexturePackAPI.bindTexture(texture);
            return true;
        }
    }

    void beginArmor(float intensity) {
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        begin(intensity);
        if (!armorScaleSet) {
            setArmorScale();
        }
        GLStateManager.glScalef(armorScaleX, armorScaleY, 1.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    void endArmor() {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        end();
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    void begin(float intensity) {
        blendMethod.applyBlending();
        blendMethod.applyDepthFunc();
        blendMethod.applyFade(intensity);
        GLStateManager.glPushMatrix();
        if (speed != 0.0) {
            double offset = ((double) System.currentTimeMillis() * speed) / 3000.0;
            offset -= Math.floor(offset);
            GLStateManager.glTranslatef((float) offset * 8.0f, 0.0f, 0.0f);
        }
        GLStateManager.glRotatef(rotation, 0.0f, 0.0f, 1.0f);
    }

    void end() {
        GLStateManager.glPopMatrix();
    }

    private void setArmorScale() {
        armorScaleSet = true;
        armorScaleX = 1.0f;
        armorScaleY = 0.5f;
        BufferedImage overlayImage = TexturePackAPI.getImage(textureName);
        if (overlayImage != null) {
            if (overlayImage.getWidth() < baseArmorWidth) {
                armorScaleX *= baseArmorWidth / (float) overlayImage.getWidth();
            }
            if (overlayImage.getHeight() < baseArmorHeight) {
                armorScaleY *= baseArmorHeight / (float) overlayImage.getHeight();
            }
        }
        logger.finer("%s: scaling by %.3fx%.3f for armor model", this, armorScaleX, armorScaleY);
    }
}
