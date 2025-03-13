package com.seibel.distanthorizons.core.render.glObject.texture;

import org.joml.Vector2i;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;

public class DhColorTexture
{
	private final EDhInternalTextureFormat internalFormat;
	private final EDhPixelFormat format;
	private final EDhPixelType type;
	private int width;
	private int height;

	private boolean isValid;
	/** AKA, the OpenGL name of this texture */
	private final int id;

	private static final ByteBuffer NULL_BUFFER = null;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhColorTexture(Builder builder)
	{
		this.isValid = true;
		
		this.internalFormat = builder.internalFormat;
		this.format = builder.format;
		this.type = builder.type;
		
		this.width = builder.width;
		this.height = builder.height;
		
		this.id = GL43C.glGenTextures();
		
		boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
		this.setupTexture(this.id, builder.width, builder.height, !isPixelFormatInteger);
		
		// Clean up after ourselves
		// This is strictly defensive to ensure that other buggy code doesn't tamper with our textures
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	private void setupTexture(int id, int width, int height, boolean allowsLinear)
	{
		this.resizeTexture(id, width, height);
		
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
	}
	
	private void resizeTexture(int texture, int width, int height)
	{
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, texture);
		GL43C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, this.internalFormat.getGlFormat(), width, height, 0, this.format.getGlFormat(), this.type.getGlFormat(), NULL_BUFFER);
	}
	
	void resize(Vector2i textureScaleOverride) { this.resize(textureScaleOverride.x, textureScaleOverride.y); }
	
	// Package private, call CompositeRenderTargets#resizeIfNeeded instead.
	public void resize(int width, int height)
	{
		this.throwIfInvalid();
		
		this.width = width;
		this.height = height;
		
		this.resizeTexture(this.id, width, height);
	}
	
	public EDhInternalTextureFormat getInternalFormat() { return this.internalFormat; }
	
	public int getTextureId()
	{
		this.throwIfInvalid();
		return this.id;
	}
	
	public int getWidth() { return this.width; }
	
	public int getHeight() { return this.height; }
	
	public void destroy()
	{
		this.throwIfInvalid();
		this.isValid = false;
		
		GL43C.glDeleteTextures(this.id);
	}
	
	/** @throws IllegalStateException if the texture isn't valid */
	private void throwIfInvalid()
	{
		if (!this.isValid)
		{
			throw new IllegalStateException("Attempted to use a deleted composite render target");
		}
	}
	
	public static Builder builder() { return new Builder(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class Builder
	{
		private EDhInternalTextureFormat internalFormat = EDhInternalTextureFormat.RGBA8;
		private int width = 0;
		private int height = 0;
		private EDhPixelFormat format = EDhPixelFormat.RGBA;
		private EDhPixelType type = EDhPixelType.UNSIGNED_BYTE;
		
		private Builder()
		{
			// No-op
		}
		
		public Builder setInternalFormat(EDhInternalTextureFormat format)
		{
			this.internalFormat = format;
			return this;
		}
		
		public Builder setDimensions(int width, int height)
		{
			if (width <= 0)
			{
				throw new IllegalArgumentException("Width must be greater than zero");
			}
			
			if (height <= 0)
			{
				throw new IllegalArgumentException("Height must be greater than zero");
			}
			
			this.width = width;
			this.height = height;
			
			return this;
		}
		
		public Builder setPixelFormat(EDhPixelFormat pixelFormat)
		{
			this.format = pixelFormat;
			return this;
		}
		
		public Builder setPixelType(EDhPixelType pixelType)
		{
			this.type = pixelType;
			return this;
		}
		
		public DhColorTexture build() { return new DhColorTexture(this); }
		
	}
}
