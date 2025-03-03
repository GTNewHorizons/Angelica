/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.lwjgl.opengl.GL32;

// TODO make this Closable or AutoClosable so it can be used with try-resource blocks
public class GLState
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	private static final int FBO_MAX = 4;
	
	public int program;
	public int vao;
	public int vbo;
	public int ebo;
	public int[] fbo;
	public int texture2D;
	/** IE: GL_TEXTURE0, GL_TEXTURE1, etc. */
	public int activeTextureNumber;
	public int texture0;
	public int texture1;
	public int texture2;
	public int texture3;
	public int frameBufferTexture0;
	public int frameBufferTexture1;
	public int frameBufferDepthTexture;
	public boolean blend;
	public boolean scissor;
	public int blendEqRGB;
	public int blendEqAlpha;
	public int blendSrcColor;
	public int blendSrcAlpha;
	public int blendDstColor;
	public int blendDstAlpha;
	public boolean depth;
	public boolean writeToDepthBuffer;
	public int depthFunc;
	//public boolean stencil;
	//public int stencilFunc;
	//public int stencilRef;
	//public int stencilMask;
	public int[] view;
	public boolean cull;
	public int cullMode;
	public int polyMode;
	
	
	
	public GLState() 
	{
		this.fbo = new int[FBO_MAX];
		
		this.saveState();
	}
	
	public void saveState()
	{
		this.program = GL32.glGetInteger(GL32.GL_CURRENT_PROGRAM);
		this.vao = GL32.glGetInteger(GL32.GL_VERTEX_ARRAY_BINDING);
		this.vbo = GL32.glGetInteger(GL32.GL_ARRAY_BUFFER_BINDING);
		this.ebo = GL32.glGetInteger(GL32.GL_ELEMENT_ARRAY_BUFFER_BINDING);
		
		GL32.glGetIntegerv(GL32.GL_FRAMEBUFFER_BINDING, this.fbo);
		
		this.texture2D = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		this.activeTextureNumber = GL32.glGetInteger(GL32.GL_ACTIVE_TEXTURE);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		this.texture0 = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		this.texture1 = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE2); // problem with Iris
		this.texture2 = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE3);
		this.texture3 = GL32.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
		
		GLMC.glActiveTexture(this.activeTextureNumber);
		
		this.frameBufferTexture0 = GL32.glGetFramebufferAttachmentParameteri(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		this.frameBufferTexture1 = GL32.glGetFramebufferAttachmentParameteri(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT1, GL32.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		this.frameBufferDepthTexture = GL32.glGetFramebufferAttachmentParameteri(GL32.GL_FRAMEBUFFER, GL32.GL_DEPTH_ATTACHMENT, GL32.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
		
		this.blend = GL32.glIsEnabled(GL32.GL_BLEND);
		this.scissor = GL32.glIsEnabled(GL32.GL_SCISSOR_TEST);
		this.blendEqRGB = GL32.glGetInteger(GL32.GL_BLEND_EQUATION_RGB);
		this.blendEqAlpha = GL32.glGetInteger(GL32.GL_BLEND_EQUATION_ALPHA);
		this.blendSrcColor = GL32.glGetInteger(GL32.GL_BLEND_SRC_RGB);
		this.blendSrcAlpha = GL32.glGetInteger(GL32.GL_BLEND_SRC_ALPHA);
		this.blendDstColor = GL32.glGetInteger(GL32.GL_BLEND_DST_RGB);
		this.blendDstAlpha = GL32.glGetInteger(GL32.GL_BLEND_DST_ALPHA);
		this.depth = GL32.glIsEnabled(GL32.GL_DEPTH_TEST);
		this.writeToDepthBuffer = GL32.glGetInteger(GL32.GL_DEPTH_WRITEMASK) == GL32.GL_TRUE;
		this.depthFunc = GL32.glGetInteger(GL32.GL_DEPTH_FUNC);
		//this.stencil = GL32.glIsEnabled(GL32.GL_STENCIL_TEST);
		//this.stencilFunc = GL32.glGetInteger(GL32.GL_STENCIL_FUNC);
		//this.stencilRef = GL32.glGetInteger(GL32.GL_STENCIL_REF);
		//this.stencilMask = GL32.glGetInteger(GL32.GL_STENCIL_VALUE_MASK);
		this.view = new int[4];
		GL32.glGetIntegerv(GL32.GL_VIEWPORT, this.view);
		this.cull = GL32.glIsEnabled(GL32.GL_CULL_FACE);
		this.cullMode = GL32.glGetInteger(GL32.GL_CULL_FACE_MODE);
		this.polyMode = GL32.glGetInteger(GL32.GL_POLYGON_MODE);
	}
	
	@Override
	public String toString()
	{
		return "GLState{" +
				"program=" + this.program + ", vao=" + this.vao + ", vbo=" + this.vbo + ", ebo=" + this.ebo + ", fbo=" + this.fbo[0] +
				", text=" + GLEnums.getString(this.texture2D) + "@" + this.activeTextureNumber + ", text0=" + GLEnums.getString(this.texture0) +
				", FB text0=" + this.frameBufferTexture0 +
				", FB text1=" + this.frameBufferTexture1 +
				", FB depth=" + this.frameBufferDepthTexture +
				", blend=" + this.blend + ", scissor=" + this.scissor + ", blendMode=" + GLEnums.getString(this.blendSrcColor) + "," + GLEnums.getString(this.blendDstColor) +
				", depth=" + this.depth +
				//", depthFunc=" + GLEnums.getString(this.depthFunc) + ", stencil=" + this.stencil + ", stencilFunc=" +
				//GLEnums.getString(this.stencilFunc) + ", stencilRef=" + this.stencilRef + ", stencilMask=" + this.stencilMask +
				", view={x:" + this.view[0] + ", y:" + this.view[1] +
				", w:" + this.view[2] + ", h:" + this.view[3] + "}" + ", cull=" + this.cull + ", cullMode="
				+ GLEnums.getString(this.cullMode) + ", polyMode=" + GLEnums.getString(this.polyMode) +
				'}';
	}
	
	public void RestoreFrameBuffer()
	{
		// explicitly unbinding the frame buffer is necessary to prevent GL_CLEAR calls from hitting the wrong buffer
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, 0);
		
		for (int i = 0; i < FBO_MAX; i++)
		{
			int buffer = this.fbo[i];
			if (i > 0 && buffer == 0) break;
			
			GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, GL32.glIsFramebuffer(buffer) ? buffer : 0);
		}
	}
	
	public void restore()
	{
		this.RestoreFrameBuffer();
		
		if (this.blend)
		{
			GLMC.enableBlend();
		}
		else
		{
			GLMC.disableBlend();
		}
		
		if (this.scissor)
		{
			GLMC.enableScissorTest();
		}
		else
		{
			GLMC.disableScissorTest();
		}
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(GL32.glIsTexture(this.texture0) ? this.texture0 : 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(GL32.glIsTexture(this.texture1) ? this.texture1 : 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE2);
		GLMC.glBindTexture(GL32.glIsTexture(this.texture2) ? this.texture2 : 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE3);
		GLMC.glBindTexture(GL32.glIsTexture(this.texture3) ? this.texture3 : 0);
		
		GLMC.glActiveTexture(this.activeTextureNumber);
		GLMC.glBindTexture(GL32.glIsTexture(this.texture2D) ? this.texture2D : 0);
		
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_TEXTURE_2D, this.frameBufferTexture0, 0);
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT1, GL32.GL_TEXTURE_2D, this.frameBufferTexture1, 0);
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_DEPTH_ATTACHMENT, GL32.GL_TEXTURE_2D, this.frameBufferDepthTexture, 0);
		
		GL32.glBindVertexArray(GL32.glIsVertexArray(this.vao) ? this.vao : 0);
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, GL32.glIsBuffer(this.vbo) ? this.vbo : 0);
		GL32.glBindBuffer(GL32.GL_ELEMENT_ARRAY_BUFFER, GL32.glIsBuffer(this.ebo) ? this.ebo: 0);
		GL32.glUseProgram(GL32.glIsProgram(this.program) ? this.program : 0);
		
		if (this.writeToDepthBuffer)
		{
			GLMC.enableDepthMask();
		}
		else
		{
			GLMC.disableDepthMask();
		}
		
		GLMC.glBlendFunc(this.blendSrcColor, this.blendDstColor);
		GL32.glBlendEquationSeparate(this.blendEqRGB, this.blendEqAlpha);
		GLMC.glBlendFuncSeparate(this.blendSrcColor, this.blendDstColor, this.blendSrcAlpha, this.blendDstAlpha);
		
		if (this.depth)
		{
			GLMC.enableDepthTest();
		}
		else
		{
			GLMC.disableDepthTest();
		}
		GLMC.glDepthFunc(this.depthFunc);
		
		//if (this.stencil)
		//{
		//	GL32.glEnable(GL32.GL_STENCIL_TEST);
		//}
		//else
		//{
		//	GL32.glDisable(GL32.GL_STENCIL_TEST);
		//}
		//GL32.glStencilFunc(this.stencilFunc, this.stencilRef, this.stencilMask);
		
		GL32.glViewport(this.view[0], this.view[1], this.view[2], this.view[3]);
		if (this.cull)
		{
			GLMC.enableFaceCulling();
		}
		else
		{
			GLMC.disableFaceCulling();
		}
		GL32.glCullFace(this.cullMode);
		GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, this.polyMode);
	}
}
