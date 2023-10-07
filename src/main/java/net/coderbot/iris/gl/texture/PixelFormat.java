package net.coderbot.iris.gl.texture;

import net.coderbot.iris.gl.GlVersion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Optional;

public enum PixelFormat {
	RED(GL11.GL_RED, GlVersion.GL_11, false),
	RG(GL30.GL_RG, GlVersion.GL_30, false),
	RGB(GL11.GL_RGB, GlVersion.GL_11, false),
	BGR(GL12.GL_BGR, GlVersion.GL_12, false),
	RGBA(GL11.GL_RGBA, GlVersion.GL_11, false),
	BGRA(GL12.GL_BGRA, GlVersion.GL_12, false),
	RED_INTEGER(GL30.GL_RED_INTEGER, GlVersion.GL_30, true),
	RG_INTEGER(GL30.GL_RG_INTEGER, GlVersion.GL_30, true),
	RGB_INTEGER(GL30.GL_RGB_INTEGER, GlVersion.GL_30, true),
	BGR_INTEGER(GL30.GL_BGR_INTEGER, GlVersion.GL_30, true),
	RGBA_INTEGER(GL30.GL_RGBA_INTEGER, GlVersion.GL_30, true),
	BGRA_INTEGER(GL30.GL_BGRA_INTEGER, GlVersion.GL_30, true);

	private final int glFormat;
	private final GlVersion minimumGlVersion;
	private final boolean isInteger;

	PixelFormat(int glFormat, GlVersion minimumGlVersion, boolean isInteger) {
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.isInteger = isInteger;
	}

	public static Optional<PixelFormat> fromString(String name) {
		try {
			return Optional.of(PixelFormat.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlFormat() {
		return glFormat;
	}

	public GlVersion getMinimumGlVersion() {
		return minimumGlVersion;
	}

	public boolean isInteger() {
		return isInteger;
	}
}
