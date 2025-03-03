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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.Pos2D;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL32;

import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 * TODO rename this class, maybe RenderBufferOrganizer or something more specific?
 */
public class RenderBufferHandler implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);

	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	// TODO: Make sorting go into the update loop instead of the render loop as it doesn't need to be done every frame
	private SortedArraySet<LoadedRenderBuffer> loadedNearToFarBuffers = null;
	
	private final AtomicBoolean rebuildAllBuffers = new AtomicBoolean(false);
	
	private int visibleBufferCount;
	private int culledBufferCount;
	private int shadowVisibleBufferCount;
	private int shadowCulledBufferCount;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RenderBufferHandler(LodQuadTree lodQuadTree) 
	{ 
		this.lodQuadTree = lodQuadTree;
		
		IDhApiCullingFrustum coreCameraFrustum = DhApi.overrides.get(IDhApiCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreCameraFrustum == null)
		{
			DhApi.overrides.bind(IDhApiCullingFrustum.class, new DhFrustumBounds());
		}
		
		// by default the shadow pass shouldn't have any frustum culling
		IDhApiShadowCullingFrustum coreShadowFrustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreShadowFrustum == null)
		{
			DhApi.overrides.bind(IDhApiShadowCullingFrustum.class, new NeverCullFrustum());
		}
	}
	
	
	
	//=================//
	// render building //
	//=================//
	
	/**
	 * The following buildRenderList sorting method is based on the following reddit post: <br>
	 * <a href="https://www.reddit.com/r/VoxelGameDev/comments/a0l8zc/correct_depthordering_for_translucent_discrete/">correct_depth_ordering_for_translucent_discrete</a> <br><br>
	 *
	 * TODO: This might get locked by update() causing move() call. Is there a way to avoid this?
	 *       Maybe dupe the base list and use atomic swap on render? Or is this not worth it?
	 */
	public void buildRenderListAndUpdateSections(IClientLevelWrapper clientLevelWrapper, DhApiRenderParam renderEventParam, Vec3f lookForwardVector)
	{
		EDhDirection[] axisDirections = new EDhDirection[3];
		
		// Do the axis that are the longest first (i.e. the largest absolute value of the lookForwardVector),
		// with the sign being the opposite of the respective lookForwardVector component's sign
		float absX = Math.abs(lookForwardVector.x);
		float absY = Math.abs(lookForwardVector.y);
		float absZ = Math.abs(lookForwardVector.z);
		EDhDirection xDir = lookForwardVector.x < 0 ? EDhDirection.EAST : EDhDirection.WEST;
		EDhDirection yDir = lookForwardVector.y < 0 ? EDhDirection.UP : EDhDirection.DOWN;
		EDhDirection zDir = lookForwardVector.z < 0 ? EDhDirection.SOUTH : EDhDirection.NORTH;
		
		if (absX >= absY && absX >= absZ)
		{
			axisDirections[0] = xDir;
			if (absY >= absZ)
			{
				axisDirections[1] = yDir;
				axisDirections[2] = zDir;
			}
			else
			{
				axisDirections[1] = zDir;
				axisDirections[2] = yDir;
			}
		}
		else if (absY >= absX && absY >= absZ)
		{
			axisDirections[0] = yDir;
			if (absX >= absZ)
			{
				axisDirections[1] = xDir;
				axisDirections[2] = zDir;
			}
			else
			{
				axisDirections[1] = zDir;
				axisDirections[2] = xDir;
			}
		}
		else
		{
			axisDirections[0] = zDir;
			if (absX >= absY)
			{
				axisDirections[1] = xDir;
				axisDirections[2] = yDir;
			}
			else
			{
				axisDirections[1] = yDir;
				axisDirections[2] = xDir;
			}
		}
		
		Pos2D cPos = this.lodQuadTree.getCenterBlockPos().toPos2D();
		
		// Now that we have the axis directions, we can sort the render list
		Comparator<LoadedRenderBuffer> farToNearComparator = (loadedBufferA, loadedBufferB) ->
		{
			Pos2D aPos = DhSectionPos.getCenterBlockPos(loadedBufferA.pos).toPos2D();
			Pos2D bPos = DhSectionPos.getCenterBlockPos(loadedBufferB.pos).toPos2D();
			if (true)
			{
				int aManhattanDistance = aPos.manhattanDist(cPos);
				int bManhattanDistance = bPos.manhattanDist(cPos);
				return bManhattanDistance - aManhattanDistance;
			}
			
			for (EDhDirection axisDirection : axisDirections)
			{
				if (axisDirection.getAxis().isVertical())
				{
					continue; // We only sort in the horizontal direction
				}
				
				int abPosDifference;
				if (axisDirection.getAxis().equals(EDhDirection.Axis.X))
				{
					abPosDifference = aPos.getX() - bPos.getX();
				}
				else
				{
					abPosDifference = aPos.getY() - bPos.getY();
				}
				
				if (abPosDifference == 0)
				{
					continue;
				}
				
				if (axisDirection.getAxisDirection().equals(EDhDirection.AxisDirection.NEGATIVE))
				{
					abPosDifference = -abPosDifference; // Reverse the sign
				}
				return abPosDifference;
			}
			
			return DhSectionPos.getDetailLevel(loadedBufferA.pos) - DhSectionPos.getDetailLevel(loadedBufferB.pos); // If all else fails, sort by detail
		};
		this.loadedNearToFarBuffers = new SortedArraySet<>((a, b) -> -farToNearComparator.compare(a, b)); // TODO is the comparator named wrong?
		
		
		
		//====================================//
		// get and update the culling frustum //
		//====================================//
		
		// get the culling frustum
		boolean enableFrustumCulling;
		IDhApiCullingFrustum frustum;
		boolean isShadowPass = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isRenderingShadowPass());
		if (isShadowPass)
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class);
		}
		else
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiCullingFrustum.class);
		}
		
		
		// update the frustum if necessary
		if (enableFrustumCulling)
		{
			int worldMinY = clientLevelWrapper.getMinHeight();
			int worldHeight = clientLevelWrapper.getMaxHeight();
			
			Vec3d cameraPos = MC_RENDER.getCameraExactPosition();
			
			Matrix4fc matWorldView = new Matrix4f()
					.setTransposed(renderEventParam.mcModelViewMatrix.getValuesAsArray())
					.translate(-(float) cameraPos.x, -(float) cameraPos.y, -(float) cameraPos.z);
			
			Matrix4fc matWorldViewProjection = new Matrix4f()
					.setTransposed(renderEventParam.dhProjectionMatrix.getValuesAsArray())
					.mul(matWorldView);
			
			frustum.update(worldMinY, worldMinY + worldHeight, new Mat4f(matWorldViewProjection));
		}
		
		
		
		//=========================//
		// Update the section list //
		//=========================//
		
		if (isShadowPass)
		{
			this.shadowCulledBufferCount = 0;
		}
		else
		{
			this.culledBufferCount = 0;
		}
		
		boolean rebuildAllBuffers = this.rebuildAllBuffers.getAndSet(false);
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.lodQuadTree.nodeIterator();
		while (nodeIterator.hasNext())
		{
			QuadNode<LodRenderSection> node = nodeIterator.next();
			
			long sectionPos = node.sectionPos;
			LodRenderSection renderSection = node.value;
			if (renderSection == null)
			{
				continue;
			}
			
			try
			{
				if (enableFrustumCulling)
				{
					DhLodPos lodBounds = DhSectionPos.getSectionBBoxPos(renderSection.pos);
					int blockMinX = lodBounds.getMinX().toBlockWidth();
					int blockMinZ = lodBounds.getMinZ().toBlockWidth();
					int lodBlockWidth = lodBounds.getBlockWidth();
					if (!frustum.intersects(blockMinX, blockMinZ, lodBlockWidth, lodBounds.detailLevel))
					{
						if (isShadowPass)
						{
							this.shadowCulledBufferCount++;
						}
						else
						{
							this.culledBufferCount++;
						}
						
						continue;
					}
				}
				
				ColumnRenderBuffer buffer = renderSection.renderBuffer;
				if (buffer == null || !renderSection.getRenderingEnabled())
				{
					continue;
				}
				
				
				this.loadedNearToFarBuffers.add(new LoadedRenderBuffer(buffer, sectionPos));
			}
			catch (Exception e)
			{
				LOGGER.error("Error updating QuadTree render source at " + renderSection.pos + ".", e);
			}
		}
		
		if (isShadowPass)
		{
			this.shadowVisibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		else
		{
			this.visibleBufferCount = this.loadedNearToFarBuffers.size();
		}
	}
	
	public void MarkAllBuffersDirty() { this.rebuildAllBuffers.set(true); }
	
	
	
	//================//
	// render methods //
	//================//
	
	public void renderOpaque(LodRenderer renderContext,DhApiRenderParam renderEventParam)
	{ this.renderPass(renderContext, renderEventParam, true); }
	public void renderTransparent(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	{ this.renderPass(renderContext, renderEventParam, false); }
	
	private void renderPass(LodRenderer renderContext, DhApiRenderParam renderEventParam, boolean opaquePass)
	{
		//=======================//
		// debug wireframe setup //
		//=======================//
		
		boolean renderWireframe = Config.Client.Advanced.Debugging.renderWireframe.get();
		if (renderWireframe)
		{
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
			GLMC.disableFaceCulling();
		}
		else
		{
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
			GLMC.enableFaceCulling();
		}
		
		
		//===========//
		// rendering //
		//===========//
		
		if (opaquePass)
		{
			// TODO why can these sometimes be null when teleporting between multiverses
			if (this.loadedNearToFarBuffers != null)
			{
				this.loadedNearToFarBuffers.forEach(loadedBuffer -> loadedBuffer.buffer.renderOpaque(renderContext, renderEventParam));
			}
		}
		else
		{
			// TODO why can these sometimes be null when teleporting between multiverses
			if (this.loadedNearToFarBuffers != null)
			{
				ListIterator<LoadedRenderBuffer> iter = this.loadedNearToFarBuffers.listIterator(this.loadedNearToFarBuffers.size());
				while (iter.hasPrevious())
				{
					LoadedRenderBuffer loadedBuffer = iter.previous();
					loadedBuffer.buffer.renderTransparent(renderContext, renderEventParam);
				}
			}
		}
		
		
		
		//=========================//
		// debug wireframe cleanup //
		//=========================//
		
		if (renderWireframe)
		{
			// default back to GL_FILL since all other rendering uses it 
			GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
			GLMC.enableFaceCulling();
		}
		
	}
	
	
	
	//=========//
	// F3 menu //
	//=========//
	
	public String getVboRenderDebugMenuString()
	{
		String countText = F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount + this.culledBufferCount);
		}
		return LodUtil.formatLog("VBO Render Count: " + countText);
	}
	public String getShadowPassRenderDebugMenuString()
	{
		boolean hasIrisShaders = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isShaderPackInUse());
		if (!hasIrisShaders)
		{
			return null;
		}
		
		String countText = F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount + this.shadowCulledBufferCount);
		}
		return LodUtil.formatLog("Shadow VBO Render Count: " + countText);
	}
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close() { this.lodQuadTree.close(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class LoadedRenderBuffer
	{
		public final ColumnRenderBuffer buffer;
		public final long pos;
		
		LoadedRenderBuffer(ColumnRenderBuffer buffer, long pos)
		{
			this.buffer = buffer;
			this.pos = pos;
		}
		
	}
	
}
