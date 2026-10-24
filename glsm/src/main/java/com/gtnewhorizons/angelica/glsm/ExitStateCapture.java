package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredAlphaHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records the GL state a block of rendering code leaves behind, so a later run of the same block can be skipped and
 * its exit state applied instead.
 */
final class ExitStateCapture {
    // Fields 0..12 are the capabilities, in CAPS order. FRONT_* and BACK_* are four contiguous fields each, ordered like
    // vector(): apply() and finish() address them as 15L << FRONT_AMBIENT and (field - FRONT_AMBIENT) & 3.
    static final int BLEND = 0, ALPHA = 1, DEPTH = 2, CULL = 3, LIGHTING = 4, NORMALIZE = 5, RESCALE = 6, LIGHT0 = 7, LIGHT1 = 8, TEXTURE0 = 9, TEXTURE1 = 10, OFFSET = 11, COLOR_MATERIAL = 12, COLOR = 13, NORMAL = 14, TEXCOORD = 15,
        LIGHTMAP = 16, SHADER_COLOR = 17, BLEND_FUNC = 18, ALPHA_FUNC = 19, DEPTH_FUNC = 20, DEPTH_MASK = 21, COLOR_MASK = 22, CULL_FACE = 23, FRONT_FACE = 24, SHADE_MODEL = 25, ACTIVE_UNIT = 26, TEXTURE_BINDING = 27, MATRIX_MODE = 28,
        TEXTURE_MATRIX = 29, OFFSET_VALUES = 30, COLOR_MATERIAL_SETTINGS = 31, FRONT_AMBIENT = 32, FRONT_DIFFUSE = 33, FRONT_SPECULAR = 34, FRONT_EMISSION = 35, BACK_AMBIENT = 36, BACK_DIFFUSE = 37, BACK_SPECULAR = 38,
        BACK_EMISSION = 39, OVERLAY_COLOR = 40, CLIENT_ACTIVE_UNIT = 41;
    private static final int FIELD_COUNT = 42;
    private static final long IMPLICIT_INPUTS = bits(ACTIVE_UNIT, MATRIX_MODE);
    private static final long MATERIAL_BAKE_INPUTS = bits(COLOR, COLOR_MATERIAL_SETTINGS);
    private static final long DRAW_INPUTS = bits(CULL, TEXTURE0, TEXTURE1, COLOR, LIGHTMAP, OVERLAY_COLOR, CULL_FACE, FRONT_FACE, TEXTURE_BINDING);
    private static final long VERTEX_INPUTS = bits(NORMAL, TEXCOORD);
    private static final long MATERIAL_INPUTS = bits(BLEND, ALPHA, DEPTH, BLEND_FUNC, ALPHA_FUNC, DEPTH_FUNC, DEPTH_MASK, COLOR_MASK);
    private static final int[] CAPS = {GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_LIGHTING, GL11.GL_NORMALIZE, GL12.GL_RESCALE_NORMAL, GL11.GL_LIGHT0, GL11.GL_LIGHT1, GL11.GL_TEXTURE_2D,
        GL11.GL_TEXTURE_2D, GL11.GL_POLYGON_OFFSET_FILL, GL11.GL_COLOR_MATERIAL};

    private static final AtomicInteger openCaptures = new AtomicInteger();

