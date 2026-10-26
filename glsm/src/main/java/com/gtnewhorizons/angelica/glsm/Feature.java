package com.gtnewhorizons.angelica.glsm;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.HashSet;
import java.util.Set;

public class Feature {
    private static final int[] supportedAttribs = new int[] { GL11.GL_ACCUM_BUFFER_BIT, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_CURRENT_BIT, GL11.GL_DEPTH_BUFFER_BIT,
        GL11.GL_ENABLE_BIT, GL11.GL_EVAL_BIT, GL11.GL_FOG_BIT, GL11.GL_HINT_BIT, GL11.GL_LIGHTING_BIT, GL11.GL_LINE_BIT, GL11.GL_LIST_BIT,
        GL13.GL_MULTISAMPLE_BIT, GL11.GL_PIXEL_MODE_BIT, GL11.GL_POINT_BIT, GL11.GL_POLYGON_BIT, GL11.GL_POLYGON_STIPPLE_BIT, GL11.GL_SCISSOR_BIT,
        GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_TEXTURE_BIT, GL11.GL_TRANSFORM_BIT, GL11.GL_VIEWPORT_BIT };

    private static final long[] NO_MEMBERS = new long[0];

    private static final Int2ObjectMap<long[]> maskToMembersMap = new Int2ObjectOpenHashMap<>();

    static long[] maskToMembers(int mask) {
        final long[] cached = maskToMembersMap.get(mask);
        if (cached != null) {
            return cached;
        }

        long[] result = NO_MEMBERS;
        for (int attrib : Feature.supportedAttribs) {
            if ((mask & attrib) == attrib) {
                result = union(result, attribToMembers.get(attrib));
            }
        }

        maskToMembersMap.put(mask, result);
        return result;
    }

    private static long[] union(long[] a, long[] b) {
        if (b == null || b.length == 0) return a;
        if (a.length == 0) return b.clone();
        final long[] result = new long[Math.max(a.length, b.length)];
        System.arraycopy(a, 0, result, 0, a.length);
        for (int i = 0; i < b.length; i++) result[i] |= b[i];
        return result;
    }

    private static long[] toMembers(Set<IStateStack<?>> stacks) {
        if (stacks.isEmpty()) return NO_MEMBERS;
        int max = 0;
        for (IStateStack<?> s : stacks) max = Math.max(max, ((CowStateStack<?>) s).stackId());
        final long[] bits = new long[(max >> 6) + 1];
        for (IStateStack<?> s : stacks) {
            final int id = ((CowStateStack<?>) s).stackId();
            bits[id >> 6] |= 1L << id;
        }
        return bits;
    }

    private static final Int2ObjectMap<long[]> attribToMembers = new Int2ObjectOpenHashMap<>();

