package com.seibel.distanthorizons.core.render.glObject.texture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

import java.util.Locale;
import java.util.Optional;

public enum EDhPixelType
{
	BYTE(GL11C.GL_BYTE, EGlVersion.GL_11),
	SHORT(GL11C.GL_SHORT, EGlVersion.GL_11),
	INT(GL11C.GL_INT, EGlVersion.GL_11),
	HALF_FLOAT(GL30C.GL_HALF_FLOAT, EGlVersion.GL_30),
	FLOAT(GL11C.GL_FLOAT, EGlVersion.GL_11),
	UNSIGNED_BYTE(GL11C.GL_UNSIGNED_BYTE, EGlVersion.GL_11),
	UNSIGNED_BYTE_3_3_2(GL12C.GL_UNSIGNED_BYTE_3_3_2, EGlVersion.GL_12),
	UNSIGNED_BYTE_2_3_3_REV(GL12C.GL_UNSIGNED_BYTE_2_3_3_REV, EGlVersion.GL_12),
	UNSIGNED_SHORT(GL11C.GL_UNSIGNED_SHORT, EGlVersion.GL_11),
	UNSIGNED_SHORT_5_6_5(GL12C.GL_UNSIGNED_SHORT_5_6_5, EGlVersion.GL_12),
	UNSIGNED_SHORT_5_6_5_REV(GL12C.GL_UNSIGNED_SHORT_5_6_5_REV, EGlVersion.GL_12),
	UNSIGNED_SHORT_4_4_4_4(GL12C.GL_UNSIGNED_SHORT_4_4_4_4, EGlVersion.GL_12),
	UNSIGNED_SHORT_4_4_4_4_REV(GL12C.GL_UNSIGNED_SHORT_4_4_4_4_REV, EGlVersion.GL_12),
	UNSIGNED_SHORT_5_5_5_1(GL12C.GL_UNSIGNED_SHORT_5_5_5_1, EGlVersion.GL_12),
	UNSIGNED_SHORT_1_5_5_5_REV(GL12C.GL_UNSIGNED_SHORT_1_5_5_5_REV, EGlVersion.GL_12),
	UNSIGNED_INT(GL11C.GL_UNSIGNED_INT, EGlVersion.GL_11),
	UNSIGNED_INT_8_8_8_8(GL12C.GL_UNSIGNED_INT_8_8_8_8, EGlVersion.GL_12),
	UNSIGNED_INT_8_8_8_8_REV(GL12C.GL_UNSIGNED_INT_8_8_8_8_REV, EGlVersion.GL_12),
	UNSIGNED_INT_10_10_10_2(GL12C.GL_UNSIGNED_INT_10_10_10_2, EGlVersion.GL_12),
	UNSIGNED_INT_2_10_10_10_REV(GL12C.GL_UNSIGNED_INT_2_10_10_10_REV, EGlVersion.GL_12);
	
	
	
	private final int glFormat;
	private final EGlVersion minimumGlVersion;
	
	
	
	EDhPixelType(int glFormat, EGlVersion minimumGlVersion)
	{
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
	}
	
	
	
	public static Optional<EDhPixelType> fromString(String name)
	{
		try
		{
			return Optional.of(EDhPixelType.valueOf(name.toUpperCase(Locale.US)));
		}
		catch (IllegalArgumentException e)
		{
			return Optional.empty();
		}
	}
	
	public int getGlFormat() { return glFormat; }
	
	public EGlVersion getMinimumGlVersion() { return minimumGlVersion; }
	
}
