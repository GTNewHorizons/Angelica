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

package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

public abstract class AbstractShaderRenderer
{
	protected static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	protected ShaderProgram shader;

	protected boolean init = false;
	
	
	protected AbstractShaderRenderer() {}
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		this.onInit();
	}
	
	public void render(float partialTicks)
	{
		this.init();
		
		this.shader.bind();
		
		this.onApplyUniforms(partialTicks);
		
		int width = MC_RENDER.getTargetFrameBufferViewportWidth();
		int height = MC_RENDER.getTargetFrameBufferViewportHeight();
		GL32.glViewport(0, 0, width, height);
		
		this.onRender();
		
		this.shader.unbind();
	}
	
	public void free()
	{
		if (this.shader != null)
		{
			this.shader.free();
		}
	}
	
	protected void onInit() {}
	
	protected void onApplyUniforms(float partialTicks) {}
	
	protected void onRender() {}
}
