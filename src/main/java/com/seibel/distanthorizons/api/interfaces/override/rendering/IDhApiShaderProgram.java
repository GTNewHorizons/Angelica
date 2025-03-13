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
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;

/**
 * @see IDhApiGenericObjectShaderProgram
 * 
 * @author James Seibel
 * @version 2024-1-24
 * @since API 2.0.0
 */
public interface IDhApiShaderProgram extends IDhApiOverrideable
{
	
	/**
	 * If this method is called that means this program has the highest priority as defined by {@link IDhApiOverrideable#getPriority()}
	 * and gets to decide if it wants to be used to render this frame or not. <br><br>
	 *
	 * If this method returns true then this program will be used for this frame. <br>
	 * If this returns false then the default DH {@link IDhApiShaderProgram} will be used instead.
	 */
	boolean overrideThisFrame();
	
	/** @return the OpenGL ID for this shader program */
	int getId();
	
	/** Free any OpenGL objects owned by this program. */
	void free();
	
	/** Runs any necessary binding this program needs so rendering can be done. */
	void bind();
	/** Runs any necessary unbinding this program needs so rendering can be done by another program. */
	void unbind();
	
	
	/** sets up the necessary uniforms for rendering */
	void fillUniformData(DhApiRenderParam renderParameters);
	
	/** sets the vec3 that all DH verticies should be offset by when rendering */
	void setModelOffsetPos(DhApiVec3f modelPos);
	
	/** Binds the given Vertex Buffer Object to this shader program for rendering. */
	void bindVertexBuffer(int vbo);
	
}
