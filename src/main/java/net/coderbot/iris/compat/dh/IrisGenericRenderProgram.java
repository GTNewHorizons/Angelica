package net.coderbot.iris.compat.dh;

import com.google.common.primitives.Ints;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import com.mitchej123.lwjgl.MemoryStack;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IrisGenericRenderProgram implements IDhApiGenericObjectShaderProgram {
    // Uniforms
    public final int modelViewUniform;
    public final int modelViewInverseUniform;
    public final int projectionUniform;
    public final int projectionInverseUniform;
    public final int normalMatrix3fUniform;
    // Fog/Clip Uniforms
    private final int id;
    private final ProgramUniforms uniforms;
    private final CustomUniforms customUniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final BlendModeOverride blend;
    private final BufferBlendOverride[] bufferBlendOverrides;
    private final Matrix4f tempModel = new Matrix4f();
    private final Matrix4f tempProj = new Matrix4f();
    private final Matrix4f tempMat4 = new Matrix4f();
    private final Matrix3f tempMat3 = new Matrix3f();

    private final int instancedShaderOffsetChunkUniform;
    private final int instancedShaderOffsetSubChunkUniform;
    private final int instancedShaderCameraChunkPosUniform;
    private final int instancedShaderCameraSubChunkPosUniform;
    private final int instancedShaderProjectionModelViewMatrixUniform;
    private final int va;
    private final int uBlockLight;
    private final int uSkyLight;

    // This will bind  AbstractVertexAttribute
    private IrisGenericRenderProgram(String name, boolean isShadowPass, boolean translucent, BlendModeOverride override, BufferBlendOverride[] bufferBlendOverrides, String vertex, String tessControl, String tessEval, String geometry, String fragment, CustomUniforms customUniforms, DeferredWorldRenderingPipeline pipeline) {
        id = GLStateManager.glCreateProgram();

        GLStateManager.glBindAttribLocation(this.id, 0, "vPosition");

        this.bufferBlendOverrides = bufferBlendOverrides;

        GlShader vert = new GlShader(ShaderType.VERTEX, name + ".vsh", vertex);
        GLStateManager.glAttachShader(id, vert.getHandle());

        GlShader tessCont = null;
        if (tessControl != null) {
            tessCont = new GlShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", tessControl);
            GLStateManager.glAttachShader(id, tessCont.getHandle());
        }

        GlShader tessE = null;
        if (tessEval != null) {
            tessE = new GlShader(ShaderType.TESSELATION_EVAL, name + ".tes", tessEval);
            GLStateManager.glAttachShader(id, tessE.getHandle());
        }

        GlShader geom = null;
        if (geometry != null) {
            geom = new GlShader(ShaderType.GEOMETRY, name + ".gsh", geometry);
            GLStateManager.glAttachShader(id, geom.getHandle());
        }

        GlShader frag = new GlShader(ShaderType.FRAGMENT, name + ".fsh", fragment);
        GLStateManager.glAttachShader(id, frag.getHandle());

        GLStateManager.glLinkProgram(this.id);
        int status = GLStateManager.glGetProgrami(this.id, GL20.GL_LINK_STATUS);
        if (status != 1) {
            String message = "Shader link error in Iris DH program! Details: " + GLStateManager.glGetProgramInfoLog(this.id, 9999);
            this.free();
            throw new RuntimeException(message);
        } else {
            GLStateManager.glUseProgram(this.id);
        }

        vert.destroy();
        frag.destroy();

        if (tessCont != null) tessCont.destroy();
        if (tessE != null) tessE.destroy();
        if (geom != null) geom.destroy();

        blend = override;
        ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, id);
        ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(id, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
        CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
        customUniforms.assignTo(uniformBuilder);
        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
        ProgramImages.Builder builder = ProgramImages.builder(id);
        pipeline.addGbufferOrShadowSamplers(samplerBuilder, builder,
            isShadowPass ? pipeline::getFlippedBeforeShadow : () -> translucent ? pipeline.getFlippedAfterTranslucent() : pipeline.getFlippedAfterPrepare(),
            isShadowPass, true, true, false);
        customUniforms.mapholderToPass(uniformBuilder, this);
        this.uniforms = uniformBuilder.buildUniforms();
        this.customUniforms = customUniforms;
        samplers = samplerBuilder.build();
        images = builder.build();

        this.va = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(va);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GLStateManager.glEnableVertexAttribArray(0);

        projectionUniform = tryGetUniformLocation2("iris_ProjectionMatrix");
        projectionInverseUniform = tryGetUniformLocation2("iris_ProjectionMatrixInverse");
        modelViewUniform = tryGetUniformLocation2("iris_ModelViewMatrix");
        modelViewInverseUniform = tryGetUniformLocation2("iris_ModelViewMatrixInverse");
        normalMatrix3fUniform = tryGetUniformLocation2("iris_NormalMatrix");

        this.instancedShaderOffsetChunkUniform = this.tryGetUniformLocation2("uOffsetChunk");
        this.instancedShaderOffsetSubChunkUniform = this.tryGetUniformLocation2("uOffsetSubChunk");
        this.instancedShaderCameraChunkPosUniform = this.tryGetUniformLocation2("uCameraPosChunk");
        this.instancedShaderCameraSubChunkPosUniform = this.tryGetUniformLocation2("uCameraPosSubChunk");
        this.instancedShaderProjectionModelViewMatrixUniform = this.tryGetUniformLocation2("uProjectionMvm");
        this.uBlockLight = this.tryGetUniformLocation2("uBlockLight");
        this.uSkyLight = this.tryGetUniformLocation2("uSkyLight");
    }

    public static IrisGenericRenderProgram createProgram(String name, boolean isShadowPass, boolean translucent, ProgramSource source, CustomUniforms uniforms, DeferredWorldRenderingPipeline pipeline) {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchDHGeneric(
            name,
            source.getVertexSource().orElseThrow(RuntimeException::new),
            source.getTessControlSource().orElse(null),
            source.getTessEvalSource().orElse(null),
            source.getGeometrySource().orElse(null),
            source.getFragmentSource().orElseThrow(RuntimeException::new),
            pipeline.getTextureMap());
        String vertex = transformed.get(PatchShaderType.VERTEX);
        String tessControl = transformed.get(PatchShaderType.TESS_CONTROL);
        String tessEval = transformed.get(PatchShaderType.TESS_EVAL);
        String geometry = transformed.get(PatchShaderType.GEOMETRY);
        String fragment = transformed.get(PatchShaderType.FRAGMENT);
        /*ShaderPrinter.printProgram(name + "_g")
            .addSources(transformed)
            .setName("dh_" + name + "_g")
            .print();*/

        List<BufferBlendOverride> bufferOverrides = new ArrayList<>();

        source.getDirectives().getBufferBlendOverrides().forEach(information -> {
            int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.getIndex());
            if (index > -1) {
                bufferOverrides.add(new BufferBlendOverride(index, information.getBlendMode()));
            }
        });

        return new IrisGenericRenderProgram(name, isShadowPass, translucent, source.getDirectives().getBlendModeOverride().orElse(null), bufferOverrides.toArray(BufferBlendOverride[]::new), vertex, tessControl, tessEval, geometry, fragment, uniforms, pipeline);
    }

    // Noise Uniforms

    private static int getChunkPosFromDouble(double value) {
        return (int) Math.floor(value / 16);
    }

    private static float getSubChunkPosFromDouble(double value) {
        double chunkPos = Math.floor(value / 16);
        return (float) (value - chunkPos * 16);
    }

    public int tryGetUniformLocation2(CharSequence name) {
        return GLStateManager.glGetUniformLocation(this.id, name);
    }

    public void setUniform(int index, Matrix4f matrix) {
        if (index == -1 || matrix == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.callocFloat(16);
            matrix.get(buffer);
            buffer.rewind();

            GLStateManager.glUniformMatrix4(index, false, buffer);
        }
    }

    public void setUniform(int index, Matrix3f matrix) {
        if (index == -1) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.callocFloat(9);
            matrix.get(buffer);
            buffer.rewind();

            GLStateManager.glUniformMatrix3(index, false, buffer);
        }
    }

    // Override ShaderProgram.bind()
    public void bind(DhApiRenderParam renderParam) {
        GLStateManager.glBindVertexArray(va);
        GLStateManager.glUseProgram(id);
        if (blend != null) blend.apply();

        for (BufferBlendOverride override : bufferBlendOverrides) {
            override.apply();
        }

        toJOML(tempModel, renderParam.dhModelViewMatrix);
        toJOML(tempProj, renderParam.dhProjectionMatrix);

        setUniform(modelViewUniform, tempModel);
        setUniform(projectionUniform, tempProj);
        this.setUniform(this.instancedShaderProjectionModelViewMatrixUniform, tempMat4.set(tempProj).mul(tempModel));
        tempModel.invert();
        tempProj.invert();
        setUniform(modelViewInverseUniform, tempModel);
        setUniform(projectionInverseUniform, tempProj);
        setUniform(normalMatrix3fUniform, tempModel.transpose3x3(tempMat3));
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + IrisSamplers.LIGHTMAP_TEXTURE_UNIT);
        DynamicTexture lightmapTexture = ((EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer).getLightmapTexture();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture.getGlTextureId());

        samplers.update();
        uniforms.update();

        customUniforms.push(this);

        images.update();
    }

    public void unbind() {
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glUseProgram(0);
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        BlendModeOverride.restore();
    }

    @Override
    public void bindVertexBuffer(int i) {
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, i);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
    }

    @Override
    public boolean overrideThisFrame() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    @Override
    public int getId() {
        return id;
    }

    public void free() {
        GLStateManager.glDeleteProgram(id);
    }

    public void fillIndirectUniformData(DhApiRenderParam dhApiRenderParam, DhApiRenderableBoxGroupShading dhApiRenderableBoxGroupShading, IDhApiRenderableBoxGroup boxGroup, DhApiVec3d camPos) {
        bind(dhApiRenderParam);
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        this.setUniform(this.instancedShaderOffsetChunkUniform,
            new DhApiVec3i(
                getChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
                getChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
                getChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
            ));
        this.setUniform(this.instancedShaderOffsetSubChunkUniform,
            new DhApiVec3f(
                getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().x),
                getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().y),
                getSubChunkPosFromDouble(boxGroup.getOriginBlockPos().z)
            ));

        this.setUniform(this.instancedShaderCameraChunkPosUniform,
            new DhApiVec3i(
                getChunkPosFromDouble(camPos.x),
                getChunkPosFromDouble(camPos.y),
                getChunkPosFromDouble(camPos.z)
            ));
        this.setUniform(this.instancedShaderCameraSubChunkPosUniform,
            new DhApiVec3f(
                getSubChunkPosFromDouble(camPos.x),
                getSubChunkPosFromDouble(camPos.y),
                getSubChunkPosFromDouble(camPos.z)
            ));
        this.setUniform(this.uBlockLight,
            boxGroup.getBlockLight());
        this.setUniform(this.uSkyLight,
            boxGroup.getSkyLight());

    }

    @Override
    public void fillSharedDirectUniformData(DhApiRenderParam dhApiRenderParam, DhApiRenderableBoxGroupShading dhApiRenderableBoxGroupShading, IDhApiRenderableBoxGroup iDhApiRenderableBoxGroup, DhApiVec3d dhApiVec3d) {
        throw new IllegalStateException("Only indirect is supported with Iris.");
    }

    @Override
    public void fillDirectUniformData(DhApiRenderParam dhApiRenderParam, IDhApiRenderableBoxGroup iDhApiRenderableBoxGroup, DhApiRenderableBox dhApiRenderableBox, DhApiVec3d dhApiVec3d) {
        throw new IllegalStateException("Only indirect is supported with Iris.");
    }

    private void toJOML(Matrix4f target, DhApiMat4f mat4f) {
        target.setTransposed(mat4f.getValuesAsArray());
    }

    private void setUniform(int index, int value) {
        GLStateManager.glUniform1i(index, value);
    }

    private void setUniform(int index, float value) {
        GLStateManager.glUniform1f(index, value);
    }

    private void setUniform(int index, DhApiVec3f pos) {
        GLStateManager.glUniform3f(index, pos.x, pos.y, pos.z);
    }

    private void setUniform(int index, DhApiVec3i pos) {
        GLStateManager.glUniform3i(index, pos.x, pos.y, pos.z);
    }

}
