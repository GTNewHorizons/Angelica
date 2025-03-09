package org.taumc.celeritas.impl.render.terrain.fog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.lwjgl.opengl.GL11;

public class GLStateManagerFogService implements FogService {
    @Override
    public float getFogEnd() {
        return GL11.glGetInteger(GL11.GL_FOG_END);
    }

    @Override
    public float getFogStart() {
        return GL11.glGetInteger(GL11.GL_FOG_START);
    }

    @Override
    public float getFogDensity() {
        return GL11.glGetFloat(GL11.GL_FOG_DENSITY);
    }

    @Override
    public int getFogShapeIndex() {
        return 0;
    }

    @Override
    public float getFogCutoff() {
        return getFogEnd();
    }

    @Override
    public float[] getFogColor() {
        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        return new float[]{entityRenderer.fogColorRed, entityRenderer.fogColorGreen, entityRenderer.fogColorBlue, 1.0F};
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (!GL11.glGetBoolean(GL11.GL_FOG)) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GL11.glGetInteger(GL11.GL_FOG_MODE));
    }
}
