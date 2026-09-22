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
            collection.add(GLStateManager.getTextures().getTexGenSStates(i));
            collection.add(GLStateManager.getTextures().getTexGenTStates(i));
            collection.add(GLStateManager.getTextures().getTexGenRStates(i));
            collection.add(GLStateManager.getTextures().getTexGenQStates(i));
        }
    }

    static {
        final Int2ObjectMap<Set<IStateStack<?>>> attribToFeatures = new Int2ObjectOpenHashMap<>();

        attribToFeatures.put(GL11.GL_COLOR_BUFFER_BIT, ImmutableSet.of(
              GLStateManager.getAlphaTest()
            , GLStateManager.getAlphaState()
            , GLStateManager.getBlendMode()
            , GLStateManager.getBlendState()
            , GLStateManager.getColorLogicOpState()
            , GLStateManager.getDitherState()
            , GLStateManager.getDrawBuffer()
            , GLStateManager.getIndexLogicOpState()
            , GLStateManager.getLogicOpMode()
            , GLStateManager.getColorMask()
            , GLStateManager.getClearColor()
        ));
        attribToFeatures.put(GL11.GL_CURRENT_BIT, ImmutableSet.of(
              GLStateManager.getColor()
            , GLStateManager.getSecondaryColor() // Current secondary color
            , ShaderManager.getNormalStack()    // Current normal vector
            , ShaderManager.getTexCoordStack()  // Current texture coordinates
            // Current color index
            // Current raster position
            // GL_CURRENT_RASTER_POSITION_VALID flag
            // RGBA color associated with current raster position
            // Color index associated with current raster position
            // Texture coordinates associated with current raster position
            // GL_EDGE_FLAG flag
        ));
        attribToFeatures.put(GL11.GL_DEPTH_BUFFER_BIT, ImmutableSet.of(
              GLStateManager.getDepthTest()
            , GLStateManager.getDepthState()
            // Depth buffer clear value
            // GL_DEPTH_WRITEMASK enable bit
        ));

        final HashSet<IStateStack<?>> enableBits = new HashSet<>(ImmutableSet.of(
              GLStateManager.getAlphaTest()
            , GLStateManager.getAutoNormalState()
            , GLStateManager.getBlendMode()
            , GLStateManager.getColorLogicOpState()
            , GLStateManager.getColorMaterial()
            , GLStateManager.getColorSumState()
            , GLStateManager.getCullState()
            , GLStateManager.getDepthTest()
            , GLStateManager.getDitherState()
            , GLStateManager.getFogMode()
            , GLStateManager.getIndexLogicOpState()
            , GLStateManager.getLightStates()[0] // GL_LIGHT0
            , GLStateManager.getLightStates()[1] // GL_LIGHT1
            , GLStateManager.getLightStates()[2] // GL_LIGHT2
            , GLStateManager.getLightStates()[3] // GL_LIGHT3
            , GLStateManager.getLightStates()[4] // GL_LIGHT4
            , GLStateManager.getLightStates()[5] // GL_LIGHT5
            , GLStateManager.getLightStates()[6] // GL_LIGHT6
            , GLStateManager.getLightStates()[7] // GL_LIGHT7
            , GLStateManager.getLightingState()
            , GLStateManager.getLineSmoothState()
            , GLStateManager.getLineStippleState()
            , GLStateManager.getMap1Color4State()
            , GLStateManager.getMap1IndexState()
            , GLStateManager.getMap1NormalState()
            , GLStateManager.getMap1TextureCoord1State()
            , GLStateManager.getMap1TextureCoord2State()
            , GLStateManager.getMap1TextureCoord3State()
            , GLStateManager.getMap1TextureCoord4State()
            , GLStateManager.getMap1Vertex3State()
            , GLStateManager.getMap1Vertex4State()
            , GLStateManager.getMap2Color4State()
            , GLStateManager.getMap2IndexState()
            , GLStateManager.getMap2NormalState()
            , GLStateManager.getMap2TextureCoord1State()
            , GLStateManager.getMap2TextureCoord2State()
            , GLStateManager.getMap2TextureCoord3State()
            , GLStateManager.getMap2TextureCoord4State()
            , GLStateManager.getMap2Vertex3State()
            , GLStateManager.getMap2Vertex4State()
            , GLStateManager.getMultisampleState()
            , GLStateManager.getNormalizeState()
            , GLStateManager.getPointSmoothState()
            , GLStateManager.getPolygonOffsetPointState()
            , GLStateManager.getPolygonOffsetLineState()
            , GLStateManager.getPolygonOffsetFillState()
            , GLStateManager.getPolygonSmoothState()
            , GLStateManager.getPolygonStippleState()
            , GLStateManager.getRescaleNormalState()
            , GLStateManager.getSampleAlphaToCoverageState()
            , GLStateManager.getSampleAlphaToOneState()
            , GLStateManager.getSampleCoverageState()
            , GLStateManager.getScissorTest()
            , GLStateManager.getStencilTest()
        ));

        // Enable bits for the user-definable clipping planes
        for(int i = 0; i < GLStateManager.getClipPlaneStates().length; i++) {
            enableBits.add(GLStateManager.getClipPlaneStates()[i]);
        }

        // GL_TEXTURE_1D, GL_TEXTURE_2D, GL_TEXTURE_3D flags
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            enableBits.add(GLStateManager.getTextures().getTexture1DStates(i));
            enableBits.add(GLStateManager.getTextures().getTextureUnitStates(i));
            enableBits.add(GLStateManager.getTextures().getTexture3DStates(i));
        }

        // Flags GL_TEXTURE_GEN_x where x is S, T, R, or Q
        addTextureGenStates(enableBits);

        attribToFeatures.put(GL11.GL_ENABLE_BIT, enableBits);
        attribToFeatures.put(GL11.GL_EVAL_BIT, ImmutableSet.of(
              GLStateManager.getAutoNormalState()
            , GLStateManager.getMap1Color4State()
            , GLStateManager.getMap1IndexState()
            , GLStateManager.getMap1NormalState()
            , GLStateManager.getMap1TextureCoord1State()
            , GLStateManager.getMap1TextureCoord2State()
            , GLStateManager.getMap1TextureCoord3State()
            , GLStateManager.getMap1TextureCoord4State()
            , GLStateManager.getMap1Vertex3State()
            , GLStateManager.getMap1Vertex4State()
            , GLStateManager.getMap2Color4State()
            , GLStateManager.getMap2IndexState()
            , GLStateManager.getMap2NormalState()
            , GLStateManager.getMap2TextureCoord1State()
            , GLStateManager.getMap2TextureCoord2State()
            , GLStateManager.getMap2TextureCoord3State()
            , GLStateManager.getMap2TextureCoord4State()
            , GLStateManager.getMap2Vertex3State()
            , GLStateManager.getMap2Vertex4State()
        ));
        attribToFeatures.put(GL11.GL_FOG_BIT, ImmutableSet.of(
              GLStateManager.getFogMode()
            , GLStateManager.getColorSumState()
            , GLStateManager.getFogState()
                                       // ^^ Fog density
                                       // ^^ Linear fog start
                                       // ^^ Linear fog end
            // Fog index
                                       // ^^ GL_FOG_MODE value
        ));
        attribToFeatures.put(GL11.GL_HINT_BIT, ImmutableSet.of(
            // GL_PERSPECTIVE_CORRECTION_HINT setting
            // GL_POINT_SMOOTH_HINT setting
            // GL_LINE_SMOOTH_HINT setting
            // GL_POLYGON_SMOOTH_HINT setting
            // GL_FOG_HINT setting
            // GL_GENERATE_MIPMAP_HINT setting
            // GL_TEXTURE_COMPRESSION_HINT setting
        ));
        attribToFeatures.put(GL11.GL_LIGHTING_BIT, ImmutableSet.of(
            GLStateManager.getColorMaterial()
            , GLStateManager.getColorMaterialFace()
            , GLStateManager.getColorMaterialParameter()
            , GLStateManager.getLightModel()
            , GLStateManager.getLightingState()
            // Enable bit for each light
            , GLStateManager.getLightStates()[0] // GL_LIGHT0
            , GLStateManager.getLightStates()[1] // GL_LIGHT1
            , GLStateManager.getLightStates()[2] // GL_LIGHT2
            , GLStateManager.getLightStates()[3] // GL_LIGHT3
            , GLStateManager.getLightStates()[4] // GL_LIGHT4
            , GLStateManager.getLightStates()[5] // GL_LIGHT5
            , GLStateManager.getLightStates()[6] // GL_LIGHT6
            , GLStateManager.getLightStates()[7] // GL_LIGHT7
            // Ambient, diffuse, and specular intensity for each light
            // Direction, position, exponent, and cutoff angle for each light
            // Constant, linear, and quadratic attenuation factors for each light
            , GLStateManager.getLightDataStates()[0]
            , GLStateManager.getLightDataStates()[1]
            , GLStateManager.getLightDataStates()[2]
            , GLStateManager.getLightDataStates()[3]
            , GLStateManager.getLightDataStates()[4]
            , GLStateManager.getLightDataStates()[5]
            , GLStateManager.getLightDataStates()[6]
            , GLStateManager.getLightDataStates()[7]
            // Ambient, diffuse, specular, and emissive color for each material
            // Ambient, diffuse, and specular color indices for each material
            // Specular exponent for each material
            , GLStateManager.getFrontMaterial()
            , GLStateManager.getBackMaterial()
            , GLStateManager.getShadeModelState()
        ));
        attribToFeatures.put(GL11.GL_LINE_BIT, ImmutableSet.of(
              GLStateManager.getLineSmoothState()
            , GLStateManager.getLineStippleState()
            , GLStateManager.getLineState()
        ));
        attribToFeatures.put(GL11.GL_LIST_BIT, ImmutableSet.of(
            // GL_LIST_BASE setting
        ));
        attribToFeatures.put(GL13.GL_MULTISAMPLE_BIT, ImmutableSet.of(
              GLStateManager.getMultisampleState()
            , GLStateManager.getSampleAlphaToCoverageState()
            , GLStateManager.getSampleAlphaToOneState()
            , GLStateManager.getSampleCoverageState()
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
              GLStateManager.getPointSmoothState()
            , GLStateManager.getPointState()
        ));
        attribToFeatures.put(GL11.GL_POLYGON_BIT, ImmutableSet.of(
              GLStateManager.getCullState()
            , GLStateManager.getPolygonSmoothState()
            , GLStateManager.getPolygonStippleState()
            , GLStateManager.getPolygonOffsetFillState()
            , GLStateManager.getPolygonOffsetLineState()
            , GLStateManager.getPolygonOffsetPointState()
            , GLStateManager.getPolygonState()
        ));
        attribToFeatures.put(GL11.GL_POLYGON_STIPPLE_BIT, ImmutableSet.of(
            // Polygon stipple pattern
        ));
        attribToFeatures.put(GL11.GL_SCISSOR_BIT, ImmutableSet.of(
              GLStateManager.getScissorTest()
            // Scissor box
        ));
        attribToFeatures.put(GL11.GL_STENCIL_BUFFER_BIT, ImmutableSet.of(
              GLStateManager.getStencilTest()
            , GLStateManager.getStencilState()
        ));
        final Set<IStateStack<?>> textureAttribs = new HashSet<>(ImmutableSet.of(
            GLStateManager.getActiveTextureUnitStack()
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
            textureAttribs.add(GLStateManager.getTextures().getTextureUnitBindings(i));
            textureAttribs.add(GLStateManager.getTextures().getTexEnvState(i));
        }

        // Enable bits GL_TEXTURE_GEN_x where x is S, T, R, or Q
        addTextureGenStates(textureAttribs);

        attribToFeatures.put(GL11.GL_TEXTURE_BIT, textureAttribs);

        final Set<IStateStack<?>> transformAttribs = new HashSet<>(ImmutableSet.of(
            // Coefficients of the six clipping planes

              GLStateManager.getMatrixMode()
            , GLStateManager.getNormalizeState()
            , GLStateManager.getRescaleNormalState()
        ));
        for(int i = 0; i < GLStateManager.getClipPlaneStates().length; i++) {
            transformAttribs.add(GLStateManager.getClipPlaneStates()[i]);
        }
        attribToFeatures.put(GL11.GL_TRANSFORM_BIT, transformAttribs);
        attribToFeatures.put(GL11.GL_VIEWPORT_BIT, ImmutableSet.of(

            GLStateManager.getViewportState()
        ));

        for (Int2ObjectMap.Entry<Set<IStateStack<?>>> entry : attribToFeatures.int2ObjectEntrySet()) {
            attribToMembers.put(entry.getIntKey(), toMembers(entry.getValue()));
        }
    }
}
