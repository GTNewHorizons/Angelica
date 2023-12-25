package net.coderbot.iris.gl.texture;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public enum DepthBufferFormat {
	DEPTH(false),
	DEPTH16(false),
	DEPTH24(false),
	DEPTH32(false),
	DEPTH32F(false),
	DEPTH_STENCIL(true),
	DEPTH24_STENCIL8(true),
	DEPTH32F_STENCIL8(true);

	private final boolean combinedStencil;

	DepthBufferFormat(boolean combinedStencil) {
		this.combinedStencil = combinedStencil;
	}

	@Nullable
	public static DepthBufferFormat fromGlEnum(int glenum) {
        return switch (glenum) {
            case GL11.GL_DEPTH_COMPONENT -> DepthBufferFormat.DEPTH;
            case GL14.GL_DEPTH_COMPONENT16 -> DepthBufferFormat.DEPTH16;
            case GL14.GL_DEPTH_COMPONENT24 -> DepthBufferFormat.DEPTH24;
            case GL14.GL_DEPTH_COMPONENT32 -> DepthBufferFormat.DEPTH32;
            case GL30.GL_DEPTH_COMPONENT32F -> DepthBufferFormat.DEPTH32F;
            case GL30.GL_DEPTH_STENCIL -> DepthBufferFormat.DEPTH_STENCIL;
            case GL30.GL_DEPTH24_STENCIL8 -> DepthBufferFormat.DEPTH24_STENCIL8;
            case GL30.GL_DEPTH32F_STENCIL8 -> DepthBufferFormat.DEPTH32F_STENCIL8;
            default -> null;
        };
	}

	public static DepthBufferFormat fromGlEnumOrDefault(int glenum) {
		DepthBufferFormat format = fromGlEnum(glenum);
		if (format == null) {
			// yolo, just assume it's GL_DEPTH_COMPONENT
			return DepthBufferFormat.DEPTH;
		}
		return format;
	}

	public int getGlInternalFormat() {
        return switch (this) {
            case DEPTH -> GL11.GL_DEPTH_COMPONENT;
            case DEPTH16 -> GL14.GL_DEPTH_COMPONENT16;
            case DEPTH24 -> GL14.GL_DEPTH_COMPONENT24;
            case DEPTH32 -> GL14.GL_DEPTH_COMPONENT32;
            case DEPTH32F -> GL30.GL_DEPTH_COMPONENT32F;
            case DEPTH_STENCIL -> GL30.GL_DEPTH_STENCIL;
            case DEPTH24_STENCIL8 -> GL30.GL_DEPTH24_STENCIL8;
            case DEPTH32F_STENCIL8 -> GL30.GL_DEPTH32F_STENCIL8;
        };

    }

	public int getGlType() {
		return isCombinedStencil() ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT;
	}

	public int getGlFormat() {
        return switch (this) {
            case DEPTH, DEPTH16 -> GL11.GL_UNSIGNED_SHORT;
            case DEPTH24, DEPTH32 -> GL11.GL_UNSIGNED_INT;
            case DEPTH32F -> GL11.GL_FLOAT;
            case DEPTH_STENCIL, DEPTH24_STENCIL8 -> GL30.GL_UNSIGNED_INT_24_8;
            case DEPTH32F_STENCIL8 -> GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
        };

    }

	public boolean isCombinedStencil() {
		return combinedStencil;
	}
}
