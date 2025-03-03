package com.seibel.distanthorizons.api.objects.render;


import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;

import java.awt.*;

/**
 * @see IDhApiRenderableBoxGroup
 * 
 * Shading values are multiplied against the color for each direction,
 * for example: <br>
 * A shading value of 1.0 indicates the color is unchanged. <br>
 * A shading value of 0.0 changes the color to black. <br>
 * 
 * @author James Seibel
 * @version 2024-7-7
 * @since API 3.0.0
 */
public class DhApiRenderableBoxGroupShading
{
	/** negative X */
	public float north = 1.0f;
	/** positive X */
	public float south = 1.0f;
	
	/** positive X */
	public float east = 1.0f;
	/** negative X */
	public float west = 1.0f;
	
	/** positive Y */
	public float top = 1.0f;
	/** negative Y */
	public float bottom = 1.0f;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static DhApiRenderableBoxGroupShading getDefaultShaded()
	{
		DhApiRenderableBoxGroupShading shading = new DhApiRenderableBoxGroupShading();
		shading.setDefaultShaded();
		return shading;
	}
	
	public static DhApiRenderableBoxGroupShading getUnshaded() 
	{
		DhApiRenderableBoxGroupShading shading = new DhApiRenderableBoxGroupShading();
		shading.setUnshaded();
		return shading;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	/**
	 * Directions will have different brightness similar to Minecraft blocks. <br>
	 * This is a good default for un-lit objects. 
	 */
	public void setDefaultShaded()
	{
		this.north = 0.8f;
		this.south = 0.8f;
		this.east = 0.6f;
		this.west = 0.6f;
		this.top = 1.0f;
		this.bottom = 0.5f;
	}
	
	/** 
	 * All directions render with the same brightness. <br>
	 * This is best used for glowing objects like beacons. 
	 */
	public void setUnshaded()
	{
		this.north = 1.0f;
		this.south = 1.0f;
		this.east = 1.0f;
		this.west = 1.0f;
		this.top = 1.0f;
		this.bottom = 1.0f;
	}
	
}
	
