package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredDepthColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.CowDepths;
import com.gtnewhorizons.angelica.glsm.stacks.CowDispatch;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.RetainedState;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import com.gtnewhorizons.angelica.glsm.states.SamplerUnitArray;
import com.gtnewhorizons.angelica.glsm.states.TextureBinding;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_ATTRIB_STACK_DEPTH;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_UNITS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.RETAINED_SLOT;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.primaryContext;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnce;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMAttribStack {

    private GLSMAttribStack() {}

    public static long attribPushes;
    public static long attribSlotsSaved;
    public static long attribValueRestores;
    public static long attribBackendCalls;
    public static long attribDiscards;

    /** Register a state stack id as modified at the current depth (called from beforeModify). */
    public static void registerModifiedState(GLContextState c, int id) {
        if (c.attribDepth > 0) {
            final int depth = c.attribDepth - 1;
            c.modifiedIds[depth][c.modifiedCount[depth]++] = id;
            if (!c.poppingAttributes) {
                attribSlotsSaved++;
            }
        }
    }

    static final int CLIENT_ATTRIB_STACK_DEPTH = 16;
    public static void glPushClientAttrib(int mask) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final int sp = glCtx.clientAttribStackPointer;
        if (sp < CLIENT_ATTRIB_STACK_DEPTH) {
            glCtx.clientAttribSavedMask[sp] = mask;
            if ((mask & GL11.GL_CLIENT_PIXEL_STORE_BIT) != 0) {
                glCtx.clientAttribSavedPixelPack[sp] = glCtx.pixelPackState;
                glCtx.clientAttribSavedPixelUnpack[sp] = glCtx.pixelUnpackState;
                glCtx.clientAttribSavedPackBuffer[sp] = glCtx.boundPixelPackBuffer;
                glCtx.clientAttribSavedUnpackBuffer[sp] = glCtx.boundPixelUnpackBuffer;
            }
            if ((mask & GL11.GL_CLIENT_VERTEX_ARRAY_BIT) != 0) {
                VAOManager.ClientArrays saved = glCtx.clientAttribSavedArrays[sp];
                if (saved == null) glCtx.clientAttribSavedArrays[sp] = saved = new VAOManager.ClientArrays();
                saved.boundVAO = glCtx.boundVAO;
                saved.boundVBO = glCtx.boundVBO;
                saved.boundEBO = glCtx.vaos.boundEBO;
                saved.clientActiveTextureUnit = glCtx.clientActiveTextureUnit;
                glCtx.vaos.saveClientArrays(saved);
            }
            glCtx.clientAttribStackPointer = sp + 1;
        }
    }

    public static void glPopClientAttrib() {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (glCtx.clientAttribStackPointer > 0) {
            final int sp = --glCtx.clientAttribStackPointer;
            final int mask = glCtx.clientAttribSavedMask[sp];
            if ((mask & GL11.GL_CLIENT_PIXEL_STORE_BIT) != 0) {
                final PixelStoreState pack = glCtx.clientAttribSavedPixelPack[sp];
                final PixelStoreState unpack = glCtx.clientAttribSavedPixelUnpack[sp];
                glCtx.clientAttribSavedPixelPack[sp] = null;
                glCtx.clientAttribSavedPixelUnpack[sp] = null;
                PixelStoreState.applyDiff(glCtx.pixelPackState, pack, true);
                glCtx.pixelPackState = pack;
                PixelStoreState.applyDiff(glCtx.pixelUnpackState, unpack, false);
                glCtx.pixelUnpackState = unpack;
                final int packBuffer = glCtx.clientAttribSavedPackBuffer[sp];
                if (glCtx.boundPixelPackBuffer != packBuffer) GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
                final int unpackBuffer = glCtx.clientAttribSavedUnpackBuffer[sp];
                if (glCtx.boundPixelUnpackBuffer != unpackBuffer) GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            }
            if ((mask & GL11.GL_CLIENT_VERTEX_ARRAY_BIT) != 0) {
                restoreClientArrays(glCtx, glCtx.clientAttribSavedArrays[sp]);
            }
        }
    }

    private static void restoreClientArrays(GLContextState glCtx, VAOManager.ClientArrays saved) {
        final VAOManager vaos = glCtx.vaos;
        if (!vaos.vaoMap.containsKey(saved.boundVAO)) return;
        GLSMVertexInput.bindVertexArrayInternal(glCtx, saved.boundVAO);
        glCtx.clientActiveTextureUnit = saved.clientActiveTextureUnit;
        if (vaos.isBufferLive(saved.boundVBO, saved)) {
            final int changed = vaos.restoreClientArrays(saved);
            for (int pointers = changed & 0xFFFF; pointers != 0; pointers &= pointers - 1) {
                GLSMVertexInput.pointAttrib(glCtx, Integer.numberOfTrailingZeros(pointers));
            }
            for (int enables = changed >>> 16; enables != 0; enables &= enables - 1) {
                GLSMVertexInput.syncAttribEnable(glCtx, Integer.numberOfTrailingZeros(enables));
            }
            if (glCtx.boundVBO != saved.boundVBO) GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, saved.boundVBO);
        }
        if (vaos.boundEBO != saved.boundEBO && vaos.isBufferLive(saved.boundEBO, saved)) GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, saved.boundEBO);
    }

    public static void pushState(int mask) {
        pushStateLive(StateSet.forMask(mask));
    }

    public static int pushState(StateSet set) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            final int token = ctx().attribDepth + DisplayListManager.virtualStateDepth(mode);
            if (token >= MAX_ATTRIB_STACK_DEPTH) {
                throw new IllegalStateException("Attrib stack overflow: max depth " + MAX_ATTRIB_STACK_DEPTH + " reached");
            }
            DisplayListManager.recordPushState(set);
            if (mode == RecordMode.COMPILE) {
                return token;
            }
        }
        return pushStateLive(set);
    }

    static int pushStateLive(StateSet set) {
        attribPushes++;
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.pushAttrib();
        if (glCtx.attribDepth >= MAX_ATTRIB_STACK_DEPTH) {
            throw new IllegalStateException("Attrib stack overflow: max depth " + MAX_ATTRIB_STACK_DEPTH + " reached");
        }
        final int depthBefore = glCtx.attribDepth;
        glCtx.attribSets[depthBefore] = set;
        glCtx.savedBatchStateGen[depthBefore] = glCtx.batchStateGeneration;

        snapshotGenerations(glCtx, set.bump);

        glCtx.modifiedCount[depthBefore] = 0;

        if ((set.restore & StateSet.R_PROGRAM) != 0) {
            glCtx.programStack.setValue(glCtx.activeProgram);
            glCtx.programStack.push();
            attribSlotsSaved++;
        }

        glCtx.attribDepth++;
        return depthBefore;
    }

    private static void snapshotGenerations(GLContextState glCtx, int bump) {
        final int depth = glCtx.attribDepth;
        if ((bump & StateSet.B_LIGHTING) != 0) {
            glCtx.savedLightingGen[depth] = glCtx.lightingGeneration;
        }
        if ((bump & (StateSet.B_FRAG_COLORBUF | StateSet.B_FRAG_TEXTURE | StateSet.B_FRAG_FOG)) != 0) {
            glCtx.savedFragmentGen[depth] = glCtx.fragmentGeneration;
        }
        if ((bump & StateSet.B_TRANSFORM) != 0) {
            glCtx.savedMvGen[depth] = glCtx.mvGeneration;
            glCtx.savedMvLinearGen[depth] = glCtx.mvLinearGeneration;
            glCtx.savedProjGen[depth] = glCtx.projGeneration;
            glCtx.savedTexMatGen[depth] = glCtx.texMatrixGeneration;
        }
        if ((bump & StateSet.B_CURRENT) != 0) {
            glCtx.savedColorGen[depth] = glCtx.colorGeneration;
            glCtx.savedNormalGen[depth] = glCtx.ffp.normalGeneration;
            glCtx.savedTexCoordGen[depth] = glCtx.ffp.texCoordGeneration;
        }
    }

    public static void popStateTo(int depth) {
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        while (glCtx.attribDepth + DisplayListManager.virtualStateDepth(mode) > depth) {
            if (mode == RecordMode.COMPILE && DisplayListManager.recordPopStateIfPending()) continue;
            if (mode == RecordMode.COMPILE_AND_EXECUTE) DisplayListManager.recordPopStateIfPending();
            popState();
        }
    }

    public static void popState() {
        final GLContextState glCtx = ctx();
        if (glCtx.attribDepth > 0 && glCtx.savedBatchStateGen[glCtx.attribDepth - 1] != glCtx.batchStateGeneration) {
            beforeUncapturedStateChange();
        }
        final int poppedDepth = glCtx.attribDepth;
        final StateSet set = glCtx.attribSets[poppedDepth - 1];
        glCtx.exitCapture.popAttrib(set);
        final boolean inspectAlpha = GLSMHooks.ALPHA_STATE_CHANGE.hasListeners()
            && ((glCtx.alphaState.cowDepths().claimedAt(poppedDepth) && set.contains(glCtx.alphaState.stackId()))
                || (glCtx.alphaTest.cowDepths().claimedAt(poppedDepth) && set.contains(glCtx.alphaTest.stackId())));
        final AlphaState effectiveAlpha = inspectAlpha ? glCtx.alphaState.readEffective(glCtx.restoredAlphaScratch) : null;
        final boolean effectiveEnabled = inspectAlpha && glCtx.alphaTest.isEffectivelyEnabled();
        final int effectiveFunction = inspectAlpha ? effectiveAlpha.getFunction() : 0;
        final float effectiveReference = inspectAlpha ? effectiveAlpha.getReference() : 0.0f;
        final boolean liveEnabled = inspectAlpha && glCtx.alphaTest.isEnabled();
        final int liveFunction = inspectAlpha ? glCtx.alphaState.getFunction() : 0;
        final float liveReference = inspectAlpha ? glCtx.alphaState.getReference() : 0.0f;
        final boolean prevPoppingAttributes = glCtx.poppingAttributes;
        glCtx.poppingAttributes = true;
        try {
            glCtx.attribDepth--;
            final int restore = set.restore;

            final boolean blendSnapshotted = set.blendSnapshot
                && (glCtx.blendState.cowDepths().claimedAt(poppedDepth) || glCtx.blendMode.cowDepths().claimedAt(poppedDepth))
                && GLSMBlend.snapshotVanillaBlendFunc();

            int changed = 0;
            long unitsChanged = 0L;

            if ((restore & StateSet.R_PROGRAM) != 0) {
                glCtx.programStack.setValue(glCtx.activeProgram);
                if (glCtx.programStack.topSlotChanged()) changed |= StateSet.R_PROGRAM;
                glCtx.programStack.pop();
            }

            final int depth = glCtx.attribDepth;
            final int count = glCtx.modifiedCount[depth];
            final int[] ids = glCtx.modifiedIds[depth];
            final CowStateStack<?>[] stackById = glCtx.stackById;
            final CowDepths[] depthsById = glCtx.depthsById;
            final int[] restoreBitById = glCtx.restoreBitById;
            final int[] restoreUnitById = glCtx.restoreUnitById;
            final byte[] kindById = glCtx.kindById;

            for (int i = 0; i < count; i++) {
                final int id = ids[i];
                final CowStateStack<?> cow = stackById[id];
                final byte kind = kindById[id];
                if (set.contains(id)) {
                    final int bit = restoreBitById[id];
                    if (bit != 0 && CowDispatch.topSlotChanged(kind, cow)) {
                        changed |= bit;
                        final int unit = restoreUnitById[id];
                        if (unit >= 0) unitsChanged |= 1L << Math.min(unit, 63);
                    }
                    final int slot = depthsById[id].pop();
                    if (slot >= 0) CowDispatch.restoreSlot(kind, cow, slot);
                    if (bit == 0
                        && (kind == CowDispatch.BOOLEAN
                            || kind == CowDispatch.CLIP_PLANE_BOOLEAN
                            || kind == CowDispatch.TEXTURE_UNIT_BOOLEAN)) attribValueRestores++;
                } else {
                    if (depthsById[id].discard(poppedDepth) >= 0) registerModifiedState(glCtx, id);
                    attribDiscards++;
                }
            }
            glCtx.modifiedCount[depth] = 0;

            glCtx.restoreChangedMask = changed;
            glCtx.restoreUnitChangedMask = unitsChanged;

            applyRestoredState(set, blendSnapshotted);
        } finally {
            glCtx.poppingAttributes = prevPoppingAttributes;
        }
        if (inspectAlpha) {
            glCtx.alphaState.readEffective(glCtx.restoredAlphaScratch);
            if (effectiveEnabled != glCtx.alphaTest.isEffectivelyEnabled()
                || effectiveFunction != effectiveAlpha.getFunction()
                || Float.compare(effectiveReference, effectiveAlpha.getReference()) != 0
                || liveEnabled != glCtx.alphaTest.isEnabled()
                || liveFunction != glCtx.alphaState.getFunction()
                || Float.compare(liveReference, glCtx.alphaState.getReference()) != 0) {
                GLSMHooks.ALPHA_STATE_CHANGE.post(GLSMHooks.alphaStateChangeEvent);
            }
        }
    }

    public static void retainModifiedState(int depth, RetainedState out) {
        final GLContextState glCtx = ctx();
        if (depth < 0 || depth != glCtx.attribDepth - 1) {
            throw new IllegalStateException("retainModifiedState: depth " + depth + " is not the open push (attrib depth " + glCtx.attribDepth + ")");
        }
        glCtx.retainedOwner = out;
        final int count = glCtx.modifiedCount[depth];
        final int[] ids = glCtx.modifiedIds[depth];
        final CowStateStack<?>[] stackById = glCtx.stackById;
        out.ensureCapacity(count);
        for (int i = 0; i < count; i++) {
            final int id = ids[i];
            stackById[id].captureSlot(RETAINED_SLOT);
            out.ids[i] = id;
        }
        out.count = count;
        out.program = glCtx.activeProgram;
        out.programGen = GLSMPrograms.programLifetimeGeneration;
    }

    public static void applyRetainedState(RetainedState in) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (glCtx.retainedOwner != in) return;
        final CowStateStack<?>[] stackById = glCtx.stackById;
        final byte[] kindById = glCtx.kindById;
        final int[] ids = in.ids;
        final int count = in.count;
        int first = 0;
        while (first < count && !CowDispatch.slotChanged(kindById[ids[first]], stackById[ids[first]], RETAINED_SLOT)) first++;
        if (first < count) applyRetainedChanges(glCtx, in, first);
        if (in.program != glCtx.activeProgram && (in.program == 0 || in.programGen == GLSMPrograms.programLifetimeGeneration)) GLSMPrograms.glUseProgram(in.program);
    }

    private static void applyRetainedChanges(GLContextState glCtx, RetainedState in, int first) {
        beforeUncapturedStateChange();
        final boolean blendSnapshotted = GLSMBlend.snapshotVanillaBlendFunc();
        final int alphaFuncId = glCtx.alphaState.stackId();
        final int alphaTestId = glCtx.alphaTest.stackId();
        boolean alphaChanged = false;
        final boolean prevPoppingAttributes = glCtx.poppingAttributes;
        glCtx.poppingAttributes = true;
        try {
            final CowStateStack<?>[] stackById = glCtx.stackById;
            final int[] restoreBitById = glCtx.restoreBitById;
            final int[] restoreUnitById = glCtx.restoreUnitById;
            final byte[] kindById = glCtx.kindById;
            int changed = 0;
            long unitsChanged = 0L;
            for (int i = first; i < in.count; i++) {
                final int id = in.ids[i];
                final CowStateStack<?> cow = stackById[id];
                final byte kind = kindById[id];
                if (!CowDispatch.slotChanged(kind, cow, RETAINED_SLOT)) continue;
                cow.beforeModify();
                final int bit = restoreBitById[id];
                if (bit != 0) {
                    changed |= bit;
                    final int unit = restoreUnitById[id];
                    if (unit >= 0) unitsChanged |= 1L << Math.min(unit, 63);
                }
                CowDispatch.restoreSlot(kind, cow, RETAINED_SLOT);
                if (id == alphaFuncId || id == alphaTestId) alphaChanged = true;
            }
            glCtx.restoreChangedMask = changed;
            glCtx.restoreUnitChangedMask = unitsChanged;
            issueRestoredBackendState(glCtx, -1);
            glCtx.lightingGeneration++;
            glCtx.fragmentGeneration++;
            glCtx.colorGeneration++;
            glCtx.ffp.normalGeneration++;
            glCtx.ffp.texCoordGeneration++;
            glCtx.dirtyColorAttrib = true;
            glCtx.dirtyNormalAttrib = true;
            glCtx.dirtyTexCoordAttrib = true;
            GLSMBlend.postVanillaBlendChangeIfMoved(blendSnapshotted);
        } finally {
            glCtx.poppingAttributes = prevPoppingAttributes;
        }
        if (alphaChanged && GLSMHooks.ALPHA_STATE_CHANGE.hasListeners()) {
            GLSMHooks.ALPHA_STATE_CHANGE.post(GLSMHooks.alphaStateChangeEvent);
        }
    }

    private static boolean isDepthColorOverridden() {
        final DeferredDepthColorHandler dch = GLSMHooks.depthColorHandler;
        return dch != null && dch.isOverrideHeld();
    }

    public static void replayStateToBackend() {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (RENDER_BACKEND == null) return;

        final List<BooleanStateStack> booleanStates = glCtx.allBooleanStates;
        for (int i = 0; i < booleanStates.size(); i++) {
            booleanStates.get(i).applyToBackend();
        }

        RENDER_BACKEND.depthFunc(glCtx.depthState.getFunc());
        RENDER_BACKEND.depthMask(glCtx.depthState.isEnabled());
        RENDER_BACKEND.clearDepth(glCtx.depthState.getClearValue());

        GLSMBlend.issueBlendFunc(glCtx.blendState);
        glCtx.blendState.clearFuncUnknownState();
        RENDER_BACKEND.blendEquationSeparate(glCtx.blendState.getEquationRgb(), glCtx.blendState.getEquationAlpha());
        RENDER_BACKEND.blendColor(glCtx.blendState.getBlendColorR(), glCtx.blendState.getBlendColorG(), glCtx.blendState.getBlendColorB(), glCtx.blendState.getBlendColorA());
        RENDER_BACKEND.colorMask(glCtx.colorMask.red, glCtx.colorMask.green, glCtx.colorMask.blue, glCtx.colorMask.alpha);
        RENDER_BACKEND.clearColor(glCtx.clearColor.getRed(), glCtx.clearColor.getGreen(), glCtx.clearColor.getBlue(), glCtx.clearColor.getAlpha());
        RENDER_BACKEND.logicOp(glCtx.logicOpMode.getValue());

        RENDER_BACKEND.stencilFuncSeparate(GL11.GL_FRONT, glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), glCtx.stencilState.getValueMaskFront());
        RENDER_BACKEND.stencilFuncSeparate(GL11.GL_BACK, glCtx.stencilState.getFuncBack(), glCtx.stencilState.getRefBack(), glCtx.stencilState.getValueMaskBack());
        RENDER_BACKEND.stencilOpSeparate(GL11.GL_FRONT, glCtx.stencilState.getFailOpFront(), glCtx.stencilState.getZFailOpFront(), glCtx.stencilState.getZPassOpFront());
        RENDER_BACKEND.stencilOpSeparate(GL11.GL_BACK, glCtx.stencilState.getFailOpBack(), glCtx.stencilState.getZFailOpBack(), glCtx.stencilState.getZPassOpBack());
        RENDER_BACKEND.stencilMaskSeparate(GL11.GL_FRONT, glCtx.stencilState.getWriteMaskFront());
        RENDER_BACKEND.stencilMaskSeparate(GL11.GL_BACK, glCtx.stencilState.getWriteMaskBack());
        RENDER_BACKEND.clearStencil(glCtx.stencilState.getClearValue());

        RENDER_BACKEND.viewport(glCtx.viewportState.x, glCtx.viewportState.y, glCtx.viewportState.width, glCtx.viewportState.height);
        RENDER_BACKEND.depthRange(glCtx.viewportState.depthRangeNear, glCtx.viewportState.depthRangeFar);
        RENDER_BACKEND.scissor(glCtx.scissorState.x, glCtx.scissorState.y, glCtx.scissorState.width, glCtx.scissorState.height);
        RENDER_BACKEND.lineWidth(Math.clamp(glCtx.lineState.getWidth(), GLSMRasterizer.lineWidthMin, GLSMRasterizer.lineWidthMax));
        GLSMRasterizer.applyPointState(glCtx);
        GLSMRasterizer.applyHintState(glCtx);

        RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, glCtx.polygonState.getFrontMode());
        applyPolygonOffset(glCtx);
        RENDER_BACKEND.cullFace(glCtx.polygonState.getCullFaceMode());
        RENDER_BACKEND.frontFace(glCtx.polygonState.getFrontFace());

        final SamplerUnitArray samplerUnits = glCtx.samplerUnits;
        for (int i = 0; i <= samplerUnits.getHighestBoundUnit(); i++) {
            final int sampler = samplerUnits.get(i);
            if (sampler != 0) RENDER_BACKEND.bindSampler(i, sampler);
        }

        for (int i = 0; i <= glCtx.maxBoundTextureUnit; i++) {
            final TextureBinding b = glCtx.textures.getTextureUnitBindings(i);
            RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + i);
            RENDER_BACKEND.bindTexture(b.getTarget() == 0 ? GL11.GL_TEXTURE_2D : b.getTarget(), b.getBinding());
        }
        RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + GLSMTextures.getActiveTextureUnit());
        if (glCtx.defaultVAO == 0 && primaryContext().defaultVAO != 0) glCtx.defaultVAO = RENDER_BACKEND.genVertexArrays();
        if (!glCtx.vaos.isInitialized()) glCtx.vaos.init(glCtx.defaultVAO);
        RENDER_BACKEND.bindVertexArray(glCtx.boundVAO != 0 ? glCtx.boundVAO : glCtx.defaultVAO);
        RENDER_BACKEND.useProgram(glCtx.activeProgram);
        glCtx.ffp.invalidateProgram();
        RENDER_BACKEND.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, glCtx.drawFramebuffer);
        RENDER_BACKEND.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, glCtx.readFramebuffer);
        glCtx.dirtyColorAttrib = true;
        glCtx.dirtyNormalAttrib = true;
        glCtx.dirtyTexCoordAttrib = true;
        glCtx.dirtyLightmapAttrib = true;
        glCtx.dirtyEntityIdsAttrib = true;
    }

    private static void applyPolygonOffset(GLContextState glCtx) {
        final float clamp = glCtx.polygonState.getOffsetClamp();
        if (clamp == 0.0f) {
            RENDER_BACKEND.polygonOffset(glCtx.polygonState.getOffsetFactor(), glCtx.polygonState.getOffsetUnits());
        } else {
            RENDER_BACKEND.polygonOffsetClamp(glCtx.polygonState.getOffsetFactor(), glCtx.polygonState.getOffsetUnits(), clamp);
        }
    }

    private static void issueRestoredBackendState(GLContextState glCtx, int restore) {
        final int changed = glCtx.restoreChangedMask;
        if ((restore & StateSet.R_DEPTH) != 0 && (changed & StateSet.R_DEPTH) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.depthFunc(glCtx.depthState.getFunc());
            attribBackendCalls++;
            if (!isDepthColorOverridden()) {
                RENDER_BACKEND.depthMask(glCtx.depthState.isEnabled());
                attribBackendCalls++;
            }
            RENDER_BACKEND.clearDepth(glCtx.depthState.getClearValue());
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_BLEND) != 0) {
            final BlendStateStack blend = glCtx.blendState;
            if ((changed & StateSet.R_BLEND) != 0 || blend.isFuncUnknown()) {
                attribValueRestores++;
                final DeferredBlendHandler restoreBlendHandler = GLSMHooks.blendHandler;
                if (restoreBlendHandler == null || !restoreBlendHandler.isOverrideHeld()) {
                    GLSMBlend.issueBlendFunc(blend);
                    attribBackendCalls++;
                    blend.clearFuncUnknownState();
                }
                if ((changed & StateSet.R_BLEND) != 0) {
                    RENDER_BACKEND.blendEquationSeparate(blend.getEquationRgb(), blend.getEquationAlpha());
                    attribBackendCalls++;
                    RENDER_BACKEND.blendColor(
                        blend.getBlendColorR(), blend.getBlendColorG(), blend.getBlendColorB(), blend.getBlendColorA());
                    attribBackendCalls++;
                }
            }
        }
        if ((restore & StateSet.R_COLOR_MASK) != 0 && (changed & StateSet.R_COLOR_MASK) != 0 && !isDepthColorOverridden()) {
            attribValueRestores++;
            RENDER_BACKEND.colorMask(glCtx.colorMask.red, glCtx.colorMask.green, glCtx.colorMask.blue, glCtx.colorMask.alpha);
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_CLEAR_COLOR) != 0 && (changed & StateSet.R_CLEAR_COLOR) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.clearColor(glCtx.clearColor.getRed(), glCtx.clearColor.getGreen(), glCtx.clearColor.getBlue(), glCtx.clearColor.getAlpha());
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_DRAW_BUFFER) != 0 && (changed & StateSet.R_DRAW_BUFFER) != 0 && glCtx.drawFramebuffer == 0) {
            attribValueRestores++;
            RENDER_BACKEND.drawBuffer(glCtx.drawBuffer.getValue());
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_LOGIC_OP) != 0 && (changed & StateSet.R_LOGIC_OP) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.logicOp(glCtx.logicOpMode.getValue());
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_STENCIL) != 0 && (changed & StateSet.R_STENCIL) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.stencilFuncSeparate(GL11.GL_FRONT, glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), glCtx.stencilState.getValueMaskFront());
            RENDER_BACKEND.stencilFuncSeparate(GL11.GL_BACK, glCtx.stencilState.getFuncBack(), glCtx.stencilState.getRefBack(), glCtx.stencilState.getValueMaskBack());
            RENDER_BACKEND.stencilOpSeparate(GL11.GL_FRONT, glCtx.stencilState.getFailOpFront(), glCtx.stencilState.getZFailOpFront(), glCtx.stencilState.getZPassOpFront());
            RENDER_BACKEND.stencilOpSeparate(GL11.GL_BACK, glCtx.stencilState.getFailOpBack(), glCtx.stencilState.getZFailOpBack(), glCtx.stencilState.getZPassOpBack());
            RENDER_BACKEND.stencilMaskSeparate(GL11.GL_FRONT, glCtx.stencilState.getWriteMaskFront());
            RENDER_BACKEND.stencilMaskSeparate(GL11.GL_BACK, glCtx.stencilState.getWriteMaskBack());
            RENDER_BACKEND.clearStencil(glCtx.stencilState.getClearValue());
            attribBackendCalls += 7;
        }
        if ((restore & StateSet.R_VIEWPORT) != 0 && (changed & StateSet.R_VIEWPORT) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.viewport(glCtx.viewportState.x, glCtx.viewportState.y, glCtx.viewportState.width, glCtx.viewportState.height);
            RENDER_BACKEND.depthRange(glCtx.viewportState.depthRangeNear, glCtx.viewportState.depthRangeFar);
            attribBackendCalls += 2;
        }
        if ((restore & StateSet.R_SCISSOR) != 0 && (changed & StateSet.R_SCISSOR) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.scissor(glCtx.scissorState.x, glCtx.scissorState.y, glCtx.scissorState.width, glCtx.scissorState.height);
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_LINE) != 0 && (changed & StateSet.R_LINE) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.lineWidth(Math.clamp(glCtx.lineState.getWidth(), GLSMRasterizer.lineWidthMin, GLSMRasterizer.lineWidthMax));
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_POINT) != 0 && (changed & StateSet.R_POINT) != 0) {
            attribValueRestores++;
            GLSMRasterizer.applyPointState(glCtx);
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_HINT) != 0 && (changed & StateSet.R_HINT) != 0) {
            attribValueRestores++;
            GLSMRasterizer.applyHintState(glCtx);
            attribBackendCalls++;
        }
        if ((restore & StateSet.R_POLYGON) != 0 && (changed & StateSet.R_POLYGON) != 0) {
            attribValueRestores++;
            RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, glCtx.polygonState.getFrontMode());
            applyPolygonOffset(glCtx);
            RENDER_BACKEND.cullFace(glCtx.polygonState.getCullFaceMode());
            RENDER_BACKEND.frontFace(glCtx.polygonState.getFrontFace());
            attribBackendCalls += 4;
        }
        final boolean textureBitSet = (restore & StateSet.R_TEXTURE) != 0 && (changed & StateSet.R_TEXTURE) != 0;
        final boolean activeUnitBitSet = (restore & StateSet.R_ACTIVE_UNIT) != 0;
        if (textureBitSet || activeUnitBitSet) {
            boolean unitTouched = false;
            if (textureBitSet) {
                final long unitsChanged = glCtx.restoreUnitChangedMask;
                for (int i = 0; i < MAX_TEXTURE_UNITS; i++) {
                    if ((unitsChanged & (1L << Math.min(i, 63))) != 0) {
                        final TextureBinding restored = glCtx.textures.getTextureUnitBindings(i);
                        final int restoredTarget = restored.getTarget() != 0 ? restored.getTarget() : GL11.GL_TEXTURE_2D;
                        RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + i);
                        RENDER_BACKEND.bindTexture(restoredTarget, restored.getBinding());
                        attribValueRestores++;
                        attribBackendCalls += 2;
                        unitTouched = true;
                    }
                }
            }
            if (unitTouched || (activeUnitBitSet && (changed & StateSet.R_ACTIVE_UNIT) != 0)) {
                RENDER_BACKEND.activeTexture(GL13.GL_TEXTURE0 + glCtx.activeTextureUnit.getValue());
                attribValueRestores++;
                attribBackendCalls++;
            }
        }
    }

    /**
     * After popping GLSM stacks, apply restored state to the GL driver.
     */
    private static void applyRestoredState(StateSet set, boolean blendSnapshotted) {
        final GLContextState glCtx = ctx();
        final int restore = set.restore;
        final int changed = glCtx.restoreChangedMask;
        issueRestoredBackendState(glCtx, restore);

        final int bump = set.bump;
        final int depth = glCtx.attribDepth;
        if ((bump & StateSet.B_LIGHTING) != 0 && glCtx.lightingGeneration != glCtx.savedLightingGen[depth]) glCtx.lightingGeneration++;
        if ((bump & StateSet.B_FRAG_FOG) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++;
        if ((bump & StateSet.B_FRAG_COLORBUF) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++;
        if ((bump & StateSet.B_FRAG_TEXTURE) != 0 && glCtx.fragmentGeneration != glCtx.savedFragmentGen[depth]) glCtx.fragmentGeneration++;
        if ((bump & StateSet.B_TRANSFORM) != 0) {
            if (glCtx.mvGeneration != glCtx.savedMvGen[depth]) glCtx.mvGeneration++;
            if (glCtx.mvLinearGeneration != glCtx.savedMvLinearGen[depth]) glCtx.mvLinearGeneration++;
            if (glCtx.projGeneration != glCtx.savedProjGen[depth]) glCtx.projGeneration++;
            if (glCtx.texMatrixGeneration != glCtx.savedTexMatGen[depth]) glCtx.texMatrixGeneration++;
        }
        if ((bump & StateSet.B_CURRENT) != 0) {
            if (glCtx.colorGeneration != glCtx.savedColorGen[depth]) {
                glCtx.colorGeneration++;
                glCtx.dirtyColorAttrib = true;
            }
            if (glCtx.ffp.normalGeneration != glCtx.savedNormalGen[depth]) {
                glCtx.ffp.normalGeneration++;
                glCtx.dirtyNormalAttrib = true;
            }
            if (glCtx.ffp.texCoordGeneration != glCtx.savedTexCoordGen[depth]) {
                glCtx.ffp.texCoordGeneration++;
                glCtx.dirtyTexCoordAttrib = true;
            }
            if ((restore & StateSet.R_SECONDARY_COLOR) != 0 && (changed & StateSet.R_SECONDARY_COLOR) != 0) glCtx.fragmentGeneration++;
        }

        GLSMBlend.postVanillaBlendChangeIfMoved(blendSnapshotted);

        if ((restore & StateSet.R_PROGRAM) != 0 && (changed & StateSet.R_PROGRAM) != 0) {
            GLSMPrograms.glUseProgram(glCtx.programStack.getValue());
            attribValueRestores++;
            attribBackendCalls++;
        }
    }

    public static void glPushAttrib(int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPushAttrib(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.pushGroup("pushState");
        pushState(mask);
    }

    public static void glPopAttrib() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopAttrib();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (ctx().attribDepth == 0) {
            rejectExitCapture();
            warnOnce("popattrib-underflow", "glPopAttrib called with an empty attrib stack; ignoring");
            return;
        }
        GLDebug.popGroup();
        GLDebug.pushGroup("popState");
        try {
            popState();
        } finally {
            GLDebug.popGroup();
        }
    }

    public static boolean isPoppingAttributes() {
        return ctx().poppingAttributes;
    }

    public static int getAttribDepth() {
        return ctx().attribDepth;
    }
}
