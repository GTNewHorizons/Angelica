package com.seibel.distanthorizons.core.render.glObject.texture;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

public enum EDhDepthBufferFormat
{
	DEPTH(false),
	DEPTH16(false),
	DEPTH24(false),
	DEPTH32(false),
	DEPTH32F(false),
	DEPTH_STENCIL(true),
	DEPTH24_STENCIL8(true),
	DEPTH32F_STENCIL8(true);
	
	
	
	private final boolean combinedStencil;
	
	EDhDepthBufferFormat(boolean combinedStencil) { this.combinedStencil = combinedStencil; }
	
	
	
	@Nullable
	public static EDhDepthBufferFormat fromGlEnum(int glenum)
	{
		switch (glenum)
		{
			case GL30C.GL_DEPTH_COMPONENT:
				return EDhDepthBufferFormat.DEPTH;
			case GL30C.GL_DEPTH_COMPONENT16:
				return EDhDepthBufferFormat.DEPTH16;
			case GL30C.GL_DEPTH_COMPONENT24:
				return EDhDepthBufferFormat.DEPTH24;
			case GL30C.GL_DEPTH_COMPONENT32:
				return EDhDepthBufferFormat.DEPTH32;
			case GL30C.GL_DEPTH_COMPONENT32F:
				return EDhDepthBufferFormat.DEPTH32F;
			case GL30C.GL_DEPTH_STENCIL:
				return EDhDepthBufferFormat.DEPTH_STENCIL;
			case GL30C.GL_DEPTH24_STENCIL8:
				return EDhDepthBufferFormat.DEPTH24_STENCIL8;
			case GL30C.GL_DEPTH32F_STENCIL8:
				return EDhDepthBufferFormat.DEPTH32F_STENCIL8;
			default:
				return null;
		}
	}
	
	public static EDhDepthBufferFormat fromGlEnumOrDefault(int glenum)
	{
		EDhDepthBufferFormat format = fromGlEnum(glenum);
		if (format == null)
		{
			// yolo, just assume it's GL_DEPTH_COMPONENT
			return EDhDepthBufferFormat.DEPTH;
		}
		return format;
	}
	
	public int getGlInternalFormat()
	{
		switch (this)
		{
			case DEPTH:
				return GL30C.GL_DEPTH_COMPONENT;
			case DEPTH16:
				return GL30C.GL_DEPTH_COMPONENT16;
			case DEPTH24:
				return GL30C.GL_DEPTH_COMPONENT24;
			case DEPTH32:
				return GL30C.GL_DEPTH_COMPONENT32;
			case DEPTH32F:
				return GL30C.GL_DEPTH_COMPONENT32F;
			case DEPTH_STENCIL:
				return GL30C.GL_DEPTH_STENCIL;
			case DEPTH24_STENCIL8:
				return GL30C.GL_DEPTH24_STENCIL8;
			case DEPTH32F_STENCIL8:
				return GL30C.GL_DEPTH32F_STENCIL8;
		}
		
		throw new AssertionError("unreachable");
	}
	
	public int getGlType() { return isCombinedStencil() ? GL30C.GL_DEPTH_STENCIL : GL30C.GL_DEPTH_COMPONENT; }
	
	public int getGlFormat()
	{
		switch (this)
		{
			case DEPTH:
			case DEPTH16:
				return GL43C.GL_UNSIGNED_SHORT;
			case DEPTH24:
			case DEPTH32:
				return GL43C.GL_UNSIGNED_INT;
			case DEPTH32F:
				return GL30C.GL_FLOAT;
			case DEPTH_STENCIL:
			case DEPTH24_STENCIL8:
				return GL30C.GL_UNSIGNED_INT_24_8;
			case DEPTH32F_STENCIL8:
				return GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
		}
		
		throw new AssertionError("unreachable");
	}
	
	public boolean isCombinedStencil() { return combinedStencil; }
	
}
