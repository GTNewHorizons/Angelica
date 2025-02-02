package com.prupe.mcpatcher.mal.resource;

import java.util.HashSet;
import java.util.Set;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.prupe.mcpatcher.MCPatcherUtils;

@Lwjgl3Aware
public class BlendMethod {

    private static final Set<ResourceLocation> blankResources = new HashSet<>();

    public static final BlendMethod ALPHA = new BlendMethod(
        "alpha",
        GL11.GL_SRC_ALPHA,
        GL11.GL_ONE_MINUS_SRC_ALPHA,
        true,
        false,
        true,
        0);
    public static final BlendMethod ADD = new BlendMethod("add", GL11.GL_SRC_ALPHA, GL11.GL_ONE, true, false, true, 0);
    public static final BlendMethod SUBTRACT = new BlendMethod(
        "subtract",
        GL11.GL_ONE_MINUS_DST_COLOR,
        GL11.GL_ZERO,
        true,
        true,
        false,
        0);
    public static final BlendMethod MULTIPLY = new BlendMethod(
        "multiply",
        GL11.GL_DST_COLOR,
        GL11.GL_ONE_MINUS_SRC_ALPHA,
        true,
        true,
        true,
        0xffffffff);
    public static final BlendMethod DODGE = new BlendMethod("dodge", GL11.GL_ONE, GL11.GL_ONE, true, true, false, 0);
    public static final BlendMethod BURN = new BlendMethod(
        "burn",
        GL11.GL_ZERO,
        GL11.GL_ONE_MINUS_SRC_COLOR,
        true,
        true,
        false,
        null);
    public static final BlendMethod SCREEN = new BlendMethod(
        "screen",
        GL11.GL_ONE,
        GL11.GL_ONE_MINUS_SRC_COLOR,
        true,
        true,
        false,
        0xffffffff);
    public static final BlendMethod OVERLAY = new BlendMethod(
        "overlay",
        GL11.GL_DST_COLOR,
        GL11.GL_SRC_COLOR,
        true,
        true,
        false,
        0x80808080);
    public static final BlendMethod REPLACE = new BlendMethod("replace", 0, 0, false, false, true, null);

    private final int srcBlend;
    private final int dstBlend;
    private final String name;
    private final boolean blend;
    private final boolean fadeRGB;
    private final boolean fadeAlpha;
    private final ResourceLocation blankResource;

    public static BlendMethod parse(String text) {
        text = text.toLowerCase()
            .trim();
        switch (text) {
            case "alpha" -> {
                return ALPHA;
            }
            case "add" -> {
                return ADD;
            }
            case "subtract" -> {
                return SUBTRACT;
            }
            case "multiply" -> {
                return MULTIPLY;
            }
            case "dodge" -> {
                return DODGE;
            }
            case "burn" -> {
                return BURN;
            }
            case "screen" -> {
                return SCREEN;
            }
            case "overlay", "color" -> {
                return OVERLAY;
            }
            case "replace", "none" -> {
                return REPLACE;
            }
            default -> {
                String[] tokens = text.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        int srcBlend = Integer.parseInt(tokens[0]);
                        int dstBlend = Integer.parseInt(tokens[1]);
                        return new BlendMethod(
                            "custom(" + srcBlend + "," + dstBlend + ")",
                            srcBlend,
                            dstBlend,
                            true,
                            true,
                            false,
                            0);
                    } catch (NumberFormatException e) {}
                }
            }
        }
        return null;
    }

    public static Set<ResourceLocation> getAllBlankResources() {
        return blankResources;
    }

    private BlendMethod(String name, int srcBlend, int dstBlend, boolean blend, boolean fadeRGB, boolean fadeAlpha,
        Integer neutralRGB) {
        this.name = name;
        this.srcBlend = srcBlend;
        this.dstBlend = dstBlend;
        this.blend = blend;
        this.fadeRGB = fadeRGB;
        this.fadeAlpha = fadeAlpha;
        if (neutralRGB == null) {
            blankResource = null;
        } else {
            String filename = String.format(MCPatcherUtils.BLANK_PNG_FORMAT, neutralRGB);
            blankResource = TexturePackAPI.newMCPatcherResourceLocation(filename);
        }
        if (blankResource != null) {
            blankResources.add(blankResource);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public void applyFade(float fade) {
        if (fadeRGB && fadeAlpha) {
            GL11.glColor4f(fade, fade, fade, fade);
        } else if (fadeRGB) {
            GL11.glColor4f(fade, fade, fade, 1.0f);
        } else if (fadeAlpha) {
            GL11.glColor4f(1.0f, 1.0f, 1.0f, fade);
        }
    }

    public void applyAlphaTest() {
        if (blend) {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        } else {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);
        }
    }

    public void applyDepthFunc() {
        if (blend) {
            GL11.glDepthFunc(GL11.GL_EQUAL);
        } else {
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
        }
    }

    public void applyBlending() {
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GLAPI.glBlendFuncSeparate(srcBlend, dstBlend, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    public boolean isColorBased() {
        return fadeRGB;
    }

    public boolean canFade() {
        return blend && (fadeAlpha || fadeRGB);
    }

    public ResourceLocation getBlankResource() {
        return blankResource;
    }
}
