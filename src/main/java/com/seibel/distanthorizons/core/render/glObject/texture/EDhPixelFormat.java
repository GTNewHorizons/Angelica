package com.seibel.distanthorizons.core.render.glObject.texture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

import java.util.Locale;
import java.util.Optional;

public enum EDhPixelFormat
{
	RED(GL11C.GL_RED, EGlVersion.GL_11, false),
	RG(GL30C.GL_RG, EGlVersion.GL_30, false),
	RGB(GL11C.GL_RGB, EGlVersion.GL_11, false),
	BGR(GL12C.GL_BGR, EGlVersion.GL_12, false),
	RGBA(GL11C.GL_RGBA, EGlVersion.GL_11, false),
	BGRA(GL12C.GL_BGRA, EGlVersion.GL_12, false),
	RED_INTEGER(GL30C.GL_RED_INTEGER, EGlVersion.GL_30, true),
	RG_INTEGER(GL30C.GL_RG_INTEGER, EGlVersion.GL_30, true),
	RGB_INTEGER(GL30C.GL_RGB_INTEGER, EGlVersion.GL_30, true),
	BGR_INTEGER(GL30C.GL_BGR_INTEGER, EGlVersion.GL_30, true),
	RGBA_INTEGER(GL30C.GL_RGBA_INTEGER, EGlVersion.GL_30, true),
	BGRA_INTEGER(GL30C.GL_BGRA_INTEGER, EGlVersion.GL_30, true);
	
	
	
	private final int glFormat;
	private final EGlVersion minimumGlVersion;
	private final boolean isInteger;
	
	
	
	EDhPixelFormat(int glFormat, EGlVersion minimumGlVersion, boolean isInteger)
	{
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.isInteger = isInteger;
	}
	
	
	
	public static Optional<EDhPixelFormat> fromString(String name)
	{
		try
		{
			return Optional.of(EDhPixelFormat.valueOf(name.toUpperCase(Locale.US)));
		}
		catch (IllegalArgumentException e)
		{
			return Optional.empty();
		}
	}
	
	public int getGlFormat() { return glFormat; }
	
	public EGlVersion getMinimumGlVersion() { return minimumGlVersion; }
	
	public boolean isInteger() { return isInteger; }
	
}
