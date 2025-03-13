package com.seibel.distanthorizons.api.interfaces.render;

import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;

import java.util.List;

/**
 * Handles creating
 * {@link IDhApiRenderableBoxGroup} objects,
 * which can be added via a {@link IDhApiCustomRenderRegister}.
 *
 * @see IDhApiCustomRenderRegister
 * @see IDhApiRenderableBoxGroup
 * 
 * @author James Seibel
 * @version 2024-7-3
 * @since API 3.0.0
 */
public interface IDhApiCustomRenderObjectFactory
{
	/**
	 * Creates a {@link IDhApiRenderableBoxGroup} from for the given {@link DhApiRenderableBox}
	 * where the box is positioned relative to the level's origin.
	 * 
	 * @param resourceLocation A colon separated Resource Location string, similar to vanilla Minecraft, for example: "DistantHorizons:Clouds"
	 * 
	 * @see DhApiRenderableBox
	 * @see IDhApiRenderableBoxGroup#getResourceLocationNamespace() 
	 * @see IDhApiRenderableBoxGroup#getResourceLocationPath() 
	 * 
	 * @throws IllegalArgumentException if <code>resourceLocation</code> is null, isn't separated by a colon, or has multiple colons.
	 */
	IDhApiRenderableBoxGroup createForSingleBox(String resourceLocation, DhApiRenderableBox cube) throws IllegalArgumentException;
	
	/**
	 * Creates a {@link IDhApiRenderableBoxGroup} from the given list of {@link DhApiRenderableBox} where each
	 * one is positioned relative to given <code>originBlockPos</code>, which in turn is relative to the level's origin.
	 *
	 * @param resourceLocation A colon separated Resource Location string, similar to vanilla Minecraft, for example: "DistantHorizons:Clouds"
	 * @param originBlockPos The starting position for this {@link IDhApiRenderableBoxGroup}, can be changed during runtime.
	 * 
	 * 
	 * @see DhApiRenderableBox
	 * @see IDhApiRenderableBoxGroup#getResourceLocationNamespace()
	 * @see IDhApiRenderableBoxGroup#getResourceLocationPath()
	 * 
	 * @throws IllegalArgumentException if <code>resourceLocation</code> is null, isn't separated by a colon, or has multiple colons.
	 */
	IDhApiRenderableBoxGroup createRelativePositionedGroup(String resourceLocation, DhApiVec3d originBlockPos, List<DhApiRenderableBox> cubeList);
	
	/**
	 * Creates a {@link IDhApiRenderableBoxGroup} from the given list of {@link DhApiRenderableBox} where each
	 * one is positioned relative to the level's origin.
	 * 
	 * @param resourceLocation A colon separated Resource Location string, similar to vanilla Minecraft, for example: "DistantHorizons:Clouds"
	 *
	 * @see DhApiRenderableBox
	 * @see IDhApiRenderableBoxGroup#getResourceLocationNamespace()
	 * @see IDhApiRenderableBoxGroup#getResourceLocationPath()
	 * 
	 * @throws IllegalArgumentException if <code>resourceLocation</code> is null, isn't separated by a colon, or has multiple colons.
	 */
	IDhApiRenderableBoxGroup createAbsolutePositionedGroup(String resourceLocation, List<DhApiRenderableBox> cubeList);
	
}
