/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General License for more details.
 *
 *    You should have received a copy of the GNU Lesser General License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import org.lwjgl.opengl.GL32;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Used to sync GL state changes between DH and MC.
 * This is specifically important for other mods that change MC's rendering like Iris.
 */
public interface IMinecraftGLWrapper extends IBindable
{
	
	// scissor //
	
	/** @see GL32#GL_SCISSOR_TEST */
	void enableScissorTest();
	/** @see GL32#GL_SCISSOR_TEST */
	void disableScissorTest();
	
	
	// stencil //
	
	///** @see GL32#GL_SCISSOR_TEST */
	//void enableScissorTest() { GlStateManager._enableScissorTest(); }
	///** @see GL32#GL_SCISSOR_TEST */
	//void disableScissorTest() { GlStateManager._disableScissorTest(); }
	
	
	// depth //
	
	/** @see GL32#GL_DEPTH_TEST */
	void enableDepthTest();
	/** @see GL32#GL_DEPTH_TEST */
	void disableDepthTest();
	
	/** @see GL32#glDepthFunc(int)  */
	void glDepthFunc(int func);
	
	/** @see GL32#glDepthMask(boolean) */
	void enableDepthMask();
	/** @see GL32#glDepthMask(boolean) */
	void disableDepthMask();
	
	
	
	// blending //
	
	/** @see GL32#GL_BLEND */
	void enableBlend();
	/** @see GL32#GL_BLEND */
	void disableBlend();
	
	/** @see GL32#glBlendFunc */
	void glBlendFunc(int sfactor, int dfactor);
	/** @see GL32#glBlendFuncSeparate */
	void glBlendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
	
	
	// frame buffers //
	
	/** @see GL32#glBindFramebuffer */
	void glBindFramebuffer(int target, int framebuffer);
	
	
	// buffers //
	
	/** @see GL32#glGenBuffers() */
	int glGenBuffers();
	
	/** @see GL32#glDeleteBuffers(int)  */
	void glDeleteBuffers(int buffer);
	
	
	
	// culling //
	
	/** @see GL32#GL_CULL_FACE */
	void enableFaceCulling();
	/** @see GL32#GL_CULL_FACE */
	void disableFaceCulling();
	
	
	// textures //
	
	/** @see GL32#glGenTextures() */
	int glGenTextures();
	/** @see GL32#glDeleteTextures(int) */
	void glDeleteTextures(int texture);
	
	/** @see GL32#glActiveTexture(int) */
	void glActiveTexture(int textureId);
	/** 
	 * Only works for textures bound via this system. <br> 
	 * Returns the bound {@link GL32#GL_TEXTURE_BINDING_2D} 
	 */
	int getActiveTexture();
	
	/**
	 * Always binds to {@link GL32#GL_TEXTURE_2D}
	 * @see GL32#glBindTexture(int, int)
	 */
	void glBindTexture(int texture);
	
}
