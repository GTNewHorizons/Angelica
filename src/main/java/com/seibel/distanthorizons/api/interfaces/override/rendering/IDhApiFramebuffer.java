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

package com.seibel.distanthorizons.api.interfaces.override.rendering;

import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;

/**
 * @author James Seibel
 * @version 2024-1-24
 * @since API 2.0.0
 */
public interface IDhApiFramebuffer extends IDhApiOverrideable
{
	
	/**
	 * If this method is called that means this program has the highest priority as defined by {@link IDhApiOverrideable#getPriority()}
	 * and gets to decide if it wants to be used to render this frame or not. <br><br>
	 *
	 * If this method returns true then this program will be used for this frame. <br>
	 * If this returns false then the default DH {@link IDhApiShaderProgram} will be used instead.
	 */
	boolean overrideThisFrame();
	
	/** Runs any necessary binding this program needs so rendering can be done. */
	void bind();
	
	/** Binds the given OpenGL depth texture ID. */
	void addDepthAttachment(int textureId, boolean isCombinedStencil);
	
	/** @return the OpenGL ID for this shader program */
	int getId();
	
	/** @return the OpenGL framebuffer status as defined by <code>glCheckFramebufferStatus</code> */
	int getStatus();
	
	/** Binds the given OpenGL texture ID to the given texture index relative to OpenGL's <code>GL_COLOR_ATTACHMENT0</code> */
	void addColorAttachment(int textureIndex, int textureId);
	
	/** Destroys this framebuffer's OpenGL object(s). */
	void destroy();
	
}
