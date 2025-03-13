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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.mojang.blaze3d.platform.GlStateManager;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;

import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL32;


/**
 * <b>Why does DH often call GL methods twice? </b><br> 
 * Once using the base {@link GL32} function and a second time using
 * Minecraft's {@link GlStateManager}?<br><br>
 * 
 * <b>Answer: </b><br>
 * Compatibility and robustness<br>
 * In general all MC rendering should go through MC's {@link GlStateManager},
 * however that isn't always the case.
 * So, to prevent issues if a mod (or MC itself) calls a direct GL function
 * instead of the {@link GlStateManager} wrapper, we need to be sure about what the actual
 * set value is (whether setting or getting) and that MC knows what DH has done.
 * This way whether a mod (or MC) is using the {@link GlStateManager} or direct GL calls,
 * they should always have the correct value for anything DH has modified.
 * <br><br>
 * This may slow down some low end GPUs that are driver limited,
 * however James would rather have slow correct rendering vs fast broken rendering.
 */
public class MinecraftGLWrapper implements IMinecraftGLWrapper
{
	public static final MinecraftGLWrapper INSTANCE = new MinecraftGLWrapper();
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	/*
    private static final StencilState STENCIL;
	 */
	
	
	// scissor //
	
	/** @see GL32#GL_SCISSOR_TEST */
	@Override
	public void enableScissorTest() 
	{
		GL32.glEnable(GL32.GL_SCISSOR_TEST);
		GlStateManager._enableScissorTest(); 
	}
	/** @see GL32#GL_SCISSOR_TEST */
	@Override
	public void disableScissorTest() 
	{ 
		GL32.glDisable(GL32.GL_SCISSOR_TEST);
		GlStateManager._disableScissorTest(); 
	}
	
	
	// stencil //
//	
//	/** @see GL32#GL_SCISSOR_TEST */
//	public void enableScissorTest() { GlStateManager._stencilFunc(); }
//	/** @see GL32#GL_SCISSOR_TEST */
//	public void disableScissorTest() { GlStateManager._disableScissorTest(); }
	
	
	// depth //
	
	/** @see GL32#GL_DEPTH_TEST */
	@Override
	public void enableDepthTest() 
	{
		GL32.glEnable(GL32.GL_DEPTH_TEST);
		GlStateManager._enableDepthTest(); 
	}
	/** @see GL32#GL_DEPTH_TEST */
	@Override
	public void disableDepthTest() 
	{
		GL32.glDisable(GL32.GL_DEPTH_TEST);
		GlStateManager._disableDepthTest(); 
	}
	
	/** @see GL32#glDepthFunc(int)  */
	@Override
	public void glDepthFunc(int func) 
	{ 
		GL32.glDepthFunc(func);
		GlStateManager._depthFunc(func); 
	}
	
	/** @see GL32#glDepthMask(boolean) */
	@Override
	public void enableDepthMask() 
	{
		GL32.glDepthMask(true);
		GlStateManager._depthMask(true); 
	}
	/** @see GL32#glDepthMask(boolean) */
	@Override
	public void disableDepthMask() 
	{
		GL32.glDepthMask(false);
		GlStateManager._depthMask(false); 
	}
	
	
	// blending //
	
	/** @see GL32#GL_BLEND */
	@Override
	public void enableBlend() 
	{
		GL32.glEnable(GL32.GL_BLEND);
		GlStateManager._enableBlend();
	}
	/** @see GL32#GL_BLEND */
	@Override
	public void disableBlend() 
	{
		GL32.glDisable(GL32.GL_BLEND);
		GlStateManager._disableBlend(); 
	}
	
	/** @see GL32#glBlendFunc */
	@Override
	public void glBlendFunc(int sfactor, int dfactor) 
	{
		GL32.glBlendFunc(sfactor, dfactor);
		GlStateManager._blendFunc(sfactor, dfactor); 
	}
	/** @see GL32#glBlendFuncSeparate */
	@Override
	public void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) 
	{
		GL32.glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
		GlStateManager._blendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha); 
	}
	
	
	// frame buffers //
	
	/** @see GL32#glBindFramebuffer */
	@Override
	public void glBindFramebuffer(int target, int framebuffer) 
	{
		GL32.glBindFramebuffer(target, framebuffer);
		GlStateManager._glBindFramebuffer(target, framebuffer); 
	}
	
	
	// buffers //
	
	/** @see GL32#glGenBuffers() */
	@Override
	public int glGenBuffers()
	{ return GlStateManager._glGenBuffers(); }
	
	/** @see GL32#glDeleteBuffers(int)  */
	@Override
	public void glDeleteBuffers(int buffer)
	{ GlStateManager._glDeleteBuffers(buffer); }
	
	
	// culling //
	
	/** @see GL32#GL_CULL_FACE */
	@Override
	public void enableFaceCulling() 
	{
		GL32.glEnable(GL32.GL_CULL_FACE);
		GlStateManager._enableCull(); 
	}
	/** @see GL32#GL_CULL_FACE */
	@Override
	public void disableFaceCulling() 
	{
		GL32.glDisable(GL32.GL_CULL_FACE);
		GlStateManager._disableCull(); 
	}
	
	
	// textures //
	
	/** @see GL32#glGenTextures() */
	@Override
	public int glGenTextures() { return GlStateManager._genTexture(); }
	/** @see GL32#glDeleteTextures(int) */
	@Override
	public void glDeleteTextures(int texture) { GlStateManager._deleteTexture(texture); }
	
	/** @see GL32#glActiveTexture(int) */
	@Override
	public void glActiveTexture(int textureId) 
	{ 
		GL32.glActiveTexture(textureId);
		GlStateManager._activeTexture(textureId);
	}
	@Override
	public int getActiveTexture() { return GL32.glGetInteger(GL32.GL_ACTIVE_TEXTURE); }
	
	/**
	 * Always binds to {@link GL32#GL_TEXTURE_2D}
	 * @see GL32#glBindTexture(int, int)
	 */
	@Override
	public void glBindTexture(int texture) 
	{
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, texture);
		GlStateManager._bindTexture(texture);
	}
	
	
	
	
}
