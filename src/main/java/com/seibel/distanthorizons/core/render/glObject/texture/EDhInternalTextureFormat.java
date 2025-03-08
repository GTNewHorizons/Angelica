package com.seibel.distanthorizons.core.render.glObject.texture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;

import java.util.Locale;
import java.util.Optional;

public enum EDhInternalTextureFormat
{
	// Default
	/** TODO: This technically shouldn't be exposed to shaders since it's not in the specification, it's the default anyways */
	RGBA(GL11C.GL_RGBA, EGlVersion.GL_11, EDhPixelFormat.RGBA),
	
	// 8-bit normalized
	R8(GL30C.GL_R8, EGlVersion.GL_30, EDhPixelFormat.RED),
	RG8(GL30C.GL_RG8, EGlVersion.GL_30, EDhPixelFormat.RG),
	RGB8(GL11C.GL_RGB8, EGlVersion.GL_11, EDhPixelFormat.RGB),
	RGBA8(GL11C.GL_RGBA8, EGlVersion.GL_11, EDhPixelFormat.RGBA),
	
	// 8-bit signed normalized
	R8_SNORM(GL31C.GL_R8_SNORM, EGlVersion.GL_31, EDhPixelFormat.RED),
	RG8_SNORM(GL31C.GL_RG8_SNORM, EGlVersion.GL_31, EDhPixelFormat.RG),
	RGB8_SNORM(GL31C.GL_RGB8_SNORM, EGlVersion.GL_31, EDhPixelFormat.RGB),
	RGBA8_SNORM(GL31C.GL_RGBA8_SNORM, EGlVersion.GL_31, EDhPixelFormat.RGBA),
	
	// 16-bit normalized
	R16(GL30C.GL_R16, EGlVersion.GL_30, EDhPixelFormat.RED),
	RG16(GL30C.GL_RG16, EGlVersion.GL_30, EDhPixelFormat.RG),
	RGB16(GL11C.GL_RGB16, EGlVersion.GL_11, EDhPixelFormat.RGB),
	RGBA16(GL11C.GL_RGBA16, EGlVersion.GL_11, EDhPixelFormat.RGBA),
	
	// 16-bit signed normalized
	R16_SNORM(GL31C.GL_R16_SNORM, EGlVersion.GL_31, EDhPixelFormat.RED),
	RG16_SNORM(GL31C.GL_RG16_SNORM, EGlVersion.GL_31, EDhPixelFormat.RG),
	RGB16_SNORM(GL31C.GL_RGB16_SNORM, EGlVersion.GL_31, EDhPixelFormat.RGB),
	RGBA16_SNORM(GL31C.GL_RGBA16_SNORM, EGlVersion.GL_31, EDhPixelFormat.RGBA),
	
	// 16-bit float
	R16F(GL30C.GL_R16F, EGlVersion.GL_30, EDhPixelFormat.RED),
	RG16F(GL30C.GL_RG16F, EGlVersion.GL_30, EDhPixelFormat.RG),
	RGB16F(GL30C.GL_RGB16F, EGlVersion.GL_30, EDhPixelFormat.RGB),
	RGBA16F(GL30C.GL_RGBA16F, EGlVersion.GL_30, EDhPixelFormat.RGBA),
	
	// 32-bit float
	R32F(GL30C.GL_R32F, EGlVersion.GL_30, EDhPixelFormat.RED),
	RG32F(GL30C.GL_RG32F, EGlVersion.GL_30, EDhPixelFormat.RG),
	RGB32F(GL30C.GL_RGB32F, EGlVersion.GL_30, EDhPixelFormat.RGB),
	RGBA32F(GL30C.GL_RGBA32F, EGlVersion.GL_30, EDhPixelFormat.RGBA),
	
	// 8-bit integer
	R8I(GL30C.GL_R8I, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG8I(GL30C.GL_RG8I, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB8I(GL30C.GL_RGB8I, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA8I(GL30C.GL_RGBA8I, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// 8-bit unsigned integer
	R8UI(GL30C.GL_R8UI, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG8UI(GL30C.GL_RG8UI, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB8UI(GL30C.GL_RGB8UI, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA8UI(GL30C.GL_RGBA8UI, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// 16-bit integer
	R16I(GL30C.GL_R16I, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG16I(GL30C.GL_RG16I, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB16I(GL30C.GL_RGB16I, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA16I(GL30C.GL_RGBA16I, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// 16-bit unsigned integer
	R16UI(GL30C.GL_R16UI, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG16UI(GL30C.GL_RG16UI, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB16UI(GL30C.GL_RGB16UI, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA16UI(GL30C.GL_RGBA16UI, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// 32-bit integer
	R32I(GL30C.GL_R32I, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG32I(GL30C.GL_RG32I, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB32I(GL30C.GL_RGB32I, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA32I(GL30C.GL_RGBA32I, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// 32-bit unsigned integer
	R32UI(GL30C.GL_R32UI, EGlVersion.GL_30, EDhPixelFormat.RED_INTEGER),
	RG32UI(GL30C.GL_RG32UI, EGlVersion.GL_30, EDhPixelFormat.RG_INTEGER),
	RGB32UI(GL30C.GL_RGB32UI, EGlVersion.GL_30, EDhPixelFormat.RGB_INTEGER),
	RGBA32UI(GL30C.GL_RGBA32UI, EGlVersion.GL_30, EDhPixelFormat.RGBA_INTEGER),
	
	// Mixed
	R3_G3_B2(GL11C.GL_R3_G3_B2, EGlVersion.GL_11, EDhPixelFormat.RGB),
	RGB5_A1(GL11C.GL_RGB5_A1, EGlVersion.GL_11, EDhPixelFormat.RGBA),
	RGB10_A2(GL11C.GL_RGB10_A2, EGlVersion.GL_11, EDhPixelFormat.RGBA),
	R11F_G11F_B10F(GL30C.GL_R11F_G11F_B10F, EGlVersion.GL_30, EDhPixelFormat.RGB),
	RGB9_E5(GL30C.GL_RGB9_E5, EGlVersion.GL_30, EDhPixelFormat.RGB);
	
	
	
	private final int glFormat;
	private final EGlVersion minimumGlVersion;
	private final EDhPixelFormat expectedPixelFormat;
	
	
	
	EDhInternalTextureFormat(int glFormat, EGlVersion minimumGlVersion, EDhPixelFormat expectedPixelFormat)
	{
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.expectedPixelFormat = expectedPixelFormat;
	}
	
	
	
	public static Optional<EDhInternalTextureFormat> fromString(String name)
	{
		try
		{
			return Optional.of(EDhInternalTextureFormat.valueOf(name.toUpperCase(Locale.US)));
		}
		catch (IllegalArgumentException e)
		{
			return Optional.empty();
		}
	}
	
	public int getGlFormat() { return glFormat; }
	
	public EDhPixelFormat getPixelFormat() { return expectedPixelFormat; }
	
	public EGlVersion getMinimumGlVersion() { return minimumGlVersion; }
}
