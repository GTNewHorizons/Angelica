package com.gtnewhorizons.angelica.glsm;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.stacks.IStateStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Feature {
    private static final int[] supportedAttribs = new int[] { GL11.GL_ACCUM_BUFFER_BIT, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_CURRENT_BIT, GL11.GL_DEPTH_BUFFER_BIT,
        GL11.GL_ENABLE_BIT, GL11.GL_EVAL_BIT, GL11.GL_FOG_BIT, GL11.GL_HINT_BIT, GL11.GL_LIGHTING_BIT, GL11.GL_LINE_BIT, GL11.GL_LIST_BIT,
        GL13.GL_MULTISAMPLE_BIT, GL11.GL_PIXEL_MODE_BIT, GL11.GL_POINT_BIT, GL11.GL_POLYGON_BIT, GL11.GL_POLYGON_STIPPLE_BIT, GL11.GL_SCISSOR_BIT,
        GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_TEXTURE_BIT, GL11.GL_TRANSFORM_BIT, GL11.GL_VIEWPORT_BIT };

    static final Int2ObjectMap<Set<IStateStack<?>>> maskToFeaturesMap = new Int2ObjectOpenHashMap<>();

    static Set<IStateStack<?>> maskToFeatures(int mask) {
        if(maskToFeaturesMap.containsKey(mask)) {
            return maskToFeaturesMap.get(mask);
        }

        final Set<IStateStack<?>> features = new HashSet<>();
        for(int attrib : Feature.supportedAttribs) {
            if((mask & attrib) == attrib) {
                features.addAll(getFeatures(attrib));
            }
        }
        maskToFeaturesMap.put(mask, features);
        return features;
    }

    private static final Map<Integer, Set<IStateStack<?>>> attribToFeatures = new HashMap<>();
    static {
        attribToFeatures.put(GL11.GL_COLOR_BUFFER_BIT, ImmutableSet.of(
              GLStateManager.alphaTest  // GL_ALPHA_TEST enable bit
            , GLStateManager.alphaState // Alpha test function and reference value
            , GLStateManager.blendMode  // GL_BLEND enable bit
            , GLStateManager.blendState // Blending source and destination functions
            // Constant blend color
            // Blending equation
            // GL_DITHER enable bit
            // GL_DRAW_BUFFER setting
            // GL_COLOR_LOGIC_OP enable bit
            // GL_INDEX_LOGIC_OP enable bit
            // Logic op function
            , GLStateManager.colorMask   // Color-mode and index-mode writemasks
            , GLStateManager.clearColor  // Color-mode and index-mode clear values
        ));
        attribToFeatures.put(GL11.GL_CURRENT_BIT, ImmutableSet.of(
              GLStateManager.color  // Current RGBA color
            // Current color index
            // Current normal vector
            // Current texture coordinates
            // Current raster position
            // GL_CURRENT_RASTER_POSITION_VALID flag
            // RGBA color associated with current raster position
            // Color index associated with current raster position
            // Texture coordinates associated with current raster position
            // GL_EDGE_FLAG flag
        ));
        attribToFeatures.put(GL11.GL_DEPTH_BUFFER_BIT, ImmutableSet.of(
              GLStateManager.depthTest     // GL_DEPTH_TEST enable bit
            , GLStateManager.depthState    // Depth buffer test function
            // Depth buffer clear value
            // GL_DEPTH_WRITEMASK enable bit
        ));

        final HashSet<IStateStack<?>> enableBits = new HashSet<>(ImmutableSet.of(
              GLStateManager.alphaTest // GL_ALPHA_TEST flag
            // GL_AUTO_NORMAL flag
            , GLStateManager.blendMode // GL_BLEND flag
            // Enable bits for the user-definable clipping planes
            // GL_COLOR_MATERIAL
            , GLStateManager.cullState // GL_CULL_FACE flag
            , GLStateManager.depthTest // GL_DEPTH_TEST flag
            // GL_DITHER flag
            , GLStateManager.fogMode // GL_FOG flag
            // GL_LIGHTi where 0 <= i < GL_MAX_LIGHTS
            , GLStateManager.lightingState // GL_LIGHTING flag
            // GL_LINE_SMOOTH flag
            // GL_LINE_STIPPLE flag
            // GL_INDEX_LOGIC_OP flag
            // GL_COLOR_LOGIC_OP flag
            // GL_MAP1_x where x is a map type
            // GL_MAP2_x where x is a map type
            // GL_MULTISAMPLE flag
            // GL_NORMALIZE flag
            // GL_POINT_SMOOTH flag
            // GL_POLYGON_OFFSET_LINE flag
            // GL_POLYGON_OFFSET_FILL flag
            // GL_POLYGON_OFFSET_POINT flag
            // GL_POLYGON_SMOOTH flag
            // GL_POLYGON_STIPPLE flag
            // GL_SAMPLE_ALPHA_TO_COVERAGE flag
            // GL_SAMPLE_ALPHA_TO_ONE flag
            // GL_SAMPLE_COVERAGE flag
            , GLStateManager.scissorTest  // GL_SCISSOR_TEST flag
            // GL_STENCIL_TEST flag
            // GL_TEXTURE_1D flag
            // GL_TEXTURE_2D flag - Below
            // GL_TEXTURE_3D flag
            // Flags GL_TEXTURE_GEN_x where x is S, T, R, or Q
        ));

        // GL_TEXTURE_2D flag
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            enableBits.add(GLStateManager.textures.getTextureUnitStates(i));
        }

        attribToFeatures.put(GL11.GL_ENABLE_BIT, enableBits);
        attribToFeatures.put(GL11.GL_EVAL_BIT, ImmutableSet.of(
            // GL_MAP1_x enable bits, where x is a map type
            // GL_MAP2_x enable bits, where x is a map type
            // 1D grid endpoints and divisions
            // 2D grid endpoints and divisions
            // GL_AUTO_NORMAL enable bit
        ));
        attribToFeatures.put(GL11.GL_FOG_BIT, ImmutableSet.of(
              GLStateManager.fogMode    // GL_FOG enable bit
            , GLStateManager.fogState   // Fog color
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
            // GL_COLOR_MATERIAL enable bit
            // GL_COLOR_MATERIAL_FACE value
            // Color material parameters that are tracking the current color
            // Ambient scene color
            // GL_LIGHT_MODEL_LOCAL_VIEWER value
            // GL_LIGHT_MODEL_TWO_SIDE setting
              GLStateManager.lightingState  // GL_LIGHTING enable bit
            // Enable bit for each light
            // Ambient, diffuse, and specular intensity for each light
            // Direction, position, exponent, and cutoff angle for each light
            // Constant, linear, and quadratic attenuation factors for each light
            // Ambient, diffuse, specular, and emissive color for each material
            // Ambient, diffuse, and specular color indices for each material
            // Specular exponent for each material
            , GLStateManager.shadeModelState // GL_SHADE_MODEL setting
        ));
        attribToFeatures.put(GL11.GL_LINE_BIT, ImmutableSet.of(
            // GL_LINE_SMOOTH flag
            // GL_LINE_STIPPLE enable bit
            // Line stipple pattern and repeat counter
            // Line width
        ));
        attribToFeatures.put(GL11.GL_LIST_BIT, ImmutableSet.of(
            // GL_LIST_BASE setting
        ));
        attribToFeatures.put(GL13.GL_MULTISAMPLE_BIT, ImmutableSet.of(
            // GL_MULTISAMPLE enable bit
            // GL_SAMPLE_ALPHA_TO_COVERAGE flag
            // GL_SAMPLE_ALPHA_TO_ONE flag
            // GL_SAMPLE_COVERAGE flag
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
            // GL_POINT_SMOOTH flag
            // Point size
        ));
        attribToFeatures.put(GL11.GL_POLYGON_BIT, ImmutableSet.of(
              GLStateManager.cullState // GL_CULL_FACE enable bit
            // GL_CULL_FACE_MODE value
            // GL_FRONT_FACE indicator
            // GL_POLYGON_MODE setting
            // GL_POLYGON_SMOOTH flag
            // GL_POLYGON_STIPPLE enable bit
            // GL_POLYGON_OFFSET_FILL flag
            // GL_POLYGON_OFFSET_LINE flag
            // GL_POLYGON_OFFSET_POINT flag
            // GL_POLYGON_OFFSET_FACTOR
            // GL_POLYGON_OFFSET_UNITS
        ));
        attribToFeatures.put(GL11.GL_POLYGON_STIPPLE_BIT, ImmutableSet.of(
            // Polygon stipple pattern
        ));
        attribToFeatures.put(GL11.GL_SCISSOR_BIT, ImmutableSet.of(
              GLStateManager.scissorTest // GL_SCISSOR_TEST enable bit
            // Scissor box
        ));
        attribToFeatures.put(GL11.GL_STENCIL_BUFFER_BIT, ImmutableSet.of(
            // GL_STENCIL_TEST enable bit
            // Stencil function and reference value
            // Stencil value mask
            // Stencil fail, pass, and depth buffer pass actions
            // Stencil buffer clear value
            // Stencil buffer writemask
        ));
        final Set<IStateStack<?>> textureAttribs = new HashSet<>(ImmutableSet.of(
            GLStateManager.activeTextureUnit // Active texture unit
                // Enable bits for the four texture coordinates

                // Border color for each texture image
                // Minification function for each texture image
                // Magnification function for each texture image
                // Texture coordinates and wrap mode for each texture image

                // Color and mode for each texture environment
                // Enable bits GL_TEXTURE_GEN_x, x is S, T, R, and Q
                // GL_TEXTURE_GEN_MODE setting for S, T, R, and Q
                // glTexGen plane equations for S, T, R, and Q
                // Current texture bindings (for example, GL_TEXTURE_BINDING_2D) - Below
        ));

        // Current Texture Bindings - GL_TEXTURE_BINDING_2D
        for(int i = 0 ; i < GLStateManager.MAX_TEXTURE_UNITS; i++) {
            textureAttribs.add(GLStateManager.textures.getTextureUnitBindings(i));
//            textureAttribs.add(GLStateManager.textures.getInfo(i))
        }

        attribToFeatures.put(GL11.GL_TEXTURE_BIT, textureAttribs);

        attribToFeatures.put(GL11.GL_TRANSFORM_BIT, ImmutableSet.of(
            // Coefficients of the six clipping planes
            // Enable bits for the user-definable clipping planes
              GLStateManager.matrixMode
            // GL_NORMALIZE flag
            , GLStateManager.rescaleNormalState // GL_RESCALE_NORMAL flag
        ));
        attribToFeatures.put(GL11.GL_VIEWPORT_BIT, ImmutableSet.of(
            // Depth range (near and far)
            GLStateManager.viewportState
        ));
    }

    public static Set<IStateStack<?>> getFeatures(int attrib) {
        return attribToFeatures.getOrDefault(attrib, Collections.emptySet());
    }


}
