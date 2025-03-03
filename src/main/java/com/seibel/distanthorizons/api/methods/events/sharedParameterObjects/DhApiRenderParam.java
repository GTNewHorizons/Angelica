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

package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEventParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/**
 * Contains information relevant to Distant Horizons and Minecraft rendering.
 *
 * @author James Seibel
 * @version 2024-1-31
 * @since API 1.0.0
 */
public class DhApiRenderParam implements IDhApiEventParam
{
	/** Indicates what render pass DH is currently rendering */
	public final EDhApiRenderPass renderPass;
	
	/** Indicates how far into this tick the frame is. */
	public final float partialTicks;
	
	/** 
	 * Indicates DH's near clip plane, measured in blocks. 
	 * Note: this may change based on time, player speed, and other factors. 
	 */
	public final float nearClipPlane;
	/**
	 * Indicates DH's far clip plane, measured in blocks. 
	 * Note: this may change based on time, player speed, and other factors. 
	 */
	public final float farClipPlane;
	
	/** The projection matrix Minecraft is using to render this frame. */
	public final DhApiMat4f mcProjectionMatrix;
	/** The model view matrix Minecraft is using to render this frame. */
	public final DhApiMat4f mcModelViewMatrix;
	
	/** The projection matrix Distant Horizons is using to render this frame. */
	public final DhApiMat4f dhProjectionMatrix;
	/** The model view matrix Distant Horizons is using to render this frame. */
	public final DhApiMat4f dhModelViewMatrix;
	
	public final int worldYOffset;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	
	public DhApiRenderParam(DhApiRenderParam parent)
	{
		this(
				parent.renderPass,
				parent.partialTicks,
				parent.nearClipPlane, parent.farClipPlane,
				parent.mcProjectionMatrix.copy(), parent.mcModelViewMatrix.copy(),
				parent.dhProjectionMatrix.copy(), parent.dhModelViewMatrix.copy(),
				parent.worldYOffset
		);
	}
	public DhApiRenderParam(
			EDhApiRenderPass renderPass,
			float newPartialTicks,
			float nearClipPlane, float farClipPlane,
			DhApiMat4f newMcProjectionMatrix, DhApiMat4f newMcModelViewMatrix,
			DhApiMat4f newDhProjectionMatrix, DhApiMat4f newDhModelViewMatrix,
			int worldYOffset
			)
	{
		this.renderPass = renderPass;
		
		this.partialTicks = newPartialTicks;
		
		this.farClipPlane = farClipPlane;
		this.nearClipPlane = nearClipPlane;
		
		this.mcProjectionMatrix = newMcProjectionMatrix;
		this.mcModelViewMatrix = newMcModelViewMatrix;
		
		this.dhProjectionMatrix = newDhProjectionMatrix;
		this.dhModelViewMatrix = newDhModelViewMatrix;
		
		this.worldYOffset = worldYOffset;
		
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public DhApiRenderParam copy()
	{
		return new DhApiRenderParam(this); 
	}
	
}
