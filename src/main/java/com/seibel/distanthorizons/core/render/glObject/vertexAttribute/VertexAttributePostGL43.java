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
import org.lwjgl.opengl.GL43;

/**
 * In OpenGL 4.3 and later, Vertex Attribute got a make-over.
 * Now it provides support for buffer binding points natively.
 * This means that setting up the VAO is just use ONE native call when
 * binding to a buffer. <br><br>
 * 
 * Since I no longer need to implement binding points, I also no
 * longer needs to keep track of Pointers.
 */
public final class VertexAttributePostGL43 extends AbstractVertexAttribute
{
	int numberOfBindingPoints = 0;
	int strideSize = 0;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** This will bind the {@link AbstractVertexAttribute} */
	public VertexAttributePostGL43()
	{
		super(); // also bind AbstractVertexAttribute
	}
	
	
	
	//=========//
	// binding //
	//=========//
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	@Override
	public void bindBufferToAllBindingPoints(int buffer)
	{
		for (int i = 0; i < this.numberOfBindingPoints; i++)
		{
			GL43.glBindVertexBuffer(i, buffer, 0, this.strideSize);
		}
	}
	
	/** Requires both AbstractVertexAttribute and VertexBuffer to be bound */
	@Override
	public void bindBufferToBindingPoint(int buffer, int bindingPoint)
	{
		GL43.glBindVertexBuffer(bindingPoint, buffer, 0, this.strideSize);
	}
	
	
	
	//===========//
	// unbinding //
	//===========//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void unbindBuffersFromAllBindingPoint()
	{
		for (int i = 0; i < this.numberOfBindingPoints; i++)
		{
			GL43.glBindVertexBuffer(i, 0, 0, 0);
		}
	}
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void unbindBuffersFromBindingPoint(int bindingPoint)
	{
		GL43.glBindVertexBuffer(bindingPoint, 0, 0, 0);
	}
	
	
	
	//==========================//
	// manual attribute setting //
	//==========================//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void setVertexAttribute(int bindingPoint, int attributeIndex, VertexPointer attribute)
	{
		if (attribute.useInteger)
		{
			GL43.glVertexAttribIFormat(attributeIndex, attribute.elementCount, attribute.glType, this.strideSize);
		}
		else
		{
			GL43.glVertexAttribFormat(attributeIndex, attribute.elementCount, attribute.glType,
					attribute.normalized, this.strideSize); // Here strideSize is new attrib offset
		}
		
		this.strideSize += attribute.byteSize;
		if (this.numberOfBindingPoints <= bindingPoint)
		{
			this.numberOfBindingPoints = bindingPoint + 1;
		}
		GL43.glVertexAttribBinding(attributeIndex, bindingPoint);
		GL43.glEnableVertexAttribArray(attributeIndex);
	}
	
	
	
	//============//
	// validation //
	//============//
	
	/** Requires AbstractVertexAttribute to be bound */
	@Override
	public void completeAndCheck(int expectedStrideSize)
	{
		if (this.strideSize != expectedStrideSize)
		{
			GLProxy.GL_LOGGER.error("Vertex Attribute calculated stride size " + this.strideSize +
					" does not match the provided expected stride size " + expectedStrideSize + "!");
			throw new IllegalArgumentException("Vertex Attribute Incorrect Format");
		}
		
		GLProxy.GL_LOGGER.info("Vertex Attribute (GL43+) completed. It contains " + this.numberOfBindingPoints
				+ " binding points and a stride size of " + this.strideSize);
	}
	
}
