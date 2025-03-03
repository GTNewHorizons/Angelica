package com.seibel.distanthorizons.core.render.renderer.generic;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

import java.awt.*;
import java.io.Closeable;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class RenderableBoxGroup 
			extends AbstractList<DhApiRenderableBox> 
			implements IDhApiRenderableBoxGroup, Closeable
	{
		private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
		
		public static final AtomicInteger NEXT_ID_ATOMIC_INT = new AtomicInteger(0);
		
		
		
		public final long id;
		
		public final String resourceLocationNamespace;
		public final String resourceLocationPath;
		
		/** If false the boxes will be positioned relative to the level's origin */
		public final boolean positionBoxesRelativeToGroupOrigin;
		
		private final List<DhApiRenderableBox> boxList;
		/** backup list which allows for uploading the boxes even it the main list is being modified on a different thread. */
		private final List<DhApiRenderableBox> uploadBoxList;
		private final DhApiVec3d originBlockPos;
		
		
		public boolean active = true;
		public boolean ssaoEnabled = true;
		private boolean vertexDataDirty = true;
		
		public int skyLight = 15;
		public int blockLight = 0;
		public DhApiRenderableBoxGroupShading shading = DhApiRenderableBoxGroupShading.getDefaultShaded();
		
		@Nullable
		public Consumer<DhApiRenderParam> beforeRenderFunc;
		public Consumer<DhApiRenderParam> afterRenderFunc;
		
		// instance data
		public int instanceColorVbo = 0;
		public int instanceMaterialVbo = 0;
		public int instanceScaleVbo = 0;
		public int instanceChunkPosVbo = 0;
		public int instanceSubChunkPosVbo = 0;
		
		public int uploadedBoxCount = -1;
		
		
		
		// setters/getters //
		
		@Override
		public long getId() { return this.id; }
		
		@Override 
		public String getResourceLocationNamespace() { return this.resourceLocationNamespace; }
		@Override 
		public String getResourceLocationPath() { return this.resourceLocationPath; }
		
		@Override
		public void setOriginBlockPos(DhApiVec3d pos)
		{
			this.originBlockPos.x = pos.x;
			this.originBlockPos.y = pos.y;
			this.originBlockPos.z = pos.z;
		}
		
		@Override
		public DhApiVec3d getOriginBlockPos() { return new DhApiVec3d(this.originBlockPos.x, this.originBlockPos.y, this.originBlockPos.z); }
		
		
		@Override
		public void setSkyLight(int skyLight) 
		{
			if (skyLight < LodUtil.MIN_MC_LIGHT || skyLight > LodUtil.MAX_MC_LIGHT)
			{
				throw new IllegalArgumentException("Sky light ["+skyLight+"] must be between ["+LodUtil.MIN_MC_LIGHT+"] and ["+LodUtil.MAX_MC_LIGHT+"] (inclusive).");
			}
			this.skyLight = skyLight; 
		}
		@Override
		public int getSkyLight() { return this.skyLight; }
		
		@Override
		public void setBlockLight(int blockLight) 
		{
			if (blockLight < LodUtil.MIN_MC_LIGHT || blockLight > LodUtil.MAX_MC_LIGHT)
			{
				throw new IllegalArgumentException("Block light ["+blockLight+"] must be between ["+LodUtil.MIN_MC_LIGHT+"] and ["+LodUtil.MAX_MC_LIGHT+"] (inclusive).");
			}
			this.blockLight = blockLight; 
		}
		@Override
		public int getBlockLight() { return this.blockLight; }
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public RenderableBoxGroup(
				String resourceLocation, 
				DhApiVec3d originBlockPos, List<DhApiRenderableBox> boxList, 
				boolean positionBoxesRelativeToGroupOrigin) throws IllegalArgumentException
		{
			String[] splitResourceLocation =  resourceLocation.split(":");
			if (splitResourceLocation.length != 2)
			{
				throw new IllegalArgumentException("Resource Location must be a string that's separated by a single colon, for example: [DistantHorizons:Beacons], your namespace ["+resourceLocation+"], contains ["+(splitResourceLocation.length-1)+"] colons.");
			}
			
			this.resourceLocationNamespace = splitResourceLocation[0];
			this.resourceLocationPath = splitResourceLocation[1];
			
			this.id = NEXT_ID_ATOMIC_INT.getAndIncrement();
			this.boxList = Collections.synchronizedList(new ArrayList<>(boxList));
			this.uploadBoxList = Collections.synchronizedList(new ArrayList<>(boxList));
			
			this.originBlockPos = originBlockPos;
			this.positionBoxesRelativeToGroupOrigin = positionBoxesRelativeToGroupOrigin;
		}
		
		
		
		// methods //
		
		@Override
		public boolean add(DhApiRenderableBox box) { return this.boxList.add(box); }
		
		@Override
		public void setPreRenderFunc(Consumer<DhApiRenderParam> func) { this.beforeRenderFunc = func; }
		
		@Override
		public void setPostRenderFunc(Consumer<DhApiRenderParam> func) { this.afterRenderFunc = func; }
		
		@Override 
		public void triggerBoxChange() { this.vertexDataDirty = true; }
		
		@Override
		public void setActive(boolean active) { this.active = active; }
		@Override
		public boolean isActive() { return this.active; }
		
		@Override
		public void setSsaoEnabled(boolean ssaoEnabled) { this.ssaoEnabled = ssaoEnabled; }
		@Override
		public boolean isSsaoEnabled() { return this.ssaoEnabled; }
		
		/** 
		 * This is called before every frame, even if {@link this#isActive()} returns false. <br>
		 * {@link this#isActive()} can be changed at this point before the object is rendered to the frame.
		 */
		public void preRender(DhApiRenderParam renderEventParam) 
		{
			if (this.beforeRenderFunc != null)
			{
				this.beforeRenderFunc.accept(renderEventParam);
			}
		}
		/**
		 * Called after rendering is completed. <br>
		 * Can be used to handle any necessary cleanup.
		 */
		public void postRender(DhApiRenderParam renderEventParam) 
		{
			if (this.afterRenderFunc != null)
			{
				this.afterRenderFunc.accept(renderEventParam);
			}
		}
		
		@Override
		public void setShading(DhApiRenderableBoxGroupShading shading) { this.shading = shading; }
		@Override
		public DhApiRenderableBoxGroupShading getShading() { return this.shading; }
		
		
		
		//================//
		// List Overrides //
		//================//
		
		@Override
		public DhApiRenderableBox get(int index) { return this.boxList.get(index); }
		@Override 
		public int size() { return this.boxList.size(); }
		@Override 
		public boolean removeIf(Predicate<? super DhApiRenderableBox> filter) { return this.boxList.removeIf(filter); }
		@Override 
		public void replaceAll(UnaryOperator<DhApiRenderableBox> operator) { this.boxList.replaceAll(operator); }
		@Override 
		public void sort(Comparator<? super DhApiRenderableBox> comparator) { this.boxList.sort(comparator); }
		@Override 
		public void forEach(Consumer<? super DhApiRenderableBox> action) { this.boxList.forEach(action); }
		@Override 
		public Spliterator<DhApiRenderableBox> spliterator() { return this.boxList.spliterator(); }
		@Override 
		public Stream<DhApiRenderableBox> stream() { return this.boxList.stream(); }
		@Override 
		public Stream<DhApiRenderableBox> parallelStream() { return this.boxList.parallelStream(); }
		@Override 
		public void clear() { this.boxList.clear(); }
		
		
		
		//===================//
		// vertex attributes //
		//===================//
		
		/** Does nothing if the vertex data is already up-to-date */
		public void updateVertexAttributeData()
		{
			if (!this.vertexDataDirty)
			{
				return;
			}
			this.vertexDataDirty = false;
			
			if (this.instanceChunkPosVbo == 0)
			{
				this.instanceChunkPosVbo = GLMC.glGenBuffers();
				this.instanceSubChunkPosVbo = GLMC.glGenBuffers();
				this.instanceScaleVbo = GLMC.glGenBuffers();
				this.instanceColorVbo = GLMC.glGenBuffers();
				this.instanceMaterialVbo = GLMC.glGenBuffers();
			}
			
			// copy over the box list so we can upload without concurrent modification issues
			this.uploadBoxList.clear();
			synchronized (this.uploadBoxList)
			{
				this.uploadBoxList.addAll(this.boxList);
			}
			
			
			int boxCount = this.uploadBoxList.size();
			this.uploadedBoxCount = boxCount;
			
			
			
			// transformation / scaling //
			int[] chunkPosData = RenderBoxArrayCache.getCachedIntArray(boxCount * 3, 0);
			float[] subChunkPosData = RenderBoxArrayCache.getCachedFloatArray(boxCount * 3, 1);
			float[] scalingData = RenderBoxArrayCache.getCachedFloatArray(boxCount * 3, 2);
			for (int i = 0; i < boxCount; i++)
			{
				DhApiRenderableBox box = this.uploadBoxList.get(i);
				
				int dataIndex = i * 3;
				
				chunkPosData[dataIndex] = LodUtil.getChunkPosFromDouble(box.minPos.x);
				chunkPosData[dataIndex + 1] = LodUtil.getChunkPosFromDouble(box.minPos.y);
				chunkPosData[dataIndex + 2] = LodUtil.getChunkPosFromDouble(box.minPos.z);
				
				subChunkPosData[dataIndex] = LodUtil.getSubChunkPosFromDouble(box.minPos.x);
				subChunkPosData[dataIndex + 1] = LodUtil.getSubChunkPosFromDouble(box.minPos.y);
				subChunkPosData[dataIndex + 2] = LodUtil.getSubChunkPosFromDouble(box.minPos.z);
				
				scalingData[dataIndex] = (float) (box.maxPos.x - box.minPos.x);
				scalingData[dataIndex + 1] = (float) (box.maxPos.y - box.minPos.y);
				scalingData[dataIndex + 2] = (float) (box.maxPos.z - box.minPos.z);
				
			}
			
			
			// colors/materials //
			float[] colorData = RenderBoxArrayCache.getCachedFloatArray(boxCount * 4, 3);
			int[] materialData = RenderBoxArrayCache.getCachedIntArray(boxCount, 4);
			for (int i = 0; i < boxCount; i++)
			{
				DhApiRenderableBox box = this.uploadBoxList.get(i);
				Color color = box.color;
				int colorIndex = i * 4;
				colorData[colorIndex] = color.getRed() / 255.0f;
				colorData[colorIndex + 1] = color.getGreen() / 255.0f;
				colorData[colorIndex + 2] = color.getBlue() / 255.0f;
				colorData[colorIndex + 3] = color.getAlpha() / 255.0f;
				
				materialData[i] = box.material;
			}
			
			
			// Upload transformation matrices
			GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.instanceChunkPosVbo);
			GL32.glBufferData(GL32.GL_ARRAY_BUFFER, chunkPosData, GL32.GL_DYNAMIC_DRAW);
			GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.instanceSubChunkPosVbo);
			GL32.glBufferData(GL32.GL_ARRAY_BUFFER, subChunkPosData, GL32.GL_DYNAMIC_DRAW);
			GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.instanceScaleVbo);
			GL32.glBufferData(GL32.GL_ARRAY_BUFFER, scalingData, GL32.GL_DYNAMIC_DRAW);
			
			// Upload colors
			GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.instanceColorVbo);
			GL32.glBufferData(GL32.GL_ARRAY_BUFFER, colorData, GL32.GL_DYNAMIC_DRAW);
			
			// Upload materials
			GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.instanceMaterialVbo);
			GL32.glBufferData(GL32.GL_ARRAY_BUFFER, materialData, GL32.GL_DYNAMIC_DRAW);
		}
		
		
		
		//================//
		// base overrides //
		//================//
		
		@Override
		public String toString() { return "ID:["+this.id+"], pos:["+this.originBlockPos.x+","+this.originBlockPos.y+","+this.originBlockPos.z+"], size:["+this.size()+"], active:["+this.active+"]"; }
		
		@Override 
		public void close()
		{
			GLProxy.getInstance().queueRunningOnRenderThread(() ->
			{
				if (this.instanceChunkPosVbo != 0)
				{
					GLMC.glDeleteBuffers(this.instanceChunkPosVbo);
					this.instanceChunkPosVbo = 0;
				}
				
				if (this.instanceSubChunkPosVbo != 0)
				{
					GLMC.glDeleteBuffers(this.instanceSubChunkPosVbo);
					this.instanceSubChunkPosVbo = 0;
				}
				
				if (this.instanceScaleVbo != 0)
				{
					GLMC.glDeleteBuffers(this.instanceScaleVbo);
					this.instanceScaleVbo = 0;
				}
				
				if (this.instanceColorVbo != 0)
				{
					GLMC.glDeleteBuffers(this.instanceColorVbo);
					this.instanceColorVbo = 0;
				}
				
				if (this.instanceMaterialVbo != 0)
				{
					GLMC.glDeleteBuffers(this.instanceMaterialVbo);
					this.instanceMaterialVbo = 0;
				}
			});
		}
		
	}
	