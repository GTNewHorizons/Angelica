package com.seibel.distanthorizons.core.render.renderer.generic;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3f;

public class GenericObjectShaderProgram extends ShaderProgram implements IDhApiGenericObjectShaderProgram
{
	public static final String VERTEX_SHADER_INSTANCED_PATH = "shaders/genericObject/instanced/vert.vert";
	public static final String VERTEX_SHADER_DIRECT_PATH = "shaders/genericObject/direct/vert.vert";
	public static final String FRAGMENT_SHADER_INSTANCED_PATH = "shaders/genericObject/instanced/frag.frag";
	public static final String FRAGMENT_SHADER_DIRECT_PATH = "shaders/genericObject/direct/frag.frag";
	
	public final AbstractVertexAttribute va;
	
	
	// shader uniforms
	private final int directShaderTransformUniform;
	private final int directShaderColorUniform;
	
	private final int instancedShaderOffsetChunkUniform;
	private final int instancedShaderOffsetSubChunkUniform;
	private final int instancedShaderCameraChunkPosUniform;
	private final int instancedShaderCameraSubChunkPosUniform;
	private final int instancedShaderProjectionModelViewMatrixUniform;
	
	private final int lightMapUniform;
	private final int skyLightUniform;
	private final int blockLightUniform;
	