    /**
     * Helper method to add all texture gen states (S, T, R, Q) for all texture units to a collection.
     * Used by both GL_ENABLE_BIT and GL_TEXTURE_BIT.
     */
    private static void addTextureGenStates(Set<IStateStack<?>> collection) {
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            collection.add(GLSMTextures.getTextures().getTexGenSStates(i));
            collection.add(GLSMTextures.getTextures().getTexGenTStates(i));
            collection.add(GLSMTextures.getTextures().getTexGenRStates(i));
            collection.add(GLSMTextures.getTextures().getTexGenQStates(i));
        }
    }

    static {
        final Int2ObjectMap<Set<IStateStack<?>>> attribToFeatures = new Int2ObjectOpenHashMap<>();

        attribToFeatures.put(GL11.GL_COLOR_BUFFER_BIT, ImmutableSet.of(
              GLSMDepthStencilAlpha.getAlphaTest()
            , GLSMDepthStencilAlpha.getAlphaState()
            , GLSMBlend.getBlendMode()
            , GLSMBlend.getBlendState()
            , GLSMBlend.getColorLogicOpState()
            , GLSMRasterizer.getDitherState()
            , GLSMFramebuffer.getDrawBuffer()
            , GLSMBlend.getIndexLogicOpState()
            , GLSMBlend.getLogicOpMode()
            , GLSMBlend.getColorMask()
            , GLSMBlend.getClearColor()
        ));
        attribToFeatures.put(GL11.GL_CURRENT_BIT, ImmutableSet.of(
              GLSMCurrentAttribs.getColor()
            , GLSMCurrentAttribs.getSecondaryColor() // Current secondary color
            , ShaderManager.getNormalStack()    // Current normal vector
            , ShaderManager.getTexCoordStack()  // Current texture coordinates
            , GLSMRasterizer.getEdgeFlagState()
            // Current color index
            // Current raster position
            // GL_CURRENT_RASTER_POSITION_VALID flag
            // RGBA color associated with current raster position
            // Color index associated with current raster position
            // Texture coordinates associated with current raster position
            // GL_EDGE_FLAG flag
        ));
        attribToFeatures.put(GL11.GL_DEPTH_BUFFER_BIT, ImmutableSet.of(
              GLSMDepthStencilAlpha.getDepthTest()
            , GLSMDepthStencilAlpha.getDepthState()
            // Depth buffer clear value
            // GL_DEPTH_WRITEMASK enable bit
        ));

        final HashSet<IStateStack<?>> enableBits = new HashSet<>(ImmutableSet.of(
              GLSMDepthStencilAlpha.getAlphaTest()
            , GLSMRasterizer.getAutoNormalState()
            , GLSMBlend.getBlendMode()
            , GLSMBlend.getColorLogicOpState()
            , GLSMLighting.getColorMaterial()
            , GLSMCurrentAttribs.getColorSumState()
            , GLSMRasterizer.getCullState()
            , GLSMDepthStencilAlpha.getDepthTest()
            , GLSMRasterizer.getDitherState()
            , GLSMFog.getFogMode()
            , GLSMBlend.getIndexLogicOpState()
            , GLSMLighting.getLightStates()[0] // GL_LIGHT0
            , GLSMLighting.getLightStates()[1] // GL_LIGHT1
            , GLSMLighting.getLightStates()[2] // GL_LIGHT2
            , GLSMLighting.getLightStates()[3] // GL_LIGHT3
            , GLSMLighting.getLightStates()[4] // GL_LIGHT4
            , GLSMLighting.getLightStates()[5] // GL_LIGHT5
            , GLSMLighting.getLightStates()[6] // GL_LIGHT6
            , GLSMLighting.getLightStates()[7] // GL_LIGHT7
            , GLSMLighting.getLightingState()
            , GLSMRasterizer.getLineSmoothState()
            , GLSMRasterizer.getLineStippleState()
            , GLSMRasterizer.getMap1Color4State()
            , GLSMRasterizer.getMap1IndexState()
            , GLSMRasterizer.getMap1NormalState()
            , GLSMRasterizer.getMap1TextureCoord1State()
            , GLSMRasterizer.getMap1TextureCoord2State()
            , GLSMRasterizer.getMap1TextureCoord3State()
            , GLSMRasterizer.getMap1TextureCoord4State()
            , GLSMRasterizer.getMap1Vertex3State()
            , GLSMRasterizer.getMap1Vertex4State()
            , GLSMRasterizer.getMap2Color4State()
            , GLSMRasterizer.getMap2IndexState()
            , GLSMRasterizer.getMap2NormalState()
            , GLSMRasterizer.getMap2TextureCoord1State()
            , GLSMRasterizer.getMap2TextureCoord2State()
            , GLSMRasterizer.getMap2TextureCoord3State()
            , GLSMRasterizer.getMap2TextureCoord4State()
            , GLSMRasterizer.getMap2Vertex3State()
            , GLSMRasterizer.getMap2Vertex4State()
            , GLSMRasterizer.getMultisampleState()
            , GLSMLighting.getNormalizeState()
            , GLSMRasterizer.getPointSmoothState()
            , GLSMRasterizer.getPolygonOffsetPointState()
            , GLSMRasterizer.getPolygonOffsetLineState()
            , GLSMRasterizer.getPolygonOffsetFillState()
            , GLSMRasterizer.getPointSpriteState()
            , GLSMPrograms.getVertexProgramTwoSideState()
            , GLSMRasterizer.getPolygonSmoothState()
            , GLSMRasterizer.getPolygonStippleState()
            , GLSMLighting.getRescaleNormalState()
            , GLSMRasterizer.getSampleAlphaToCoverageState()
            , GLSMRasterizer.getSampleAlphaToOneState()
            , GLSMRasterizer.getSampleCoverageState()
            , GLSMRasterizer.getScissorTest()
            , GLSMDepthStencilAlpha.getStencilTest()
        ));

        // Enable bits for the user-definable clipping planes
        for(int i = 0; i < GLSMRasterizer.getClipPlaneStates().length; i++) {
            enableBits.add(GLSMRasterizer.getClipPlaneStates()[i]);
        }

        // GL_TEXTURE_1D, GL_TEXTURE_2D, GL_TEXTURE_3D flags
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            enableBits.add(GLSMTextures.getTextures().getTexture1DStates(i));
            enableBits.add(GLSMTextures.getTextures().getTextureUnitStates(i));
            enableBits.add(GLSMTextures.getTextures().getTexture3DStates(i));
            enableBits.add(GLSMTextures.getTextures().getTextureCubeMapStates(i));
        }

        // Flags GL_TEXTURE_GEN_x where x is S, T, R, or Q
        addTextureGenStates(enableBits);

        attribToFeatures.put(GL11.GL_ENABLE_BIT, enableBits);
        attribToFeatures.put(GL11.GL_EVAL_BIT, ImmutableSet.of(
              GLSMRasterizer.getAutoNormalState()
            , GLSMRasterizer.getMap1Color4State()
            , GLSMRasterizer.getMap1IndexState()
            , GLSMRasterizer.getMap1NormalState()
            , GLSMRasterizer.getMap1TextureCoord1State()
            , GLSMRasterizer.getMap1TextureCoord2State()
            , GLSMRasterizer.getMap1TextureCoord3State()
            , GLSMRasterizer.getMap1TextureCoord4State()
            , GLSMRasterizer.getMap1Vertex3State()
            , GLSMRasterizer.getMap1Vertex4State()
            , GLSMRasterizer.getMap2Color4State()
            , GLSMRasterizer.getMap2IndexState()
            , GLSMRasterizer.getMap2NormalState()
            , GLSMRasterizer.getMap2TextureCoord1State()
            , GLSMRasterizer.getMap2TextureCoord2State()
            , GLSMRasterizer.getMap2TextureCoord3State()
            , GLSMRasterizer.getMap2TextureCoord4State()
            , GLSMRasterizer.getMap2Vertex3State()
            , GLSMRasterizer.getMap2Vertex4State()
        ));
        attribToFeatures.put(GL11.GL_FOG_BIT, ImmutableSet.of(
              GLSMFog.getFogMode()
            , GLSMCurrentAttribs.getColorSumState()
            , GLSMFog.getFogState()
                                       // ^^ Fog density
                                       // ^^ Linear fog start
                                       // ^^ Linear fog end
            // Fog index
                                       // ^^ GL_FOG_MODE value
        ));
        attribToFeatures.put(GL11.GL_HINT_BIT, ImmutableSet.of(
            GLSMRasterizer.getHintState()
        ));
        attribToFeatures.put(GL11.GL_LIGHTING_BIT, ImmutableSet.of(
            GLSMLighting.getColorMaterial()
            , GLSMLighting.getColorMaterialFace()
            , GLSMLighting.getColorMaterialParameter()
            , GLSMLighting.getLightModel()
            , GLSMLighting.getLightingState()
            // Enable bit for each light
            , GLSMLighting.getLightStates()[0] // GL_LIGHT0
            , GLSMLighting.getLightStates()[1] // GL_LIGHT1
            , GLSMLighting.getLightStates()[2] // GL_LIGHT2
            , GLSMLighting.getLightStates()[3] // GL_LIGHT3
            , GLSMLighting.getLightStates()[4] // GL_LIGHT4
            , GLSMLighting.getLightStates()[5] // GL_LIGHT5
            , GLSMLighting.getLightStates()[6] // GL_LIGHT6
            , GLSMLighting.getLightStates()[7] // GL_LIGHT7
            // Ambient, diffuse, and specular intensity for each light
            // Direction, position, exponent, and cutoff angle for each light
            // Constant, linear, and quadratic attenuation factors for each light
            , GLSMLighting.getLightDataStates()[0]
            , GLSMLighting.getLightDataStates()[1]
            , GLSMLighting.getLightDataStates()[2]
            , GLSMLighting.getLightDataStates()[3]
            , GLSMLighting.getLightDataStates()[4]
            , GLSMLighting.getLightDataStates()[5]
            , GLSMLighting.getLightDataStates()[6]
            , GLSMLighting.getLightDataStates()[7]
            // Ambient, diffuse, specular, and emissive color for each material
            // Ambient, diffuse, and specular color indices for each material
            // Specular exponent for each material
            , GLSMLighting.getFrontMaterial()
            , GLSMLighting.getBackMaterial()
            , GLSMRasterizer.getShadeModelState()
        ));
        attribToFeatures.put(GL11.GL_LINE_BIT, ImmutableSet.of(
              GLSMRasterizer.getLineSmoothState()
            , GLSMRasterizer.getLineStippleState()
            , GLSMRasterizer.getLineState()
        ));
        attribToFeatures.put(GL11.GL_LIST_BIT, ImmutableSet.of(
            // GL_LIST_BASE setting
        ));
        attribToFeatures.put(GL13.GL_MULTISAMPLE_BIT, ImmutableSet.of(
              GLSMRasterizer.getMultisampleState()
            , GLSMRasterizer.getSampleAlphaToCoverageState()
            , GLSMRasterizer.getSampleAlphaToOneState()
            , GLSMRasterizer.getSampleCoverageState()
            // GL_SAMPLE_COVERAGE_VALUE value
            // GL_SAMPLE_COVERAGE_INVERT value
        ));
        attribToFeatures.put(GL11.GL_PIXEL_MODE_BIT, ImmutableSet.of(
            // GL_RED_BIAS and GL_RED_SCALE settings
            // GL_GREEN_BIAS and GL_GREEN_SCALE values
            // GL_BLUE_BIAS and GL_BLUE_SCALE
            // GL_ALPHA_BIAS and GL_ALPHA_SCALE
            // GL_DEPTH_BIAS and GL_DEPTH_SCALE
            // GL_INDEX_OFFSET and GL_INDEX_SHIFT values
            // GL_MAP_COLOR and GL_MAP_STENCIL flags
            // GL_ZOOM_X and GL_ZOOM_Y factors
            // GL_READ_BUFFER setting
        ));
        attribToFeatures.put(GL11.GL_POINT_BIT, ImmutableSet.of(
              GLSMRasterizer.getPointSmoothState()
            , GLSMRasterizer.getPointSpriteState()
            , GLSMRasterizer.getPointState()
        ));
        attribToFeatures.put(GL11.GL_POLYGON_BIT, ImmutableSet.of(
              GLSMRasterizer.getCullState()
            , GLSMRasterizer.getPolygonSmoothState()
            , GLSMRasterizer.getPolygonStippleState()
            , GLSMRasterizer.getPolygonOffsetFillState()
            , GLSMRasterizer.getPolygonOffsetLineState()
            , GLSMRasterizer.getPolygonOffsetPointState()
            , GLSMRasterizer.getPolygonState()
        ));
        attribToFeatures.put(GL11.GL_POLYGON_STIPPLE_BIT, ImmutableSet.of(
            // Polygon stipple pattern
        ));
        attribToFeatures.put(GL11.GL_SCISSOR_BIT, ImmutableSet.of(
              GLSMRasterizer.getScissorTest()
            , GLSMRasterizer.getScissorState()
        ));
        attribToFeatures.put(GL11.GL_STENCIL_BUFFER_BIT, ImmutableSet.of(
              GLSMDepthStencilAlpha.getStencilTest()
            , GLSMDepthStencilAlpha.getStencilState()
        ));
        final Set<IStateStack<?>> textureAttribs = new HashSet<>(ImmutableSet.of(
            GLSMTextures.getActiveTextureUnitStack()
                // GL_TEXTURE_ENV_MODE + GL_TEXTURE_ENV_COLOR — now per-unit in TexEnvState (added below)
                // Enable bits for the four texture coordinates

                // Border color for each texture image
                // Minification function for each texture image
                // Magnification function for each texture image
                // Texture coordinates and wrap mode for each texture image

                // Enable bits GL_TEXTURE_GEN_x, x is S, T, R, and Q
                // GL_TEXTURE_GEN_MODE setting for S, T, R, and Q
                // glTexGen plane equations for S, T, R, and Q
                // Current texture bindings (for example, GL_TEXTURE_BINDING_2D) - Below
        ));

        // Current Texture Bindings - GL_TEXTURE_BINDING_2D + per-unit TexEnvState
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            textureAttribs.add(GLSMTextures.getTextures().getTextureUnitBindings(i));
            textureAttribs.add(GLSMTextures.getTextures().getTexEnvState(i));
            textureAttribs.add(GLSMTextures.getTextures().getTexture1DStates(i));
            textureAttribs.add(GLSMTextures.getTextures().getTextureUnitStates(i));
            textureAttribs.add(GLSMTextures.getTextures().getTexture3DStates(i));
            textureAttribs.add(GLSMTextures.getTextures().getTextureCubeMapStates(i));
        }

        // Enable bits GL_TEXTURE_GEN_x where x is S, T, R, or Q
        addTextureGenStates(textureAttribs);

        attribToFeatures.put(GL11.GL_TEXTURE_BIT, textureAttribs);

        final Set<IStateStack<?>> transformAttribs = new HashSet<>(ImmutableSet.of(
            // Coefficients of the six clipping planes

              GLSMMatrices.getMatrixMode()
            , GLSMLighting.getNormalizeState()
            , GLSMLighting.getRescaleNormalState()
        ));
        for(int i = 0; i < GLSMRasterizer.getClipPlaneStates().length; i++) {
            transformAttribs.add(GLSMRasterizer.getClipPlaneStates()[i]);
        }
        attribToFeatures.put(GL11.GL_TRANSFORM_BIT, transformAttribs);
        attribToFeatures.put(GL11.GL_VIEWPORT_BIT, ImmutableSet.of(

            GLSMRasterizer.getViewportState()
        ));

        for (Int2ObjectMap.Entry<Set<IStateStack<?>>> entry : attribToFeatures.int2ObjectEntrySet()) {
            attribToMembers.put(entry.getIntKey(), toMembers(entry.getValue()));
        }
    }
}
