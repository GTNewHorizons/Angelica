package com.seibel.distanthorizons.core.render.glObject.texture;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.lwjgl.opengl.GL32;

public class DhFramebuffer implements IDhApiFramebuffer
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;
	private int id;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhFramebuffer() 
	{
		this.id = GL32.glGenFramebuffers();

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = GL32.glGetInteger(GL32.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = GL32.glGetInteger(GL32.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	/** For internal use by Iris, do not remove. */
	public DhFramebuffer(int id) 
	{
		this.id = id;
		
		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = GL32.glGetInteger(GL32.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = GL32.glGetInteger(GL32.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void addDepthAttachment(int textureId, boolean isCombinedStencil) 
	{
		this.bind();
		
		int depthAttachment = isCombinedStencil ? GL32.GL_DEPTH_STENCIL_ATTACHMENT : GL32.GL_DEPTH_ATTACHMENT;
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, depthAttachment, GL32.GL_TEXTURE_2D, textureId, 0);
		
		this.hasDepthAttachment = true;
	}
	
	@Override
	public void addColorAttachment(int textureIndex, int textureId)
	{
		this.bind();
		
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0 + textureIndex, GL32.GL_TEXTURE_2D, textureId, 0);
		this.attachments.put(textureIndex, textureId);
	}

	public void noDrawBuffers()
	{
		this.bind(); 
		GL32.glDrawBuffers(new int[]{GL32.GL_NONE});
	}
	
	public void drawBuffers(int[] buffers)
	{
		int[] glBuffers = new int[buffers.length]; 
		int index = 0;
		
		if (buffers.length > this.maxDrawBuffers)
		{
			throw new IllegalArgumentException("Cannot write to more than " + this.maxDrawBuffers + " draw buffers on this GPU");
		}
		
		for (int buffer : buffers)
		{
			if (buffer >= this.maxColorAttachments)
			{
				throw new IllegalArgumentException("Only " + this.maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
			}
			
			glBuffers[index++] = GL32.GL_COLOR_ATTACHMENT0 + buffer;
		}
		
		this.bind(); 
		GL32.glDrawBuffers(new int[]{GL32.GL_NONE});
	}
	
	public void readBuffer(int buffer)
	{
		this.bind();
		GL32.glReadBuffer(GL32.GL_COLOR_ATTACHMENT0 + buffer);
	}
	
	public int getColorAttachment(int index) { return this.attachments.get(index); }
	
	public boolean hasDepthAttachment() { return this.hasDepthAttachment; }
	
	@Override
	public void bind()
	{
		if (this.id == -1)
		{
			throw new IllegalStateException("Framebuffer does not exist!");
		} 
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.id);
	}
	
	public void bindAsReadBuffer() { GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, this.id); }
	
	public void bindAsDrawBuffer() { GLMC.glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, this.id); }
	
	@Override
	public void destroy()
	{
		GL32.glDeleteFramebuffers(this.id); 
		this.id = -1;
	}
	
	@Override
	public int getStatus()
	{
		this.bind(); 
		int status = GL32.glCheckFramebufferStatus(GL32.GL_FRAMEBUFFER);
		return status;
	}
	
	@Override
	public int getId() { return this.id; }
	
	
	
	//=============//
	// API methods //
	//=============//
	
	public boolean overrideThisFrame() { return true; }
	
}
