package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_CLIP_PLANES;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMEnable {

    private GLSMEnable() {}

    public static void glEnable(int cap) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].enable();
            glCtx.clipPlaneGeneration++;
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> GLSMDepthStencilAlpha.enableAlphaTest(glCtx);
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.enable();
            case GL11.GL_BLEND -> GLSMBlend.enableBlend(glCtx);
            case GL11.GL_COLOR_MATERIAL -> GLSMLighting.enableColorMaterial(glCtx);
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.enable();
            case GL14.GL_COLOR_SUM -> glCtx.colorSumState.enable();
            case GL11.GL_CULL_FACE -> GLSMRasterizer.enableCull(glCtx);
            case GL11.GL_DEPTH_TEST -> GLSMDepthStencilAlpha.enableDepthTest(glCtx);
            case GL11.GL_DITHER -> glCtx.ditherState.enable();
            case GL11.GL_FOG -> GLSMFog.enableFog(glCtx);
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.enable();
            case GL11.GL_LIGHTING -> GLSMLighting.enableLighting(glCtx);
            case GL11.GL_LIGHT0 -> GLSMLighting.enableLight(glCtx, 0);
            case GL11.GL_LIGHT1 -> GLSMLighting.enableLight(glCtx, 1);
            case GL11.GL_LIGHT2 -> GLSMLighting.enableLight(glCtx, 2);
            case GL11.GL_LIGHT3 -> GLSMLighting.enableLight(glCtx, 3);
            case GL11.GL_LIGHT4 -> GLSMLighting.enableLight(glCtx, 4);
            case GL11.GL_LIGHT5 -> GLSMLighting.enableLight(glCtx, 5);
            case GL11.GL_LIGHT6 -> GLSMLighting.enableLight(glCtx, 6);
            case GL11.GL_LIGHT7 -> GLSMLighting.enableLight(glCtx, 7);
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.enable();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.enable();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.enable();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.enable();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.enable();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.enable();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.enable();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.enable();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.enable();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.enable();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.enable();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.enable();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.enable();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.enable();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.enable();
            case GL20.GL_POINT_SPRITE -> glCtx.pointSpriteState.enable();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> glCtx.vertexProgramTwoSideState.enable();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.enable();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.enable();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.enable();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.enable();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.enable();
            case GL12.GL_RESCALE_NORMAL -> GLSMLighting.enableRescaleNormal(glCtx);
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.enable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.enable();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.enable();
            case GL30.GL_RASTERIZER_DISCARD -> glCtx.rasterizerDiscard.enable();
            case GL11.GL_SCISSOR_TEST -> GLSMRasterizer.enableScissorTest(glCtx);
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.enable();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_2D -> GLSMTextures.enableTexture(glCtx);
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL13.GL_TEXTURE_CUBE_MAP -> glCtx.textures.getTextureCubeMapStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).enable();
            default -> {
                glCtx.exitCapture.reject();
                if (!GLSMVertexInput.setClientArrayCap(glCtx, cap, true)) RENDER_BACKEND.enable(cap);
            }
        }
    }

    public static void glDisable(int cap) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].disable();
            glCtx.clipPlaneGeneration++;
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> GLSMDepthStencilAlpha.disableAlphaTest(glCtx);
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.disable();
            case GL11.GL_BLEND -> GLSMBlend.disableBlend(glCtx);
            case GL11.GL_COLOR_MATERIAL -> GLSMLighting.disableColorMaterial(glCtx);
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.disable();
            case GL14.GL_COLOR_SUM -> glCtx.colorSumState.disable();
            case GL11.GL_CULL_FACE -> GLSMRasterizer.disableCull(glCtx);
            case GL11.GL_DEPTH_TEST -> GLSMDepthStencilAlpha.disableDepthTest(glCtx);
            case GL11.GL_DITHER -> glCtx.ditherState.disable();
            case GL11.GL_FOG -> GLSMFog.disableFog(glCtx);
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.disable();
            case GL11.GL_LIGHTING -> GLSMLighting.disableLighting(glCtx);
            case GL11.GL_LIGHT0 -> GLSMLighting.disableLight(glCtx, 0);
            case GL11.GL_LIGHT1 -> GLSMLighting.disableLight(glCtx, 1);
            case GL11.GL_LIGHT2 -> GLSMLighting.disableLight(glCtx, 2);
            case GL11.GL_LIGHT3 -> GLSMLighting.disableLight(glCtx, 3);
            case GL11.GL_LIGHT4 -> GLSMLighting.disableLight(glCtx, 4);
            case GL11.GL_LIGHT5 -> GLSMLighting.disableLight(glCtx, 5);
            case GL11.GL_LIGHT6 -> GLSMLighting.disableLight(glCtx, 6);
            case GL11.GL_LIGHT7 -> GLSMLighting.disableLight(glCtx, 7);
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.disable();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.disable();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.disable();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.disable();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.disable();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.disable();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.disable();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.disable();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.disable();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.disable();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.disable();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.disable();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.disable();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.disable();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.disable();
            case GL20.GL_POINT_SPRITE -> glCtx.pointSpriteState.disable();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> glCtx.vertexProgramTwoSideState.disable();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.disable();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.disable();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.disable();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.disable();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.disable();
            case GL12.GL_RESCALE_NORMAL -> GLSMLighting.disableRescaleNormal(glCtx);
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.disable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.disable();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.disable();
            case GL30.GL_RASTERIZER_DISCARD -> glCtx.rasterizerDiscard.disable();
            case GL11.GL_SCISSOR_TEST -> GLSMRasterizer.disableScissorTest(glCtx);
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.disable();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_2D -> GLSMTextures.disableTexture(glCtx);
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL13.GL_TEXTURE_CUBE_MAP -> glCtx.textures.getTextureCubeMapStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).disable();
            default -> {
                glCtx.exitCapture.reject();
                if (!GLSMVertexInput.setClientArrayCap(glCtx, cap, false)) RENDER_BACKEND.disable(cap);
            }
        }
    }

    public static boolean glIsEnabled(int cap) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getBoolean(cap);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return glCtx.clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> glCtx.alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.isEnabled();
            case GL11.GL_BLEND -> glCtx.blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> glCtx.colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.isEnabled();
            case GL14.GL_COLOR_SUM -> glCtx.colorSumState.isEnabled();
            case GL11.GL_CULL_FACE -> glCtx.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> glCtx.depthTest.isEnabled();
            case GL11.GL_DITHER -> glCtx.ditherState.isEnabled();
            case GL11.GL_FOG -> glCtx.fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> glCtx.indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> glCtx.lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> glCtx.lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> glCtx.lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> glCtx.lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> glCtx.lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> glCtx.lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> glCtx.lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> glCtx.lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> glCtx.lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> glCtx.lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> glCtx.lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> glCtx.map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> glCtx.map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> glCtx.map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> glCtx.map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> glCtx.map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> glCtx.map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> glCtx.map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> glCtx.map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> glCtx.map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> glCtx.map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> glCtx.map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> glCtx.map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> glCtx.map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> glCtx.map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> glCtx.map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> glCtx.map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> glCtx.map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> glCtx.map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> glCtx.multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> glCtx.normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> glCtx.pointSmoothState.isEnabled();
            case GL20.GL_POINT_SPRITE -> glCtx.pointSpriteState.isEnabled();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> glCtx.vertexProgramTwoSideState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> glCtx.polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> glCtx.polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> glCtx.polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> glCtx.polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> glCtx.polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> glCtx.rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> glCtx.sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> glCtx.sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> glCtx.sampleCoverageState.isEnabled();
            case GL30.GL_RASTERIZER_DISCARD -> glCtx.rasterizerDiscard.isEnabled();
            case GL11.GL_SCISSOR_TEST -> glCtx.scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> glCtx.stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> glCtx.textures.getTexture1DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> glCtx.textures.getTextureUnitStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> glCtx.textures.getTexture3DStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL13.GL_TEXTURE_CUBE_MAP -> glCtx.textures.getTextureCubeMapStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> glCtx.textures.getTexGenSStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> glCtx.textures.getTexGenTStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> glCtx.textures.getTexGenRStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> glCtx.textures.getTexGenQStates(glCtx.activeTextureUnit.getValue()).isEnabled();
            default -> {
                final int conv = GLSMVertexInput.clientStateToConventional(glCtx, cap);
                yield conv >= 0 ? glCtx.vaos.isConventionalEnabled(conv) : LegacyQueries.desc(cap) == 0 && RENDER_BACKEND.getBoolean(cap);
            }
        };
    }

    public static void glEnablei(int cap, int index) {
        rejectExitCapture();
        RENDER_BACKEND.enablei(cap, index);
        markIndexedCapUnknown(cap);
    }

    public static void glDisablei(int cap, int index) {
        rejectExitCapture();
        RENDER_BACKEND.disablei(cap, index);
        markIndexedCapUnknown(cap);
    }

    private static void markIndexedCapUnknown(int cap) {
        if (cap == GL11.GL_BLEND) {
            ctx().blendMode.setUnknownState();
        } else if (cap == GL11.GL_SCISSOR_TEST) {
            ctx().scissorTest.setUnknownState();
        }
    }
}
