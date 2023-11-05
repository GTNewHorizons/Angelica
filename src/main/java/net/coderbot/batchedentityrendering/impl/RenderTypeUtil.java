package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import org.lwjgl.opengl.GL11;

public class RenderTypeUtil {
    public static boolean isTriangleStripDrawMode(RenderLayer renderType) {
        return renderType.mode() == GL11.GL_TRIANGLE_STRIP;
    }
}
