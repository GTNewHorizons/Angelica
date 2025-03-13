package com.seibel.distanthorizons.api.interfaces.render;

import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;

import java.util.List;
import java.util.function.Consumer;

/**
 * A list of {@link DhApiRenderableBox}'s that
 * can be rendered to DH's terrain pass.
 * 
 * @see DhApiRenderableBox
 * 
 * @author James Seibel
 * @version 2024-6-30
 * @since API 3.0.0
 */
public interface IDhApiRenderableBoxGroup extends List<DhApiRenderableBox>
{
	/**
	 * A unique numerical ID used by DH during rendering.
	 * This can also be used to bind/unbind specific {@link IDhApiRenderableBoxGroup}'s from the renderer.
	 * @return the ID for this specific group 
	 */
	long getId();
	
	/** 
	 * Used to determine which mods have added what to the DH renderer.
	 * This can be used both by the F3 pie chart so you as a mod developer can profile your code
	 * or by shader developers who want to render your objects differently. <br><br>
	 * 
	 * Should be used the same as a vanilla Minecraft ResourceLocation.
	 * For example if your mod named "Heavy Thunder" adds additional clouds named "Storm Front",
	 * your Resource Location would be something like "HeavyThunder:StormFront"
	 * and this method would return "HeavyThunder".
	 */
	String getResourceLocationNamespace();
	/**
	 * Used to determine what type of object mods have added what to the DH renderer.
	 * This can be used both by the F3 pie chart so you as a mod developer can profile your code
	 * or by shader developers who want to render your objects differently. <br><br>
	 *
	 * Should be used the same as a vanilla Minecraft ResourceLocation.
	 * For example if your mod named "Heavy Thunder" adds additional clouds named "Storm Front",
	 * your Resource Location would be something like "HeavyThunder:StormFront"
	 * and this method would return "StormFront".
	 */
	String getResourceLocationPath();
	
	/** Sets whether this group should render or not. */
	void setActive(boolean active);
	/** @return if active this group will render. */
	boolean isActive();
	
	/** Sets whether this group should render with Screen Space Ambient Occlusioning. */
	void setSsaoEnabled(boolean ssaoEnabled);
	/** @return if active this group will render with Screen Space Ambient Occlusioning. */
	boolean isSsaoEnabled();
	
	/** Sets where this group will render in the level. */
	void setOriginBlockPos(DhApiVec3d pos);
	/** @return the block position in the level that all {@see DhApiRenderableBox} will render relative to. */
	DhApiVec3d getOriginBlockPos();
	
	/** 
	 * Called right before this group is rendered. <br>
	 * This is a good place to change the origin or notify of any box changes. 
	 */
	void setPreRenderFunc(Consumer<DhApiRenderParam> renderEventParam);
	void setPostRenderFunc(Consumer<DhApiRenderParam> renderEventParam); // TODO name?
	
	/**
	 * If a cube's color, position, or other property is changed this method
	 * must be called for those changes to render. <br><br>
	 * 
	 * Note: changing the group's position via {@link #setOriginBlockPos} doesn't
	 * require calling this method.
	 */
	void triggerBoxChange();
	
	/** Only accepts values between 0 and 15 */
	void setSkyLight(int skyLight);
	int getSkyLight();
	
	/** Only accepts values between 0 and 15 */
	void setBlockLight(int blockLight);
	int getBlockLight();
	
	void setShading(DhApiRenderableBoxGroupShading shading);
	DhApiRenderableBoxGroupShading getShading();
	
}
