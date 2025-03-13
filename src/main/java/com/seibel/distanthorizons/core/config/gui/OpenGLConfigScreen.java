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

package com.seibel.distanthorizons.core.config.gui;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author coolGi
 */
public class OpenGLConfigScreen extends AbstractScreen
{
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	private ShaderProgram basicShader;
	private GLVertexBuffer sameContextBuffer;
	private GLVertexBuffer sharedContextBuffer;
	private AbstractVertexAttribute va;
	
	
	
	@Override
	public void init()
	{
		System.out.println("init");
		
		this.va = AbstractVertexAttribute.create();
		this.va.bind();
		// Pos
		this.va.setVertexAttribute(0, 0, VertexPointer.addVec2Pointer(false));
		// Color
		this.va.setVertexAttribute(0, 1, VertexPointer.addVec4Pointer(false));
		this.va.completeAndCheck(Float.BYTES * 6);
		this.basicShader = new ShaderProgram("shaders/test/vert.vert", "shaders/test/frag.frag",
						"fragColor", new String[]{"vPosition", "color"});
		this.createBuffer();
	}
	
	// Render a square with uv color
	private static final float[] vertices = {
			// PosX,Y, ColorR,G,B,A
			-0.5f, -0.5f, 0.0f, 0.0f, 0.0f, 1.0f,
			0.4f, -0.4f, 1.0f, 0.0f, 0.0f, 1.0f,
			0.3f, 0.3f, 1.0f, 1.0f, 0.0f, 0.0f,
			-0.2f, 0.2f, 0.0f, 1.0f, 1.0f, 1.0f
	};
	
	private static GLVertexBuffer createTextingBuffer()
	{
		ByteBuffer buffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
		// Fill buffer with the vertices.
		buffer = buffer.order(ByteOrder.nativeOrder());
		buffer.asFloatBuffer().put(vertices);
		buffer.rewind();
		GLVertexBuffer vbo = new GLVertexBuffer(false);
		vbo.bind();
		vbo.uploadBuffer(buffer, 4, EDhApiGpuUploadMethod.DATA, vertices.length * Float.BYTES);
		return vbo;
	}
	
	private void createBuffer()
	{
		this.sharedContextBuffer = createTextingBuffer();
		this.sameContextBuffer = createTextingBuffer();
	}
	
	@Override
	public void render(float delta)
	{
		System.out.println("Updated config screen with the delta of " + delta);
		
		GL32.glViewport(0, 0, this.width, this.height);
		GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
		GLMC.disableFaceCulling();
		GLMC.disableDepthTest();
		GLMC.disableBlend();
		
		this.basicShader.bind();
		this.va.bind();
		
		// Switch between the two buffers per second
		if (System.currentTimeMillis() % 2000 < 1000)
		{
			this.sameContextBuffer.bind();
			this.va.bindBufferToAllBindingPoints(this.sameContextBuffer.getId());
		}
		else
		{
			this.sameContextBuffer.bind();
			this.va.bindBufferToAllBindingPoints(this.sharedContextBuffer.getId());
		}
		// Render the square
		GL32.glDrawArrays(GL32.GL_TRIANGLE_FAN, 0, 4);
		GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
	}
	
	@Override
	public void tick() { System.out.println("Ticked"); }
	
}
