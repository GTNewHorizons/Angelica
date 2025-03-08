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

package com.seibel.distanthorizons.core.render.renderer.generic;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.*;

/**
 * Handles creating {@link DhApiRenderableBox}.
 * 
 * @see IDhApiCustomRenderRegister
 * @see DhApiRenderableBox
 */
public class GenericRenderObjectFactory implements IDhApiCustomRenderObjectFactory
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final GenericRenderObjectFactory INSTANCE = new GenericRenderObjectFactory();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GenericRenderObjectFactory() { }
	
	
	
	//================//
	// group creation //
	//================//
	
	@Override 
	public IDhApiRenderableBoxGroup createForSingleBox(String resourceLocation, DhApiRenderableBox box)
	{
		ArrayList<DhApiRenderableBox> list = new ArrayList<>();
		list.add(box);
		return this.createAbsolutePositionedGroup(resourceLocation, list);
	}
	
	@Override 
	public IDhApiRenderableBoxGroup createRelativePositionedGroup(String resourceLocation, DhApiVec3d originBlockPos, List<DhApiRenderableBox> boxList)
	{ return new RenderableBoxGroup(resourceLocation, new DhApiVec3d(originBlockPos.x, originBlockPos.y, originBlockPos.z), boxList, true); }
	
	@Override 
	public IDhApiRenderableBoxGroup createAbsolutePositionedGroup(String resourceLocation, List<DhApiRenderableBox> boxList)
	{ return new RenderableBoxGroup(resourceLocation, new DhApiVec3d(0, 0, 0), boxList, false); }
	
}
