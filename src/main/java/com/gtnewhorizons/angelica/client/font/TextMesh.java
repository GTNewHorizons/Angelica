package com.gtnewhorizons.angelica.client.font;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

public final class TextMesh {

    private static final int INITIAL_COMMANDS = 8;

    GlyphSettings settings;
    ByteBuffer vertices;
    long address;
    int quadCount;
    int[] commandStart = new int[INITIAL_COMMANDS];
    int[] commandCount = new int[INITIAL_COMMANDS];
    int[] commandTexture = new int[INITIAL_COMMANDS];
    int[] commandLayer = new int[INITIAL_COMMANDS];
    boolean[] commandUnicode = new boolean[INITIAL_COMMANDS];
    int commands;
    boolean captured;
    boolean animated;
    int regionStart = -1;
    int regionQuads;
    int regionGeneration;
    long lastUsedNanos;

    public boolean isValid(GlyphSettings live) {
        return captured && !BatchingFontRenderer.recolorActive() && live.equals(settings);
    }

    public boolean isAnimated() {
        return animated;
    }

    void reserveVertexBytes(int bytes) {
        if (vertices == null) {
            vertices = memAlloc(Math.max(bytes, 1));
        } else if (vertices.capacity() < bytes) {
            vertices = memRealloc(vertices, Math.max(bytes, vertices.capacity() * 2));
        } else {
            return;
        }
        address = memAddress0(vertices);
    }

    public void free() {
        RetainedTextStore.release(this);
        memFree(vertices);
        vertices = null;
        address = 0L;
        captured = false;
    }

    void addCommand(int start, int count, int texture, boolean unicode, int layer) {
        if (commands == commandStart.length) {
            final int n = commands * 2;
            commandStart = Arrays.copyOf(commandStart, n);
            commandCount = Arrays.copyOf(commandCount, n);
            commandTexture = Arrays.copyOf(commandTexture, n);
            commandLayer = Arrays.copyOf(commandLayer, n);
            commandUnicode = Arrays.copyOf(commandUnicode, n);
        }
        commandStart[commands] = start;
        commandCount[commands] = count;
        commandTexture[commands] = texture;
        commandLayer[commands] = layer;
        commandUnicode[commands] = unicode;
        commands++;
    }
}