	private final int northShadingUniform;
	private final int southShadingUniform;
	private final int eastShadingUniform;
	private final int westShadingUniform;
	private final int topShadingUniform;
	private final int bottomShadingUniform;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public GenericObjectShaderProgram(boolean useInstancedRendering)
	{
		super(
				useInstancedRendering ? VERTEX_SHADER_INSTANCED_PATH : VERTEX_SHADER_DIRECT_PATH,
				useInstancedRendering ? FRAGMENT_SHADER_INSTANCED_PATH : FRAGMENT_SHADER_DIRECT_PATH,
				"fragColor", new String[]{"vPosition"});
		
		this.va = AbstractVertexAttribute.create();
		this.va.bind();
		// Pos
		this.va.setVertexAttribute(0, 0, VertexPointer.addVec3Pointer(false));
		this.va.completeAndCheck(Float.BYTES * 3);
		
		this.directShaderTransformUniform = this.tryGetUniformLocation("uTransform");
		this.directShaderColorUniform = this.tryGetUniformLocation("uColor");
		
		this.instancedShaderOffsetChunkUniform = this.tryGetUniformLocation("uOffsetChunk");
		this.instancedShaderOffsetSubChunkUniform = this.tryGetUniformLocation("uOffsetSubChunk");
		this.instancedShaderCameraChunkPosUniform = this.tryGetUniformLocation("uCameraPosChunk");
		this.instancedShaderCameraSubChunkPosUniform = this.tryGetUniformLocation("uCameraPosSubChunk");
		this.instancedShaderProjectionModelViewMatrixUniform = this.tryGetUniformLocation("uProjectionMvm");
		
		this.lightMapUniform = this.getUniformLocation("uLightMap");
		this.skyLightUniform = this.getUniformLocation("uSkyLight");
		this.blockLightUniform = this.getUniformLocation("uBlockLight");
		this.northShadingUniform = this.getUniformLocation("uNorthShading");
		this.southShadingUniform = this.getUniformLocation("uSouthShading");
		this.eastShadingUniform = this.getUniformLocation("uEastShading");
		this.westShadingUniform = this.getUniformLocation("uWestShading");
		this.topShadingUniform = this.getUniformLocation("uTopShading");
		this.bottomShadingUniform = this.getUniformLocation("uBottomShading");
		
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void bind(DhApiRenderParam renderEventParam)
	{
		super.bind();
		this.va.bind();
	}
	@Override
	public void unbind()
	{
		super.unbind();
		this.va.unbind();
	}
	
	@Override
	public void free()
	{
		this.va.free();
		super.free();
	}
	
	@Override
	public void bindVertexBuffer(int vbo) { this.va.bindBufferToAllBindingPoints(vbo); }
	
	@Override
	public void fillIndirectUniformData(
			DhApiRenderParam renderParameters,
			DhApiRenderableBoxGroupShading shading, IDhApiRenderableBoxGroup boxGroup,
			DhApiVec3d camPos
		)
	{
		Mat4f projectionMvmMatrix = new Mat4f(renderParameters.dhProjectionMatrix);
		projectionMvmMatrix.multiply(renderParameters.dhModelViewMatrix);
		
		super.bind();
		
		
		
		
		this.setUniform(this.instancedShaderOffsetChunkUniform,
				new DhApiVec3i(
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
						LodUtil.getChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
				));
		this.setUniform(this.instancedShaderOffsetSubChunkUniform,
				new Vec3f(
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
						LodUtil.getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
				));
		
		this.setUniform(this.instancedShaderCameraChunkPosUniform,
				new DhApiVec3i(
						LodUtil.getChunkPosFromDouble(camPos.x),
						LodUtil.getChunkPosFromDouble(camPos.y),
						LodUtil.getChunkPosFromDouble(camPos.z)
				));
		this.setUniform(this.instancedShaderCameraSubChunkPosUniform,
				new Vec3f(
						LodUtil.getSubChunkPosFromDouble(camPos.x),
						LodUtil.getSubChunkPosFromDouble(camPos.y),
						LodUtil.getSubChunkPosFromDouble(camPos.z)
				));
		
		this.setUniform(this.instancedShaderProjectionModelViewMatrixUniform, projectionMvmMatrix);
		
		this.setUniform(this.lightMapUniform, 0); // TODO this should probably be passed in
		this.setUniform(this.skyLightUniform, boxGroup.getSkyLight());
		this.setUniform(this.blockLightUniform, boxGroup.getBlockLight());
		
		
		this.setUniform(this.northShadingUniform, shading.north);
		this.setUniform(this.southShadingUniform, shading.south);
		this.setUniform(this.eastShadingUniform, shading.east);
		this.setUniform(this.westShadingUniform, shading.west);
		this.setUniform(this.topShadingUniform, shading.top);
		this.setUniform(this.bottomShadingUniform, shading.bottom);
		
		
	}
	
	
	@Override 
	public void fillSharedDirectUniformData(
			DhApiRenderParam renderParameters, 
			DhApiRenderableBoxGroupShading shading, IDhApiRenderableBoxGroup boxGroup, 
			DhApiVec3d camPos)
	{
		
		this.setUniform(this.lightMapUniform, 0); // TODO this should probably be passed in
		this.setUniform(this.skyLightUniform, boxGroup.getSkyLight());
		this.setUniform(this.blockLightUniform, boxGroup.getBlockLight());
		
		
		this.setUniform(this.northShadingUniform, shading.north);
		this.setUniform(this.southShadingUniform, shading.south);
		this.setUniform(this.eastShadingUniform, shading.east);
		this.setUniform(this.westShadingUniform, shading.west);
		this.setUniform(this.topShadingUniform, shading.top);
		this.setUniform(this.bottomShadingUniform, shading.bottom);
		
	}
	
	public void fillDirectUniformData(
			DhApiRenderParam renderParameters,
			IDhApiRenderableBoxGroup boxGroup, DhApiRenderableBox box, 
			DhApiVec3d camPos)
	{
		Mat4f projectionMvmMatrix = new Mat4f(renderParameters.dhProjectionMatrix);
		projectionMvmMatrix.multiply(renderParameters.dhModelViewMatrix);
		
		Mat4f boxTransform = Mat4f.createTranslateMatrix(
				(float) (box.minPos.x + boxGroup.getOriginBlockPos().x - camPos.x),
				(float) (box.minPos.y + boxGroup.getOriginBlockPos().y - camPos.y),
				(float) (box.minPos.z + boxGroup.getOriginBlockPos().z - camPos.z));
		boxTransform.multiply(Mat4f.createScaleMatrix(
				(float) (box.maxPos.x - box.minPos.x),
				(float) (box.maxPos.y - box.minPos.y),
				(float) (box.maxPos.z - box.minPos.z)));
		projectionMvmMatrix.multiply(boxTransform);
		this.setUniform(this.directShaderTransformUniform, projectionMvmMatrix);
		
		this.setUniform(this.directShaderColorUniform, box.color);
		
	}
	
	
	
	@Override
	public int getId() { return this.id; }
	
	/** The base DH render program should always render */
	@Override
	public boolean overrideThisFrame() { return true; }
	
}
