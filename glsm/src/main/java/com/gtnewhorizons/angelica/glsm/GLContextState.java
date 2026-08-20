package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.AlphaStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightModelStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LineStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MaterialStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MatrixModeStack;
import com.gtnewhorizons.angelica.glsm.stacks.PointStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.PolygonStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.StencilStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.PixelUnpackState;
import com.gtnewhorizons.angelica.glsm.states.ImageUnitArray;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

class GLContextState {
    final List<BooleanStateStack> allBooleanStates = new ArrayList<>();

    private BooleanStateStack track(BooleanStateStack s) {
        allBooleanStates.add(s);
        return s;
    }

    public int programGeneration;
    public int drawFramebufferGeneration;
    public boolean poppingAttributes;
    // modelview matrix changes (any: translation, rotation, scale)
    public int mvGeneration;
    public int mvLinearGeneration;
    // projection matrix changes
    public int projGeneration;
    // texture matrix changes
    public int texMatrixGeneration;
    public int lightingGeneration;
    // fog + alpha ref + overlay color
    public int fragmentGeneration;
    // current vertex color
    public int colorGeneration;
    // clip plane equation changes
    public int clipPlaneGeneration;
    public float overlayR = 0.0f;
    public float overlayG = 0.0f;
    public float overlayB = 0.0f;
    public float overlayA = 0.0f;
    public float shaderColorR = 1.0f;
    public float shaderColorG = 1.0f;
    public float shaderColorB = 1.0f;
    public float shaderColorA = 1.0f;
    // Deferred vertex attribute upload flags - set when state changes, flushed before draw
    public boolean dirtyColorAttrib = true;
    public boolean dirtyNormalAttrib;
    public boolean dirtyTexCoordAttrib;
    public boolean dirtyLightmapAttrib = true;
    public boolean unit23TexCoordSetDuringDraw = false;
    public int maxBoundTextureUnit = 0;
    public int maxBoundImageUnit = 0;
    public final IntStack attribs = new IntArrayList(GLStateManager.MAX_ATTRIB_STACK_DEPTH);
    public int attribDepth = 0;
    @SuppressWarnings("unchecked")
    public final List<IStateStack<?>>[] modifiedAtDepth = new List[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedMvGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedMvLinearGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedProjGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedTexMatGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedLightingGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedFragmentGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedColorGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedNormalGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final int[] savedTexCoordGen = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    public final IntegerStateStack activeTextureUnit = new IntegerStateStack(0);
    public int clientActiveTextureUnit = 0;
    public final IntegerStateStack shadeModelState = new IntegerStateStack(GL11.GL_SMOOTH);
    public final TextureUnitArray textures = new TextureUnitArray();
    public final ImageUnitArray imageUnits = new ImageUnitArray();
    public final BlendStateStack blendState = new BlendStateStack();
    public final BooleanStateStack blendMode = track(new BooleanStateStack(GL11.GL_BLEND));
    public final BooleanStateStack scissorTest = track(new BooleanStateStack(GL11.GL_SCISSOR_TEST));
    public final DepthStateStack depthState = new DepthStateStack();
    public final BooleanStateStack depthTest = track(new BooleanStateStack(GL11.GL_DEPTH_TEST));
    public final FogStateStack fogState = new FogStateStack();
    public final BooleanStateStack fogMode = track(new BooleanStateStack(GL11.GL_FOG, false, true));
    public final Color4Stack color = new Color4Stack();
    public final Color4Stack clearColor = new Color4Stack(new Color4(0.0F, 0.0F, 0.0F, 0.0F));
    public final ColorMaskStack colorMask = new ColorMaskStack();
    public final IntegerStateStack drawBuffer = new IntegerStateStack(GLStateManager.DEFAULT_DRAW_BUFFER);
    public final IntegerStateStack logicOpMode = new IntegerStateStack(GL11.GL_COPY);
    public final BooleanStateStack cullState = track(new BooleanStateStack(GL11.GL_CULL_FACE));
    public final AlphaStateStack alphaState = new AlphaStateStack();
    public final BooleanStateStack alphaTest = track(new BooleanStateStack(GL11.GL_ALPHA_TEST, false, true));
    public final BooleanStateStack lightingState = track(new BooleanStateStack(GL11.GL_LIGHTING, false, true));
    public final BooleanStateStack rescaleNormalState = track(new BooleanStateStack(GL12.GL_RESCALE_NORMAL, false, true));
    public final BooleanStateStack normalizeState = track(new BooleanStateStack(GL11.GL_NORMALIZE, false, true));
    public final BooleanStateStack ditherState = track(new BooleanStateStack(GL11.GL_DITHER, true));
    public final BooleanStateStack stencilTest = track(new BooleanStateStack(GL11.GL_STENCIL_TEST));
    public final BooleanStateStack lineSmoothState = track(new BooleanStateStack(GL11.GL_LINE_SMOOTH));
    public final BooleanStateStack lineStippleState = track(new BooleanStateStack(GL11.GL_LINE_STIPPLE, false, true));
    public final BooleanStateStack pointSmoothState = track(new BooleanStateStack(GL11.GL_POINT_SMOOTH, false, true));
    public final BooleanStateStack polygonSmoothState = track(new BooleanStateStack(GL11.GL_POLYGON_SMOOTH));
    public final BooleanStateStack polygonStippleState = track(new BooleanStateStack(GL11.GL_POLYGON_STIPPLE, false, true));
    public final BooleanStateStack multisampleState = track(new BooleanStateStack(GL13.GL_MULTISAMPLE, true));
    public final BooleanStateStack sampleAlphaToCoverageState = track(new BooleanStateStack(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE));
    public final BooleanStateStack sampleAlphaToOneState = track(new BooleanStateStack(GL13.GL_SAMPLE_ALPHA_TO_ONE));
    public final BooleanStateStack sampleCoverageState = track(new BooleanStateStack(GL13.GL_SAMPLE_COVERAGE));
    public final BooleanStateStack colorLogicOpState = track(new BooleanStateStack(GL11.GL_COLOR_LOGIC_OP));
    public final BooleanStateStack indexLogicOpState = track(new BooleanStateStack(GL11.GL_INDEX_LOGIC_OP, false, true));
    public final BooleanStateStack polygonOffsetPointState = track(new BooleanStateStack(GL11.GL_POLYGON_OFFSET_POINT));
    public final BooleanStateStack polygonOffsetLineState = track(new BooleanStateStack(GL11.GL_POLYGON_OFFSET_LINE));
    public final BooleanStateStack polygonOffsetFillState = track(new BooleanStateStack(GL11.GL_POLYGON_OFFSET_FILL));
    public final LineStateStack lineState = new LineStateStack();
    public final PointStateStack pointState = new PointStateStack();
    public final PolygonStateStack polygonState = new PolygonStateStack();
    public final StencilStateStack stencilState = new StencilStateStack();
    public int stencilBitMask = 0xFFFFFFFF;
    public final BooleanStateStack autoNormalState = track(new BooleanStateStack(GL11.GL_AUTO_NORMAL, false, true));
    public final BooleanStateStack map1Color4State = track(new BooleanStateStack(GL11.GL_MAP1_COLOR_4, false, true));
    public final BooleanStateStack map1IndexState = track(new BooleanStateStack(GL11.GL_MAP1_INDEX, false, true));
    public final BooleanStateStack map1NormalState = track(new BooleanStateStack(GL11.GL_MAP1_NORMAL, false, true));
    public final BooleanStateStack map1TextureCoord1State = track(new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_1, false, true));
    public final BooleanStateStack map1TextureCoord2State = track(new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_2, false, true));
    public final BooleanStateStack map1TextureCoord3State = track(new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_3, false, true));
    public final BooleanStateStack map1TextureCoord4State = track(new BooleanStateStack(GL11.GL_MAP1_TEXTURE_COORD_4, false, true));
    public final BooleanStateStack map1Vertex3State = track(new BooleanStateStack(GL11.GL_MAP1_VERTEX_3, false, true));
    public final BooleanStateStack map1Vertex4State = track(new BooleanStateStack(GL11.GL_MAP1_VERTEX_4, false, true));
    public final BooleanStateStack map2Color4State = track(new BooleanStateStack(GL11.GL_MAP2_COLOR_4, false, true));
    public final BooleanStateStack map2IndexState = track(new BooleanStateStack(GL11.GL_MAP2_INDEX, false, true));
    public final BooleanStateStack map2NormalState = track(new BooleanStateStack(GL11.GL_MAP2_NORMAL, false, true));
    public final BooleanStateStack map2TextureCoord1State = track(new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_1, false, true));
    public final BooleanStateStack map2TextureCoord2State = track(new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_2, false, true));
    public final BooleanStateStack map2TextureCoord3State = track(new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_3, false, true));
    public final BooleanStateStack map2TextureCoord4State = track(new BooleanStateStack(GL11.GL_MAP2_TEXTURE_COORD_4, false, true));
    public final BooleanStateStack map2Vertex3State = track(new BooleanStateStack(GL11.GL_MAP2_VERTEX_3, false, true));
    public final BooleanStateStack map2Vertex4State = track(new BooleanStateStack(GL11.GL_MAP2_VERTEX_4, false, true));
    public final BooleanStateStack[] clipPlaneStates = new BooleanStateStack[GLStateManager.MAX_CLIP_PLANES];
    public final ClipPlaneState clipPlaneState = new ClipPlaneState();
    public final FloatBuffer queryScratch = BufferUtils.createFloatBuffer(16);
    public final DoubleBuffer queryScratchDouble = BufferUtils.createDoubleBuffer(16);
    public final MatrixModeStack matrixMode = new MatrixModeStack();
    public final Matrix4fStack modelViewMatrix = new Matrix4fStack(GLStateManager.MAX_MODELVIEW_STACK_DEPTH);
    public final Matrix4fStack projectionMatrix = new Matrix4fStack(GLStateManager.MAX_PROJECTION_STACK_DEPTH);
    public final BooleanStateStack[] lightStates = new BooleanStateStack[8];
    public final LightStateStack[] lightDataStates = new LightStateStack[8];
    public final BooleanStateStack colorMaterial = track(new BooleanStateStack(GL11.GL_COLOR_MATERIAL, false, true));
    public final IntegerStateStack colorMaterialFace = new IntegerStateStack(GL11.GL_FRONT_AND_BACK);
    public final IntegerStateStack colorMaterialParameter = new IntegerStateStack(GL11.GL_AMBIENT_AND_DIFFUSE);
    public final LightModelStateStack lightModel = new LightModelStateStack();
    public final MaterialStateStack frontMaterial = new MaterialStateStack(GL11.GL_FRONT);
    public final MaterialStateStack backMaterial = new MaterialStateStack(GL11.GL_BACK);
    public final ViewPortStateStack viewportState = new ViewPortStateStack();
    public int activeProgram = 0;
    public int listBase = 0;
    public int boundVBO;
    public int boundVAO;
    public int boundPixelUnpackBuffer;
    public int boundPixelPackBuffer;
    public PixelUnpackState pixelUnpackState = PixelUnpackState.DEFAULT;
    public final int[] clientAttribSavedTextureUnit = new int[GLStateManager.CLIENT_ATTRIB_STACK_DEPTH];
    public final int[] clientAttribSavedVertexFlags = new int[GLStateManager.CLIENT_ATTRIB_STACK_DEPTH];
    public int clientAttribStackPointer = 0;
    public final boolean[] restoreUnitChanged = new boolean[GLStateManager.MAX_TEXTURE_UNITS];
    public boolean restoreDepthChanged, restoreBlendChanged, restoreColorMaskChanged, restoreClearColorChanged, restoreDrawBufferChanged, restoreLogicOpChanged, restoreStencilChanged, restoreViewportChanged, restoreLineChanged, restorePointChanged, restorePolygonChanged, restoreActiveUnitChanged;
    public final BlendState vanillaBlendBefore = new BlendState();
    public final BlendState vanillaBlendAfter = new BlendState();
    public boolean vanillaBlendEnabledBefore;
    public int drawFramebuffer = 0;
    public int readFramebuffer = 0;
    public int texGenGeneration;

    GLContextState() {
        for (int i = 0; i < GLStateManager.MAX_ATTRIB_STACK_DEPTH; i++) modifiedAtDepth[i] = new ArrayList<>();
        for (int i = 0; i < lightStates.length; i++) {
            lightStates[i] = track(new BooleanStateStack(GL11.GL_LIGHT0 + i, false, true));
        }
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            clipPlaneStates[i] = track(new BooleanStateStack(GL11.GL_CLIP_PLANE0 + i));
        }
    }

    void init() {
        for (int i = 0; i < lightDataStates.length; i++) {
            lightDataStates[i] = new LightStateStack(GL11.GL_LIGHT0 + i);
        }
    }
}
