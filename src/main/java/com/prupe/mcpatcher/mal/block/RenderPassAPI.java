package com.prupe.mcpatcher.mal.block;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.mal.resource.BlendMethod;

public class RenderPassAPI {

    public static RenderPassAPI instance = new RenderPassAPI();

    private static final String[] NAMES = new String[] { "solid", "cutout_mipped", "cutout", "translucent", "backface",
        "overlay" };

    public static final int SOLID_RENDER_PASS = 0;
    public static final int CUTOUT_MIPPED_RENDER_PASS = 1;
    public static final int CUTOUT_RENDER_PASS = 2;
    public static final int TRANSLUCENT_RENDER_PASS = 3;
    public static final int BACKFACE_RENDER_PASS = 4;
    public static final int OVERLAY_RENDER_PASS = 5;
    public static final int MAX_BASE_RENDER_PASS = BACKFACE_RENDER_PASS;
    public static final int MAX_EXTRA_RENDER_PASS = NAMES.length - 1;
    public static final int NUM_RENDER_PASSES = NAMES.length;

    public int parseRenderPass(String value) {
        int pass = value.matches("\\d+") ? Integer.parseInt(value) : -1;
        if (value.equalsIgnoreCase("solid") || pass == 0) {
            return SOLID_RENDER_PASS;
        } else if (value.equalsIgnoreCase("cutout_mipped")) {
            return CUTOUT_MIPPED_RENDER_PASS;
        } else if (value.equalsIgnoreCase("cutout")) {
            return CUTOUT_RENDER_PASS;
        } else if (value.equalsIgnoreCase("translucent") || pass == 1) {
            return TRANSLUCENT_RENDER_PASS;
        } else if (value.equalsIgnoreCase("backface") || pass == 2) {
            return BACKFACE_RENDER_PASS;
        } else if (value.equalsIgnoreCase("overlay") || pass == 3) {
            return OVERLAY_RENDER_PASS;
        } else {
            return pass;
        }
    }

    public String getRenderPassName(int pass) {
        if (pass < 0) {
            return "(default)";
        } else if (pass < NAMES.length) {
            return NAMES[pass];
        } else {
            return "(unknown pass " + pass + ")";
        }
    }

    public boolean skipDefaultRendering(Block block) {
        return false;
    }

    public boolean skipThisRenderPass(Block block, int pass) {
        return pass > MAX_BASE_RENDER_PASS;
    }

    public boolean useColorMultiplierThisPass(Block block) {
        return true;
    }

    public boolean useLightmapThisPass() {
        return true;
    }

    public void clear() {}

    public void refreshBlendingOptions() {}

    public void setRenderPassForBlock(Block block, int pass) {}

    public ResourceLocation getBlankResource(int pass) {
        return BlendMethod.ALPHA.getBlankResource();
    }

    public ResourceLocation getBlankResource() {
        return BlendMethod.ALPHA.getBlankResource();
    }
}
