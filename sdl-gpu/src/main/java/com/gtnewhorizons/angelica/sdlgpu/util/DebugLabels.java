package com.gtnewhorizons.angelica.sdlgpu.util;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;

public final class DebugLabels {
    private final FrameManager frameManager;
    private final boolean enabled;
    private Int2ObjectOpenHashMap<String> programLabels;
    private Int2ObjectOpenHashMap<ByteBuffer> programLabelEncoded;

    public DebugLabels(FrameManager frameManager, boolean enabled) {
        this.frameManager = frameManager;
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }

    public void objectLabel(int identifier, int name, CharSequence label) {
        if (!enabled || label == null) return;
        if (identifier == KHRDebug.GL_PROGRAM) {
            if (programLabels == null) programLabels = new Int2ObjectOpenHashMap<>();
            programLabels.put(name, label.toString());
            if (programLabelEncoded != null) {
                final ByteBuffer prev = programLabelEncoded.remove(name);
                if (prev != null) MemoryUtil.memFree(prev);
            }
        }
    }

    public void pushDebugGroup(CharSequence message) {
        if (!enabled || message == null) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;
        SDL_PushGPUDebugGroup(cb, message);
    }

    public void popDebugGroup() {
        if (!enabled) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;
        SDL_PopGPUDebugGroup(cb);
    }

    public void debugMessageInsert(CharSequence message) {
        if (!enabled || message == null) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;
        SDL_InsertGPUDebugLabel(cb, message);
    }

    public void onProgramDeleted(int program) {
        if (programLabelEncoded == null) return;
        final ByteBuffer enc = programLabelEncoded.remove(program);
        if (enc != null) MemoryUtil.memFree(enc);
    }

    public void updateAutoDebugGroup(ContextState st, int newProgram) {
        if (!enabled) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;
        if (st.autoPushedProgram != 0) {
            SDL_PopGPUDebugGroup(cb);
            st.autoPushedProgram = 0;
        }
        if (newProgram != 0) {
            final ByteBuffer encoded = encodedProgramLabel(newProgram);
            nSDL_PushGPUDebugGroup(cb, MemoryUtil.memAddress(encoded));
            st.autoPushedProgram = newProgram;
        }
    }

    public void popAutoDebugGroup(ContextState st) {
        if (st.autoPushedProgram == 0) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) {
            st.autoPushedProgram = 0;
            return;
        }
        SDL_PopGPUDebugGroup(cb);
        st.autoPushedProgram = 0;
    }

    private ByteBuffer encodedProgramLabel(int program) {
        if (programLabelEncoded == null) programLabelEncoded = new Int2ObjectOpenHashMap<>();
        ByteBuffer cached = programLabelEncoded.get(program);
        if (cached != null) return cached;
        final String label = programLabels == null ? null : programLabels.get(program);
        final String groupName = label != null ? label : ("program " + program);
        cached = MemoryUtil.memUTF8(groupName, true);
        programLabelEncoded.put(program, cached);
        return cached;
    }
}
