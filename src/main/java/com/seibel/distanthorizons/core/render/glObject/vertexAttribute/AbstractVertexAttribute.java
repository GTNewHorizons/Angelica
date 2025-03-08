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

package com.seibel.distanthorizons.core.render.glObject.vertexAttribute;

import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import org.lwjgl.opengl.GL32;

/**
 * Base for binding/unbinding Vertex Attribute objects (VAO's).
 * 
 * @see VertexAttributePostGL43
 * @see VertexAttributePreGL43
 */
public abstract class AbstractVertexAttribute
{
	/** Stores the handle of the AbstractVertexAttribute. */
	public final int id;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	// This will bind AbstractVertexAttribute
	protected AbstractVertexAttribute()
	{
		this.id = GL32.glGenVertexArrays();
		GL32.glBindVertexArray(this.id);
	}
	
	public static AbstractVertexAttribute create()
	{
		if (GLProxy.getInstance().vertexAttributeBufferBindingSupported)
		{
			return new VertexAttributePostGL43();
		}
		else
		{
			return new VertexAttributePreGL43();
		}
	}
	
	
	
	//=========//
	// binding //
	//=========//
	
	public void bind() { GL32.glBindVertexArray(this.id); }
	public void unbind() { GL32.glBindVertexArray(0); }
	
	/** Always remember to always free your resources! */
	public void free() { GL32.glDeleteVertexArrays(this.id); }
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	public abstract void bindBufferToAllBindingPoints(int buffer);
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	public abstract void bindBufferToBindingPoint(int buffer, int bindingPoint);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void unbindBuffersFromAllBindingPoint();
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void unbindBuffersFromBindingPoint(int bindingPoint);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void setVertexAttribute(int bindingPoint, int attributeIndex, VertexPointer attribute);
	/** Requires both AbstractVertexAttribute to be bound */
	public abstract void completeAndCheck(int expectedStrideSize);
	
}
