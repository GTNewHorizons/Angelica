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

package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.api.enums.config.EDhApiLoggerMode;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class TestRenderer
{
	public static final ConfigBasedLogger logger = new ConfigBasedLogger(
			LogManager.getLogger(TestRenderer.class), () -> EDhApiLoggerMode.LOG_ALL_TO_CHAT);
	public static final ConfigBasedSpamLogger spamLogger = new ConfigBasedSpamLogger(
			LogManager.getLogger(TestRenderer.class), () -> EDhApiLoggerMode.LOG_ALL_TO_CHAT, 1);
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	
	ShaderProgram basicShader;
	GLVertexBuffer vbo;
	AbstractVertexAttribute va;
	boolean init = false;
	
	
	
	
	public TestRenderer() { }
	
	public void init()
	{
		if (this.init)
		{
			return;
		}
		
		logger.info("init");
		this.init = true;
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
	
	private void createBuffer()
	{
		ByteBuffer buffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
		// Fill buffer with vertices.
		buffer.order(ByteOrder.nativeOrder());
		buffer.asFloatBuffer().put(vertices);
		buffer.rewind();
		
		this.vbo = new GLVertexBuffer(false);
		this.vbo.bind();
		this.vbo.uploadBuffer(buffer, 4, EDhApiGpuUploadMethod.DATA, vertices.length * Float.BYTES);
	}
	
	public void render()
	{
		this.init();
		
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, MC_RENDER.getTargetFrameBuffer());
		GL32.glViewport(0, 0, MC_RENDER.getTargetFrameBufferViewportWidth(), MC_RENDER.getTargetFrameBufferViewportHeight());
		GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
		
		GLMC.disableFaceCulling();
		GLMC.disableDepthTest();
		GLMC.disableBlend();
		GLMC.disableScissorTest();
		
		this.basicShader.bind();
		this.va.bind();
		
		this.vbo.bind();
		this.va.bindBufferToAllBindingPoints(this.vbo.getId());
		
		// Render the square
		GL32.glDrawArrays(GL32.GL_TRIANGLE_FAN, 0, 4);
		GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
		
		spamLogger.incLogTries();
	}
	
	
	
}