    private final GLContextState context;
    private final Matrix4f modelView = new Matrix4f();
    private final long[] attribWrites = new long[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    private final long[] matrixWrites = new long[GLStateManager.MAX_TEXTURE_STACK_DEPTH];
    private final boolean[] matrixInherited = new boolean[GLStateManager.MAX_TEXTURE_STACK_DEPTH];
    private final ExitState.Values current = new ExitState.Values();
    private final Reference2LongOpenHashMap<StateSet> restoredFieldsBySet = new Reference2LongOpenHashMap<>();
    private ExitState result;
    private int suspended, attribDepth, modelViewDepth, textureDepth, entryOverrides;
    private int filterTexture, filterMin, filterMag;
    private float filterAniso;
    private boolean filterOperation;
    private boolean textureInherited;

    ExitStateCapture(GLContextState context) {
        this.context = context;
    }

    static boolean anyOpen() {
        return openCaptures.get() != 0;
    }

    private static boolean blendWritesReachVanilla() {
        final DeferredBlendHandler handler = GLSMHooks.blendHandler;
        return handler == null || !handler.isOverrideHeld() || handler.isBlendLocked();
    }

    private static boolean alphaWritesReachVanilla() {
        final DeferredAlphaHandler handler = GLSMHooks.alphaHandler;
        return handler == null || !handler.isOverrideHeld() || handler.isAlphaTestLocked();
    }

    private static boolean depthColorWritesReachVanilla() {
        final DeferredDepthColorHandler handler = GLSMHooks.depthColorHandler;
        return handler == null || !handler.isOverrideHeld() || handler.isDepthColorLocked();
    }

    private static int overridesInControl() {
        return (blendWritesReachVanilla() ? 0 : 1) | (alphaWritesReachVanilla() ? 0 : 2) | (depthColorWritesReachVanilla() ? 0 : 4);
    }

    private static boolean same(int field, ExitState.Values a, ExitState.Values b) {
        return switch (field) {
            case COLOR -> a.color.sameAs(b.color);
            case NORMAL -> a.normal.equals(b.normal);
            case TEXCOORD -> a.texCoord.equals(b.texCoord);
            case LIGHTMAP -> a.lightmapX == b.lightmapX && a.lightmapY == b.lightmapY;
            case OVERLAY_COLOR -> a.overlayColor.equals(b.overlayColor);
            case BLEND_FUNC -> a.blend.sameAs(b.blend);
            case ALPHA_FUNC -> a.alpha.sameAs(b.alpha);
            case DEPTH_FUNC -> a.depthFunction == b.depthFunction;
            case DEPTH_MASK -> a.depthMask == b.depthMask;
            case COLOR_MASK -> a.colorMask.sameAs(b.colorMask);
            case CULL_FACE -> a.cullFace == b.cullFace;
            case FRONT_FACE -> a.frontFace == b.frontFace;
            case ACTIVE_UNIT -> a.activeUnit == b.activeUnit;
            case TEXTURE_BINDING -> a.texture == b.texture;
            case MATRIX_MODE -> a.matrixMode == b.matrixMode;
            case TEXTURE_MATRIX -> a.textureMatrix.equals(b.textureMatrix);
            case COLOR_MATERIAL_SETTINGS -> a.materialFace == b.materialFace && a.materialParameter == b.materialParameter;
            default -> throw new IllegalArgumentException("Unsupported renderer state dependency " + field);
        };
    }

    private static Vector4f vector(MaterialState state, int component) {
        return switch (component) {
            case 0 -> state.ambient;
            case 1 -> state.diffuse;
            case 2 -> state.specular;
            default -> state.emission;
        };
    }

    private static boolean has(ExitState state, int field) {
        return has(state.writes, field);
    }

    private static boolean has(long fields, int field) {
        return (fields & (1L << field)) != 0;
    }

    private static long bits(int... fields) {
        long bits = 0;
        for (int field : fields) bits |= 1L << field;
        return bits;
    }

    boolean active() {
        return result != null && result.valid && suspended == 0 && !context.poppingAttributes && !BatchStateGuard.isSuspended();
    }

    void suspend() {
        suspended++;
    }

    void resume() {
        if (suspended == 0) throw new IllegalStateException("Exit capture suspension underflow");
        suspended--;
    }

    void begin(ExitState out) {
        out.clear();
        if (result != null) {
            result.valid = false;
            return;
        }
        result = out;
        openCaptures.incrementAndGet();
        filterTexture = 0;
        filterOperation = false;
        out.valid = suspended == 0 && !BatchStateGuard.isSuspended() && GLStateManager.isCachingEnabled() && !DisplayListManager.isRecording() && context.savedFilterTextureId == 0;
        if (!out.valid) return;
        out.dependencies = IMPLICIT_INPUTS;
        capture(out.entry, IMPLICIT_INPUTS | DRAW_INPUTS | MATERIAL_INPUTS | MATERIAL_BAKE_INPUTS | VERTEX_INPUTS | (1L << TEXTURE_MATRIX));
        textureInherited = true;
        modelView.set(context.modelViewMatrix);
        attribDepth = context.attribDepth;
        modelViewDepth = GLStateManager.getMatrixStackDepth(context.modelViewMatrix);
        textureDepth = GLStateManager.getMatrixStackDepth(context.textures.getTextureUnitMatrix(0));
        entryOverrides = overridesInControl();
    }

    boolean finish(ExitState out) {
        if (result != out) {
            out.clear();
            return false;
        }
        if (textureInherited && has(out, TEXTURE_MATRIX)) out.dependencies |= 1L << TEXTURE_MATRIX;
        result = null;
        openCaptures.decrementAndGet();
        if (out.valid) out.valid = suspended == 0 && filterTexture == 0 && stacksMatchEntry() && overridesInControl() == entryOverrides;
        if (out.valid) {
            capture(out.value, out.writes);
            if ((out.writes & (15L << FRONT_AMBIENT)) != 0) out.frontMaterial.set(context.frontMaterial);
            if ((out.writes & (15L << BACK_AMBIENT)) != 0) out.backMaterial.set(context.backMaterial);
            out.textureDeleteCount = TextureInfoCache.INSTANCE.deleteCount();
        }
        filterTexture = 0;
        return out.valid;
    }

    private boolean stacksMatchEntry() {
        return context.attribDepth == attribDepth && GLStateManager.getMatrixStackDepth(context.modelViewMatrix) == modelViewDepth
            && GLStateManager.getMatrixStackDepth(context.textures.getTextureUnitMatrix(0)) == textureDepth && modelView.equals(context.modelViewMatrix);
    }

    void cancel() {
        if (result != null) {
            result.clear();
            openCaptures.decrementAndGet();
        }
        result = null;
        filterTexture = 0;
        filterOperation = false;
    }

    void reject() {
        if (active()) result.valid = false;
    }

    // Callers record before their cache's unchanged-value early return
    void write(int field) {
        if (active()) record(field);
    }

    void writeBlend() {
        if (active() && blendWritesReachVanilla()) record(ExitStateCapture.BLEND_FUNC);
    }

    void writeAlpha() {
        if (active() && alphaWritesReachVanilla()) record(ExitStateCapture.ALPHA_FUNC);
    }

    void writeDepthColor(int field) {
        if (active() && depthColorWritesReachVanilla()) record(field);
    }

    private void record(int field) {
        result.writes |= 1L << field;
    }

    void requireDrawState(boolean materialFromState) {
        if (!active()) return;
        long fields = (materialFromState ? DRAW_INPUTS | MATERIAL_INPUTS : DRAW_INPUTS) & ~(result.writes | result.dependencies);
        if ((fields & (1L << BLEND_FUNC)) != 0 && !context.blendMode.isEffectivelyEnabled()) fields &= ~(1L << BLEND_FUNC);
        if ((fields & (1L << ALPHA_FUNC)) != 0 && !context.alphaTest.isEffectivelyEnabled()) fields &= ~(1L << ALPHA_FUNC);
        result.dependencies |= fields;
        if (textureInherited) result.dependencies |= 1L << TEXTURE_MATRIX;
    }

    void requireMaterialBakeInputs() {
        if (active()) result.dependencies |= MATERIAL_BAKE_INPUTS & ~result.writes;
    }

    void requireVertexDefaults(boolean normal, boolean texCoord, boolean color, boolean lightmap) {
        if (!active()) return;
        final long fields = (normal ? 1L << NORMAL : 0) | (texCoord ? 1L << TEXCOORD : 0) | (color ? 1L << COLOR : 0) | (lightmap ? 1L << LIGHTMAP : 0);
        result.dependencies |= fields & ~result.writes;
    }

    boolean beginTextureFilter(boolean nearest) {
        if (filterOperationBlocked()) return false;
        if (!nearest || filterTexture != 0 || context.savedFilterTextureId <= 0 || context.activeTextureUnit.getValue() != 0 || context.savedFilterMin < 0 || context.savedFilterMag < 0 || !(context.savedFilterAniso >= 1)) {
            reject();
            return false;
        }
        filterTexture = context.savedFilterTextureId;
        filterMin = context.savedFilterMin;
        filterMag = context.savedFilterMag;
        filterAniso = context.savedFilterAniso;
        startFilterOperation();
        return true;
    }

    void finishTextureFilterSave() {
        endFilterOperation();
        if (active() && (savedFilterDiffers() || context.activeTextureUnit.getValue() != 0 || context.textures.getTextureUnitBindings(0).getBinding() != filterTexture || filterDiffersFrom(GL11.GL_NEAREST, GL11.GL_NEAREST, 1))) reject();
    }

    boolean beginTextureFilterRestore() {
        if (filterOperationBlocked()) return false;
        if (savedFilterDiffers()) {
            reject();
            return false;
        }
        startFilterOperation();
        return true;
    }

    void finishTextureFilterRestore() {
        endFilterOperation();
        if (active() && (savedFilterDiffers() || filterDiffersFrom(filterMin, filterMag, filterAniso))) reject();
        filterTexture = 0;
    }

    private boolean filterOperationBlocked() {
        if (filterOperation) {
            failTextureFilter();
            return true;
        }
        return !active();
    }

    private void startFilterOperation() {
        filterOperation = true;
        suspend();
    }

    private void endFilterOperation() {
        resume();
        filterOperation = false;
    }

    void failTextureFilter() {
        if (result != null) result.valid = false;
    }

    void requireTextureFilter(int texture, boolean nearest) {
        if (filterTexture != 0 && texture == filterTexture && !nearest && active()) reject();
    }

    private boolean savedFilterDiffers() {
        return filterTexture == 0 || context.savedFilterTextureId != filterTexture || context.savedFilterMin != filterMin || context.savedFilterMag != filterMag || context.savedFilterAniso != filterAniso;
    }

    private boolean filterDiffersFrom(int min, int mag, float aniso) {
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(filterTexture);
        return info == null || info.getMinFilter() != min || info.getMagFilter() != mag || info.getMaxAnisotropy() != aniso;
    }

    boolean lightingWritten() {
        return active() && has(result, LIGHTING);
    }

    int polygonOffsetWrites() {
        if (!active()) return 0;
        return (has(result, OFFSET) ? ExitState.POLYGON_OFFSET_ENABLE_WRITTEN : 0) | (has(result, OFFSET_VALUES) ? ExitState.POLYGON_OFFSET_VALUES_WRITTEN : 0);
    }

    void capability(int cap, int unit) {
        if (!active()) return;
        int field = -1;
        for (int i = 0; i < CAPS.length; i++) {
            if (CAPS[i] == cap) {
                field = i;
                break;
            }
        }
        if (field == TEXTURE0) field = unit == 0 ? TEXTURE0 : unit == 1 ? TEXTURE1 : -1;
        if (field < 0) result.valid = false;
        else if (field == BLEND ? blendWritesReachVanilla() : field != ALPHA || alphaWritesReachVanilla()) record(field);
    }

    void material(int face, int parameter) {
        if (!active()) return;
        int first = switch (parameter) {
            case GL11.GL_AMBIENT, GL11.GL_AMBIENT_AND_DIFFUSE -> 0;
            case GL11.GL_DIFFUSE -> 1;
            case GL11.GL_SPECULAR -> 2;
            case GL11.GL_EMISSION -> 3;
            default -> -1;
        };
        if (first < 0) {
            result.valid = false;
            return;
        }
        if (face != GL11.GL_BACK) {
            record(FRONT_AMBIENT + first);
            if (parameter == GL11.GL_AMBIENT_AND_DIFFUSE) record(FRONT_DIFFUSE);
        }
        if (face != GL11.GL_FRONT) {
            record(BACK_AMBIENT + first);
            if (parameter == GL11.GL_AMBIENT_AND_DIFFUSE) record(BACK_DIFFUSE);
        }
    }

    // Pushes and pops check result rather than active()
    void pushAttrib() {
        if (result == null) return;
        if (context.attribDepth < attribWrites.length) attribWrites[context.attribDepth] = result.writes;
        else result.valid = false;
    }

    void popAttrib(StateSet set) {
        if (result == null) return;
        if (context.attribDepth <= attribDepth) {
            result.valid = false;
            return;
        }
        final long restored = restoredFields(set);
        result.writes = (result.writes & ~restored) | (attribWrites[context.attribDepth - 1] & restored);
    }

    private long restoredFields(StateSet set) {
        long restored = restoredFieldsBySet.getOrDefault(set, -1L);
        if (restored >= 0) return restored;
        restored = 0;
        for (int field = 0; field < FIELD_COUNT; field++) {
            final CowStateStack<?> stack = stack(field);
            if (stack != null && set.contains(stack.stackId())) restored |= 1L << field;
        }
        restoredFieldsBySet.put(set, restored);
        return restored;
    }

    private CowStateStack<?> stack(int field) {
        return switch (field) {
            case BLEND -> context.blendMode;
            case ALPHA -> context.alphaTest;
            case DEPTH -> context.depthTest;
            case CULL -> context.cullState;
            case LIGHTING -> context.lightingState;
            case NORMALIZE -> context.normalizeState;
            case RESCALE -> context.rescaleNormalState;
            case LIGHT0, LIGHT1 -> context.lightStates[field - LIGHT0];
            case TEXTURE0, TEXTURE1 -> context.textures.getTextureUnitStates(field - TEXTURE0);
            case OFFSET -> context.polygonOffsetFillState;
            case COLOR_MATERIAL -> context.colorMaterial;
            case COLOR -> context.color;
            case NORMAL -> context.ffp.normalStack;
            case TEXCOORD -> context.ffp.texCoordStack;
            case BLEND_FUNC -> context.blendState;
            case ALPHA_FUNC -> context.alphaState;
            case DEPTH_FUNC, DEPTH_MASK -> context.depthState;
            case COLOR_MASK -> context.colorMask;
            case CULL_FACE, FRONT_FACE, OFFSET_VALUES -> context.polygonState;
            case SHADE_MODEL -> context.shadeModelState;
            case ACTIVE_UNIT -> context.activeTextureUnit;
            case TEXTURE_BINDING -> context.textures.getTextureUnitBindings(0);
            case MATRIX_MODE -> context.matrixMode;
            // Face and parameter are restored together (GL_LIGHTING_BIT and the batch set hold both).
            case COLOR_MATERIAL_SETTINGS -> context.colorMaterialFace;
            case FRONT_AMBIENT, FRONT_DIFFUSE, FRONT_SPECULAR, FRONT_EMISSION -> context.frontMaterial;
            case BACK_AMBIENT, BACK_DIFFUSE, BACK_SPECULAR, BACK_EMISSION -> context.backMaterial;
            default -> null;
        };
    }

    void matrixChange(boolean absolute) {
        if (result == null) return;
        final int mode = context.matrixMode.getMode();
        if ((absolute || mode != GL11.GL_MODELVIEW) && active()) recordMatrixChange(mode, context.activeTextureUnit.getValue(), absolute);
    }

    void matrixChange(int mode, int unit, boolean absolute) {
        if (active()) recordMatrixChange(mode, unit, absolute);
    }

    private void recordMatrixChange(int mode, int unit, boolean absolute) {
        if (mode == GL11.GL_MODELVIEW) {
            if (absolute) result.valid = false;
            return;
        }
        if (mode != GL11.GL_TEXTURE || unit != 0) {
            result.valid = false;
            return;
        }
        record(TEXTURE_MATRIX);
        if (absolute) textureInherited = false;
    }

    void pushMatrix() {
        if (result == null) return;
        final int mode = context.matrixMode.getMode();
        if (mode == GL11.GL_TEXTURE && context.activeTextureUnit.getValue() == 0) {
            final int depth = GLStateManager.getMatrixStackDepth(context.textures.getTextureUnitMatrix(0));
            if (depth < matrixWrites.length) {
                matrixWrites[depth] = result.writes;
                matrixInherited[depth] = textureInherited;
            } else {
                result.valid = false;
            }
        } else if (mode != GL11.GL_MODELVIEW) {
            reject();
        }
    }

    void popMatrix() {
        if (result == null) return;
        final int mode = context.matrixMode.getMode();
        final boolean texture = mode == GL11.GL_TEXTURE && context.activeTextureUnit.getValue() == 0;
        final Matrix4fStack matrix;
        final int entryDepth;
        if (mode == GL11.GL_MODELVIEW) {
            matrix = context.modelViewMatrix;
            entryDepth = modelViewDepth;
        } else if (texture) {
            matrix = context.textures.getTextureUnitMatrix(0);
            entryDepth = textureDepth;
        } else {
            reject();
            return;
        }
        final int depth = GLStateManager.getMatrixStackDepth(matrix);
        if (depth <= entryDepth) {
            result.valid = false;
            return;
        }
        if (texture) {
            final long bit = 1L << TEXTURE_MATRIX;
            result.writes = (result.writes & ~bit) | (matrixWrites[depth - 1] & bit);
            textureInherited = matrixInherited[depth - 1];
        }
    }

    private void capture(ExitState.Values out, long fields) {
        out.enabled = 0;
        for (int field = 0; field < CAPS.length; field++) {
            if (has(fields, field) && ((BooleanStateStack) Objects.requireNonNull(stack(field))).isEffectivelyEnabled()) out.enabled |= 1L << field;
        }
        if (has(fields, COLOR)) out.color.set(context.color);
        if (has(fields, NORMAL)) out.normal.set(context.ffp.currentNormal);
        if (has(fields, TEXCOORD)) out.texCoord.set(context.ffp.currentTexCoords[0]);
        if (has(fields, LIGHTMAP)) {
            out.lightmapX = context.lastBrightnessX;
            out.lightmapY = context.lastBrightnessY;
        }
        if (has(fields, SHADER_COLOR)) out.shaderColor.set(context.shaderColorR, context.shaderColorG, context.shaderColorB, context.shaderColorA);
        if (has(fields, OVERLAY_COLOR)) out.overlayColor.set(context.overlayR, context.overlayG, context.overlayB, context.overlayA);
        if (has(fields, BLEND_FUNC)) context.blendState.readEffective(out.blend);
        if (has(fields, ALPHA_FUNC)) context.alphaState.readEffective(out.alpha);
        if (has(fields, COLOR_MASK)) context.colorMask.readEffective(out.colorMask);
        if (has(fields, DEPTH_FUNC)) out.depthFunction = context.depthState.getFunc();
        if (has(fields, DEPTH_MASK)) out.depthMask = context.depthState.isEffectiveMaskEnabled();
        if (has(fields, CULL_FACE)) out.cullFace = context.polygonState.getCullFaceMode();
        if (has(fields, FRONT_FACE)) out.frontFace = context.polygonState.getFrontFace();
        if (has(fields, OFFSET_VALUES)) {
            out.offsetFactor = context.polygonState.getOffsetFactor();
            out.offsetUnits = context.polygonState.getOffsetUnits();
            out.offsetClamp = context.polygonState.getOffsetClamp();
        }
        if (has(fields, SHADE_MODEL)) out.shadeModel = context.shadeModelState.getValue();
        if (has(fields, ACTIVE_UNIT)) out.activeUnit = context.activeTextureUnit.getValue();
        if (has(fields, CLIENT_ACTIVE_UNIT)) out.clientActiveUnit = context.clientActiveTextureUnit;
        if (has(fields, MATRIX_MODE)) out.matrixMode = context.matrixMode.getMode();
        if (has(fields, TEXTURE_BINDING)) out.texture = context.textures.getTextureUnitBindings(0).getBinding();
        if (has(fields, TEXTURE_MATRIX)) out.textureMatrix.set(context.textures.getTextureUnitMatrix(0));
        if (has(fields, COLOR_MATERIAL_SETTINGS)) {
            out.materialFace = context.colorMaterialFace.getValue();
            out.materialParameter = context.colorMaterialParameter.getValue();
        }
    }

    boolean canApply(ExitState in) {
        if (!in.valid || result != null || !GLStateManager.isCachingEnabled() || DisplayListManager.isRecording() || context.savedFilterTextureId != 0) return false;
        if (in.textureDeleteCount != TextureInfoCache.INSTANCE.deleteCount()) return false;
        capture(current, in.dependencies);
        if (((current.enabled ^ in.entry.enabled) & in.dependencies & ((1L << CAPS.length) - 1)) != 0) return false;
        long remaining = in.dependencies & -(1L << CAPS.length);
        while (remaining != 0) {
            final int field = Long.numberOfTrailingZeros(remaining);
            if (!same(field, in.entry, current)) return false;
            remaining &= remaining - 1;
        }
        return true;
    }

    void apply(ExitState state) {
        if (!state.valid) throw new IllegalStateException("Invalid renderer exit state");
        if (result != null) throw new IllegalStateException("Exit state applied while a capture is open");
        final ExitState.Values in = state.value;
        final long fields = state.writes;
        final int liveUnit = context.activeTextureUnit.getValue();
        // Capabilities go first so the glColor4f below bakes into the material only if color material is on at exit.
        for (int field = 0; field < CAPS.length; field++) {
            if (!has(fields, field)) continue;
            if (field == TEXTURE0 || field == TEXTURE1) GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + field - TEXTURE0);
            // glEnable would bake the live color, not the exit color, into the material.
            if (field == COLOR_MATERIAL) {
                context.colorMaterial.setEnabled((in.enabled & (1L << field)) != 0);
            } else if ((in.enabled & (1L << field)) != 0) GLStateManager.glEnable(CAPS[field]);
            else GLStateManager.glDisable(CAPS[field]);
        }
        if (has(fields, COLOR)) GLStateManager.glColor4f(in.color.getRed(), in.color.getGreen(), in.color.getBlue(), in.color.getAlpha());
        if (has(fields, NORMAL)) GLStateManager.glNormal3f(in.normal.x, in.normal.y, in.normal.z);
        if (has(fields, TEXCOORD)) GLStateManager.glTexCoord4f(in.texCoord.x, in.texCoord.y, in.texCoord.z, in.texCoord.w);
        if (has(fields, LIGHTMAP)) GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, in.lightmapX, in.lightmapY);
        if (has(fields, SHADER_COLOR)) GLStateManager.setShaderColor(in.shaderColor.x, in.shaderColor.y, in.shaderColor.z, in.shaderColor.w);
        if (has(fields, OVERLAY_COLOR)) GLStateManager.setOverlayColor(in.overlayColor.x, in.overlayColor.y, in.overlayColor.z, in.overlayColor.w);
        if (has(fields, BLEND_FUNC)) GLStateManager.tryBlendFuncSeparate(in.blend.getSrcRgb(), in.blend.getDstRgb(), in.blend.getSrcAlpha(), in.blend.getDstAlpha());
        if (has(fields, ALPHA_FUNC)) GLStateManager.glAlphaFunc(in.alpha.getFunction(), in.alpha.getReference());
        if (has(fields, DEPTH_FUNC)) GLStateManager.glDepthFunc(in.depthFunction);
        if (has(fields, DEPTH_MASK)) GLStateManager.glDepthMask(in.depthMask);
        if (has(fields, COLOR_MASK)) GLStateManager.glColorMask(in.colorMask.red, in.colorMask.green, in.colorMask.blue, in.colorMask.alpha);
        if (has(fields, CULL_FACE)) GLStateManager.glCullFace(in.cullFace);
        if (has(fields, FRONT_FACE)) GLStateManager.glFrontFace(in.frontFace);
        if (has(fields, SHADE_MODEL)) GLStateManager.glShadeModel(in.shadeModel);
        if (has(fields, OFFSET_VALUES)) GLStateManager.glPolygonOffsetClamp(in.offsetFactor, in.offsetUnits, in.offsetClamp);
        if (has(fields, TEXTURE_BINDING)) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, in.texture);
        }
        if (has(fields, TEXTURE_MATRIX)) GLStateManager.setTextureMatrix(0, in.textureMatrix);
        if (has(fields, COLOR_MATERIAL_SETTINGS)) GLStateManager.glColorMaterial(in.materialFace, in.materialParameter);
        boolean materialChanged = false;
        for (int field = FRONT_AMBIENT; field <= BACK_EMISSION; field++) {
            if (!has(fields, field)) continue;
            final boolean front = field < BACK_AMBIENT;
            final int component = (field - FRONT_AMBIENT) & 3;
            final Vector4f source = vector(front ? state.frontMaterial : state.backMaterial, component);
            final var target = front ? context.frontMaterial : context.backMaterial;
            if (vector(target, component).equals(source)) continue;
            target.beforeModify();
            vector(target, component).set(source);
            materialChanged = true;
        }
        if (materialChanged) context.lightingGeneration++;
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + (has(fields, ACTIVE_UNIT) ? in.activeUnit : liveUnit));
        if (has(fields, CLIENT_ACTIVE_UNIT)) GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + in.clientActiveUnit);
        if (has(fields, MATRIX_MODE)) GLStateManager.glMatrixMode(in.matrixMode);
    }
}
