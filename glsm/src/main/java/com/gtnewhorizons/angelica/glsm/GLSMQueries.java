package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.HAS_MULTIPLE_SET;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_ATTRIB_STACK_DEPTH;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_CLIP_PLANES;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_LEGACY_TEXTURE_UNITS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_MODELVIEW_STACK_DEPTH;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_PROJECTION_STACK_DEPTH;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_COORDS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_STACK_DEPTH;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.initConfig;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMQueries {

    private GLSMQueries() {}

    public static boolean glGetBoolean(int pname) {
        rejectExitCapture();
        return glGetBoolean(ctx(), pname);
    }

    static boolean glGetBoolean(GLContextState glCtx, int pname) {
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getBoolean(pname);
        }
        // Handle clip planes dynamically (supports up to MAX_CLIP_PLANES)
        if (pname >= GL11.GL_CLIP_PLANE0 && pname < GL11.GL_CLIP_PLANE0 + MAX_CLIP_PLANES) {
            return glCtx.clipPlaneStates[pname - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST -> glCtx.alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> glCtx.autoNormalState.isEnabled();
            case GL11.GL_BLEND -> glCtx.blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> glCtx.colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> glCtx.colorLogicOpState.isEnabled();
            case GL14.GL_COLOR_SUM -> glCtx.colorSumState.isEnabled();
            case GL11.GL_CULL_FACE -> glCtx.cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> glCtx.depthTest.isEnabled();
            case GL11.GL_DEPTH_WRITEMASK -> glCtx.depthState.isEnabled();
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
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.localViewer != 0.0f;
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.twoSide != 0.0f;
            case GL11.GL_EDGE_FLAG -> glCtx.edgeFlagState.isEnabled();
            default -> {
                final int conv = GLSMVertexInput.clientStateToConventional(glCtx, pname);
                if (conv >= 0) yield glCtx.vaos.isConventionalEnabled(conv);
                final int desc = LegacyQueries.desc(pname);
                yield desc != 0 ? LegacyQueries.getBoolean(glCtx, pname, desc) : RENDER_BACKEND.getBoolean(pname);
            }
        };
    }

    public static void glGetBoolean(int pname, ByteBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getBoolean(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_COLOR_WRITEMASK -> {
                final int pos = params.position();
                params.put(pos, (byte) (glCtx.colorMask.red ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 1, (byte) (glCtx.colorMask.green ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 2, (byte) (glCtx.colorMask.blue ? GL11.GL_TRUE : GL11.GL_FALSE));
                params.put(pos + 3, (byte) (glCtx.colorMask.alpha ? GL11.GL_TRUE : GL11.GL_FALSE));
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(params.position(), (byte) (glGetBoolean(glCtx, pname) ? GL11.GL_TRUE : GL11.GL_FALSE));
                } else if (!LegacyQueries.getBooleanv(glCtx, pname, params)) {
                    RENDER_BACKEND.getBoolean(pname, params);
                }
            }
        }
    }

    public static int glGetInteger(int pname) {
        rejectExitCapture();
        return glGetInteger(ctx(), pname);
    }

    static int glGetInteger(GLContextState glCtx, int pname) {
        if (!isCachingEnabled()) {
            return RENDER_BACKEND.getInteger(pname);
        }

        return switch (pname) {
            case GL11.GL_ALPHA_TEST_FUNC -> glCtx.alphaState.getFunction();
            case GL11.GL_DEPTH_FUNC -> glCtx.depthState.getFunc();
            case GL11.GL_LIST_BASE -> glCtx.listBase;
            case GL11.GL_LIST_MODE -> DisplayListManager.getListMode();
            case GL11.GL_MATRIX_MODE -> glCtx.matrixMode.getMode();
            case GL11.GL_SHADE_MODEL -> glCtx.shadeModelState.getValue();
            // GL_TEXTURE_2D makes no sense here, but some mod still queries it...
            case GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BINDING_2D -> GLSMTextures.getBoundTextureForServerState();
            case GL13.GL_ACTIVE_TEXTURE -> GL13.GL_TEXTURE0 + GLSMTextures.getActiveTextureUnitForServerState();
            case GL13.GL_CLIENT_ACTIVE_TEXTURE -> GL13.GL_TEXTURE0 + glCtx.clientActiveTextureUnit;
            case GL11.GL_COLOR_MATERIAL_FACE -> glCtx.colorMaterialFace.getValue();
            case GL11.GL_COLOR_MATERIAL_PARAMETER -> glCtx.colorMaterialParameter.getValue();
            case GL11.GL_MODELVIEW_STACK_DEPTH -> GLSMMatrices.getMatrixStackDepth(glCtx.modelViewMatrix);
            case GL11.GL_PROJECTION_STACK_DEPTH -> GLSMMatrices.getMatrixStackDepth(glCtx.projectionMatrix);
            case GL11.GL_DEPTH_WRITEMASK -> glCtx.depthState.isEnabled() ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL11.GL_CULL_FACE_MODE -> glCtx.polygonState.getCullFaceMode();
            case GL11.GL_FRONT_FACE -> glCtx.polygonState.getFrontFace();
            case GL11.GL_POLYGON_MODE -> glCtx.polygonState.getFrontMode();
            case GL11.GL_UNPACK_ALIGNMENT -> glCtx.pixelUnpackState.alignment();
            case GL11.GL_UNPACK_ROW_LENGTH -> glCtx.pixelUnpackState.rowLength();
            case GL11.GL_UNPACK_SKIP_ROWS -> glCtx.pixelUnpackState.skipRows();
            case GL11.GL_UNPACK_SKIP_PIXELS -> glCtx.pixelUnpackState.skipPixels();

            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.colorControl;

            case GL14.GL_BLEND_DST_ALPHA -> glCtx.blendState.getDstAlpha();
            case GL11.GL_BLEND_DST, GL14.GL_BLEND_DST_RGB -> glCtx.blendState.getDstRgb();
            case GL14.GL_BLEND_SRC_ALPHA -> glCtx.blendState.getSrcAlpha();
            case GL11.GL_BLEND_SRC, GL14.GL_BLEND_SRC_RGB -> glCtx.blendState.getSrcRgb();
            case GL14.GL_BLEND_EQUATION -> glCtx.blendState.getEquationRgb();
            case GL20.GL_BLEND_EQUATION_ALPHA -> glCtx.blendState.getEquationAlpha();

            case GL11.GL_LOGIC_OP_MODE -> glCtx.logicOpMode.getValue();
            case GL11.GL_DRAW_BUFFER -> glCtx.drawBuffer.getValue();

            case GL11.GL_STENCIL_FUNC -> glCtx.stencilState.getFuncFront();
            case GL11.GL_STENCIL_REF -> glCtx.stencilState.getRefFront();
            case GL11.GL_STENCIL_VALUE_MASK -> glCtx.stencilState.getValueMaskFront();
            case GL11.GL_STENCIL_FAIL -> glCtx.stencilState.getFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_FAIL -> glCtx.stencilState.getZFailOpFront();
            case GL11.GL_STENCIL_PASS_DEPTH_PASS -> glCtx.stencilState.getZPassOpFront();
            case GL11.GL_STENCIL_WRITEMASK -> glCtx.stencilState.getWriteMaskFront();
            case GL11.GL_STENCIL_CLEAR_VALUE -> glCtx.stencilState.getClearValue();

            case GL15.GL_ARRAY_BUFFER_BINDING -> glCtx.boundVBO;
            case GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING -> glCtx.vaos.boundEBO;
            case GL21.GL_PIXEL_UNPACK_BUFFER_BINDING -> glCtx.boundPixelUnpackBuffer;
            case GL21.GL_PIXEL_PACK_BUFFER_BINDING -> glCtx.boundPixelPackBuffer;

            case GL20.GL_CURRENT_PROGRAM -> glCtx.activeProgram;

            case GL30.GL_VERTEX_ARRAY_BINDING -> glCtx.boundVAO;

            case GL30.GL_DRAW_FRAMEBUFFER_BINDING -> glCtx.drawFramebuffer;
            case GL30.GL_READ_FRAMEBUFFER_BINDING -> glCtx.readFramebuffer;

            case GL33.GL_SAMPLER_BINDING -> glCtx.samplerUnits.get(glCtx.activeTextureUnit.getValue());

            default -> switch (pname) {
                case GL11.GL_FOG_MODE -> glCtx.fogState.getFogMode();
                case GL11.GL_LINE_STIPPLE_PATTERN -> glCtx.lineState.getStipplePattern() & 0xFFFF;
                case GL11.GL_LINE_STIPPLE_REPEAT -> glCtx.lineState.getStippleFactor();
                case GL11.GL_LIST_INDEX -> DisplayListManager.isRecording() ? Math.max(DisplayListManager.getRecordingListId(), 0) : 0;
                case GL11.GL_ATTRIB_STACK_DEPTH -> glCtx.attribDepth;
                case GL11.GL_CLIENT_ATTRIB_STACK_DEPTH -> glCtx.clientAttribStackPointer;
                case GL11.GL_TEXTURE_STACK_DEPTH -> GLSMMatrices.getMatrixStackDepth(glCtx.textures.getTextureUnitMatrix(glCtx.activeTextureUnit.getValue()));
                case GL11.GL_MAX_ATTRIB_STACK_DEPTH -> MAX_ATTRIB_STACK_DEPTH;
                case GL11.GL_MAX_CLIENT_ATTRIB_STACK_DEPTH -> GLSMAttribStack.CLIENT_ATTRIB_STACK_DEPTH;
                case GL11.GL_MAX_MODELVIEW_STACK_DEPTH -> MAX_MODELVIEW_STACK_DEPTH;
                case GL11.GL_MAX_PROJECTION_STACK_DEPTH -> MAX_PROJECTION_STACK_DEPTH;
                case GL11.GL_MAX_TEXTURE_STACK_DEPTH -> MAX_TEXTURE_STACK_DEPTH;
                case GL13.GL_MAX_TEXTURE_UNITS -> MAX_LEGACY_TEXTURE_UNITS;
                case GL11.GL_MAX_LIGHTS -> glCtx.lightStates.length;
                case GL11.GL_STENCIL_BITS -> GLSMFramebuffer.drawFramebufferBits(glCtx, true);
                case GL11.GL_DEPTH_BITS -> GLSMFramebuffer.drawFramebufferBits(glCtx, false);
                case GL20.GL_MAX_TEXTURE_COORDS -> MAX_TEXTURE_COORDS;
                case GL20.GL_POINT_SPRITE_COORD_ORIGIN -> glCtx.pointState.getSpriteCoordOrigin();
                case GL11.GL_RENDER_MODE -> FeedbackManager.getRenderMode();
                default -> {
                    final int shift = GLSMRasterizer.hintShift(pname);
                    if (shift >= 0) yield GLSMRasterizer.hintMode(glCtx.hintState.getValue(), shift);
                    final int key = GLSMVertexInput.clientArrayField(glCtx, pname);
                    if (key >= 0 && (key & ~0xFF) != GLSMVertexInput.FIELD_POINTER) yield GLSMVertexInput.clientArrayParam(glCtx, key);
                    final int desc = LegacyQueries.desc(pname);
                    yield desc != 0 ? LegacyQueries.getInteger(glCtx, pname, desc) : RENDER_BACKEND.getInteger(pname);
                }
            };
        };
    }

    public static void glGetInteger(int pname, IntBuffer params) {
        rejectExitCapture();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getInteger(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> ctx().viewportState.get(params);
            case GL11.GL_SCISSOR_BOX -> ctx().scissorState.get(params);
            case GL11.GL_POLYGON_MODE -> {
                final PolygonState polygon = ctx().polygonState;
                final int pos = params.position();
                params.put(pos, polygon.getFrontMode());
                params.put(pos + 1, polygon.getBackMode());
            }
            default -> {
                final GLContextState glCtx = ctx();
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(params.position(), glGetInteger(glCtx, pname));
                } else if (!LegacyQueries.getIntegerv(glCtx, pname, params)) {
                    RENDER_BACKEND.getInteger(pname, params);
                }
            }
        }
    }

    public static void glGetFloat(int pname, FloatBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getFloat(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_MODELVIEW_MATRIX -> glCtx.modelViewMatrix.get(params.position(), params);
            case GL11.GL_PROJECTION_MATRIX -> glCtx.projectionMatrix.get(params.position(), params);
            case GL11.GL_TEXTURE_MATRIX -> glCtx.textures.getTextureUnitMatrix(GLSMTextures.getActiveTextureUnit()).get(params.position(), params);
            case GL11.GL_COLOR_CLEAR_VALUE -> glCtx.clearColor.get(params);
            case GL11.GL_CURRENT_COLOR -> glCtx.color.get(params);
            case GL14.GL_CURRENT_SECONDARY_COLOR -> glCtx.secondaryColor.get(params);
            case GL11.GL_DEPTH_RANGE -> {
                final int pos = params.position();
                params.put(pos, (float) glCtx.viewportState.depthRangeNear);
                params.put(pos + 1, (float) glCtx.viewportState.depthRangeFar);
            }
            case GL14.GL_BLEND_COLOR -> {
                final int pos = params.position();
                params.put(pos, glCtx.blendState.getBlendColorR());
                params.put(pos + 1, glCtx.blendState.getBlendColorG());
                params.put(pos + 2, glCtx.blendState.getBlendColorB());
                params.put(pos + 3, glCtx.blendState.getBlendColorA());
            }
            case GL11.GL_CURRENT_NORMAL -> {
                final Vector3f normal = glCtx.ffp.currentNormal;
                final int pos = params.position();
                params.put(pos, normal.x);
                params.put(pos + 1, normal.y);
                params.put(pos + 2, normal.z);
            }
            case GL11.GL_CURRENT_TEXTURE_COORDS -> {
                final int unit = glCtx.activeTextureUnit.getValue();
                final int pos = params.position();
                if (unit == 1) {
                    params.put(pos, glCtx.lastBrightnessX);
                    params.put(pos + 1, glCtx.lastBrightnessY);
                    params.put(pos + 2, 0.0f);
                    params.put(pos + 3, 1.0f);
                } else if (unit >= 0 && unit < glCtx.ffp.currentTexCoords.length) {
                    final Vector4f tc = glCtx.ffp.currentTexCoords[unit];
                    params.put(pos, tc.x);
                    params.put(pos + 1, tc.y);
                    params.put(pos + 2, tc.z);
                    params.put(pos + 3, tc.w);
                } else {
                    params.put(pos, 0.0f);
                    params.put(pos + 1, 0.0f);
                    params.put(pos + 2, 0.0f);
                    params.put(pos + 3, 1.0f);
                }
            }
            case GL11.GL_FOG_COLOR -> {
                final FloatBuffer fogBuf = glCtx.fogState.getFogColorBuffer();
                final int pos = params.position();
                params.put(pos, fogBuf.get(0));
                params.put(pos + 1, fogBuf.get(1));
                params.put(pos + 2, fogBuf.get(2));
                params.put(pos + 3, fogBuf.get(3));
            }
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.ambient.get(params.position(), params);
            case GL14.GL_POINT_DISTANCE_ATTENUATION -> {
                final int pos = params.position();
                params.put(pos, glCtx.pointState.getAttenuationConstant());
                params.put(pos + 1, glCtx.pointState.getAttenuationLinear());
                params.put(pos + 2, glCtx.pointState.getAttenuationQuadratic());
            }
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(params.position(), glGetFloat(glCtx, pname));
                } else {
                    RENDER_BACKEND.getFloat(pname, params);
                }
            }
        }
    }

    public static float glGetFloat(int pname) {
        rejectExitCapture();
        return glGetFloat(ctx(), pname);
    }

    static float glGetFloat(GLContextState glCtx, int pname) {
        return switch (pname) {
            case GL11.GL_ALPHA_TEST_REF -> glCtx.alphaState.getReference();
            case GL11.GL_FOG_DENSITY -> glCtx.fogState.getDensity();
            case GL11.GL_FOG_START -> glCtx.fogState.getStart();
            case GL11.GL_FOG_END -> glCtx.fogState.getEnd();
            case GL11.GL_DEPTH_CLEAR_VALUE -> (float) glCtx.depthState.getClearValue();
            case GL11.GL_LINE_WIDTH -> glCtx.lineState.getWidth();
            case GL11.GL_POINT_SIZE -> glCtx.pointState.getSize();
            case GL14.GL_POINT_SIZE_MIN -> glCtx.pointState.getMinSize();
            case GL14.GL_POINT_SIZE_MAX -> Float.isNaN(glCtx.pointState.getMaxSize()) ? GLSMRasterizer.maxPointSize : glCtx.pointState.getMaxSize();
            case GL14.GL_POINT_FADE_THRESHOLD_SIZE -> glCtx.pointState.getFadeThreshold();
            default -> {
                final int desc = isCachingEnabled() ? LegacyQueries.desc(pname) : 0;
                yield desc != 0 ? LegacyQueries.getFloat(glCtx, pname, desc) : RENDER_BACKEND.getFloat(pname);
            }
        };
    }

    public static void glGetFloat(int pname, float[] params) {
        final FloatBuffer scratch = ctx().queryScratch;
        final FloatBuffer buf;
        if (params.length <= scratch.capacity()) {
            buf = scratch;
            buf.clear();
            buf.limit(params.length);
        } else {
            buf = BufferUtils.createFloatBuffer(params.length);
        }
        glGetFloat(pname, buf);
        buf.position(0);
        buf.get(params);
    }

    public static double glGetDouble(int pname) {
        rejectExitCapture();
        return switch (pname) {
            case GL11.GL_DEPTH_CLEAR_VALUE -> ctx().depthState.getClearValue();
            default -> glGetFloat(pname);
        };
    }

    public static void glGetDouble(int pname, DoubleBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled()) {
            RENDER_BACKEND.getDouble(pname, params);
            return;
        }

        final int pos = params.position();
        switch (pname) {
            case GL11.GL_DEPTH_CLEAR_VALUE -> params.put(pos, glCtx.depthState.getClearValue());
            case GL11.GL_DEPTH_RANGE -> {
                params.put(pos, glCtx.viewportState.depthRangeNear);
                params.put(pos + 1, glCtx.viewportState.depthRangeFar);
            }
            case GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX -> widenFromFloat(pname, params, 16);
            case GL11.GL_COLOR_CLEAR_VALUE, GL11.GL_CURRENT_COLOR, GL11.GL_CURRENT_TEXTURE_COORDS, GL11.GL_FOG_COLOR,
                GL11.GL_LIGHT_MODEL_AMBIENT, GL14.GL_BLEND_COLOR, GL14.GL_CURRENT_SECONDARY_COLOR -> widenFromFloat(pname, params, 4);
            case GL11.GL_CURRENT_NORMAL, GL14.GL_POINT_DISTANCE_ATTENUATION -> widenFromFloat(pname, params, 3);
            default -> {
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params.put(pos, glGetDouble(pname));
                } else {
                    RENDER_BACKEND.getDouble(pname, params);
                }
            }
        }
    }

    public static void glGetDouble(int pname, double[] params) {
        final DoubleBuffer scratch = ctx().queryScratchDouble;
        final DoubleBuffer buf;
        if (params.length <= scratch.capacity()) {
            buf = scratch;
            buf.clear();
            buf.limit(params.length);
        } else {
            buf = BufferUtils.createDoubleBuffer(params.length);
        }
        glGetDouble(pname, buf);
        buf.position(0);
        buf.get(params);
    }

    private static void widenFromFloat(int pname, DoubleBuffer params, int count) {
        final FloatBuffer scratch = ctx().queryScratch;
        scratch.clear();
        glGetFloat(pname, scratch);
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            params.put(pos + i, scratch.get(i));
        }
    }

    public static int glGetError() {
        rejectExitCapture();
        if (!RENDER_BACKEND.hasContext()) return 0;
        if (initConfig.isNoErrorChecks()) return 0;
        return RENDER_BACKEND.getError();
    }

    public static void checkGLError() {
        final int error = glGetError();
        if (error != GL11.GL_NO_ERROR) GLErrorReporter.reportError(error);
    }

    public static String glGetString(int pname) {
        rejectExitCapture();
        if (!RENDER_BACKEND.hasContext()) return "no valid GL/render context";
        return RENDER_BACKEND.getString(pname);
    }

    public static String glGetStringi(int name, int index) {
        rejectExitCapture();
        if (!RENDER_BACKEND.hasContext()) return "no valid GL/render context";
        return RENDER_BACKEND.getStringi(name, index);
    }
    public static boolean glIsQuery(int query) { rejectExitCapture(); return RENDER_BACKEND.isQuery(query); }

    public static void glGenQueries(IntBuffer ids) {
        requireContext("glGenQueries");
        RENDER_BACKEND.genQueries(ids);
    }
    public static int glGenQueries() {
        requireContext("glGenQueries");
        return RENDER_BACKEND.genQueries();
    }
    public static void glDeleteQueries(int id) { rejectExitCapture(); RENDER_BACKEND.deleteQueries(id); }
    public static void glBeginQuery(int target, int id) { rejectExitCapture(); RENDER_BACKEND.beginQuery(target, id); }
    public static void glEndQuery(int target) { rejectExitCapture(); RENDER_BACKEND.endQuery(target); }
    public static void glGetQueryObjectui(int id, int pname, IntBuffer params) { rejectExitCapture(); RENDER_BACKEND.getQueryObjectui(id, pname, params); }
    public static int glGetQueryObjecti(int id, int pname) { rejectExitCapture(); return RENDER_BACKEND.getQueryObjecti(id, pname); }
    public static void glQueryCounter(int id, int target) { rejectExitCapture(); RENDER_BACKEND.queryCounter(id, target); }
    public static long glGetQueryObjectui64(int id, int pname) { rejectExitCapture(); return RENDER_BACKEND.getQueryObjectui64(id, pname); }

    public static void glGetInteger(int pname, int[] params) {
        rejectExitCapture();
        if (!isCachingEnabled() || params.length == 0) {
            RENDER_BACKEND.getInteger(pname, params);
            return;
        }

        switch (pname) {
            case GL11.GL_VIEWPORT -> {
                if (params.length < 4) {
                    RENDER_BACKEND.getInteger(pname, params);
                } else {
                    ctx().viewportState.get(params);
                }
            }
            case GL11.GL_SCISSOR_BOX -> {
                if (params.length < 4) {
                    RENDER_BACKEND.getInteger(pname, params);
                } else {
                    ctx().scissorState.get(params);
                }
            }
            case GL11.GL_POLYGON_MODE -> {
                final PolygonState polygon = ctx().polygonState;
                params[0] = polygon.getFrontMode();
                if (params.length > 1) params[1] = polygon.getBackMode();
            }
            default -> {
                final GLContextState glCtx = ctx();
                if (!HAS_MULTIPLE_SET.contains(pname)) {
                    params[0] = glGetInteger(glCtx, pname);
                } else if (!LegacyQueries.getIntegerv(glCtx, pname, params)) {
                    RENDER_BACKEND.getInteger(pname, params);
                }
            }
        }
    }

    private static int indexedIntegerOrBackend(int pname, int index) {
        if (pname == GL31.GL_UNIFORM_BUFFER_BINDING) return GLSMBuffers.getIndexedBufferBinding(GL31.GL_UNIFORM_BUFFER, index);
        if (pname == GL43.GL_SHADER_STORAGE_BUFFER_BINDING) return GLSMBuffers.getIndexedBufferBinding(GL43.GL_SHADER_STORAGE_BUFFER, index);
        final ImageUnitBinding binding = !isCachingEnabled() ? null : ctx().imageUnits.get(index);
        if (binding == null) return RENDER_BACKEND.getIntegerIndexed(pname, index);
        return switch (pname) {
            case GL42.GL_IMAGE_BINDING_NAME -> binding.getTexture();
            case GL42.GL_IMAGE_BINDING_LEVEL -> binding.getLevel();
            case GL42.GL_IMAGE_BINDING_LAYERED -> binding.isLayered() ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL42.GL_IMAGE_BINDING_LAYER -> binding.getLayer();
            case GL42.GL_IMAGE_BINDING_ACCESS -> binding.getAccess();
            case GL42.GL_IMAGE_BINDING_FORMAT -> binding.getFormat();
            default -> RENDER_BACKEND.getIntegerIndexed(pname, index);
        };
    }

    public static int glGetInteger(int pname, int index) {
        rejectExitCapture();
        return indexedIntegerOrBackend(pname, index);
    }

    public static int glGetIntegeri(int pname, int index) {
        rejectExitCapture();
        return indexedIntegerOrBackend(pname, index);
    }

    public static void glGetIntegeri(int pname, int index, IntBuffer params) {
        glGetIntegeri_v(pname, index, params);
    }

    public static void glGetIntegeri_v(int pname, int index, IntBuffer params) {
        rejectExitCapture();
        params.put(params.position(), indexedIntegerOrBackend(pname, index));
    }

    public static void glGetBooleani_v(int pname, int index, ByteBuffer params) {
        rejectExitCapture();
        params.put(params.position(), (byte) (indexedIntegerOrBackend(pname, index) != 0 ? GL11.GL_TRUE : GL11.GL_FALSE));
    }

    public static void glGetBooleani(int pname, int index, ByteBuffer params) {
        glGetBooleani_v(pname, index, params);
    }
}
