package net.coderbot.iris.sodium.vertex_format;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import org.lwjgl.opengl.GL11;

public class IrisGlVertexAttributeFormat {
	public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(GL11.GL_BYTE, 1);
	public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(GL11.GL_SHORT, 2);
}
