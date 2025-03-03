package com.seibel.distanthorizons.core.render.glObject.texture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;

public class DHDepthTexture
{
	private int id;
	public DHDepthTexture(int width, int height, EDhDepthBufferFormat format)
	{
		this.id = GL43C.glGenTextures();
		
		resize(width, height, format);
		
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
		GL43C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
		
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
	}
	
	// For internal use by Iris for copying data. Do not use this in DH.
	public DHDepthTexture(int id) {
		this.id = id;
	}
	
	public void resize(int width, int height, EDhDepthBufferFormat format)
	{
		GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, getTextureId());
		GL43C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), width, height, 0,
				format.getGlType(), format.getGlFormat(), (ByteBuffer) null);
	}
	
	public int getTextureId()
	{
		if (id == -1) throw new IllegalStateException("Depth texture does not exist!");
		return id;
	}
	
	public void destroy()
	{
		GL43C.glDeleteTextures(getTextureId());
		this.id = -1;
	}
	
}
