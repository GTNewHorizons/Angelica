package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetShort;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Supplies {@code mc_midTexCoord}/{@code at_tangent} for mod geometry drawn through the FFPclient-array path
 * ({@code glVertexPointer}/{@code glTexCoordPointer} + {@code glDrawArrays}) out of a VBO, which bypasses the
 * Tessellator and display-list ext-injection paths.
 *
 * <p>Tangents are reconstructed from the flat face normal (handler's {@code normalOffset < 0} path).
 */
public final class FfpExtendedAttribs {

    private FfpExtendedAttribs() {}

    private static final int POSITION_LOC = Usage.POSITION.getAttributeLocation();
    private static final int UV_LOC = Usage.PRIMARY_UV.getAttributeLocation();
    private static final int NORMAL_LOC = Usage.NORMAL.getAttributeLocation();
    private static final int SRC_TEX_OFFSET = 12;
    private static final int SRC_NORMAL_OFFSET = 20;
    private static final int SRC_STRIDE_FLAT = 20;
    private static final int SRC_STRIDE_SMOOTH = 24;
    private static final int MAX_CACHE = 1024;
    private static final int MAX_EXT_VERTS = 1 << 20;
    private static final int MAX_RESPECS = 8;
    private static final int RESPEC_DECAY_FRAMES = 2;
    private static final int MIN_STREAM_SPAN_FRAMES = 4;
    private static final int STREAM_EXPIRE_FRAMES = 60;

    private static final HashMap<Key, ExtBuffer> cache = new HashMap<>();
    private static final Key lookup = new Key();
    private static int internalGlDepth = 0;

    private static final HashMap<EboKey, Boolean> eboPatternCache = new HashMap<>();
    private static final EboKey eboLookup = new EboKey();

    private static final Int2ObjectMap<ObjectSet<Key>> keysBySource = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<ObjectSet<EboKey>> eboKeysBySource = new Int2ObjectOpenHashMap<>();
    private static final Int2IntMap respecCounts = new Int2IntOpenHashMap();
    private static final Int2IntMap respecFrames = new Int2IntOpenHashMap();
    private static final Int2IntMap respecRunStart = new Int2IntOpenHashMap();
    private static final IntSet streamingSources = new IntOpenHashSet();
    private static final IntSet persistentSources = new IntOpenHashSet();
    private static int frame;

    public static void endFrame() { frame++; }

    public static boolean isEmpty() {
        return cache.isEmpty() && eboPatternCache.isEmpty();
    }

    private static int internalDrawDepth = 0;

    public static void beginInternalDraw() { internalDrawDepth++; }

    public static void endInternalDraw() { if (internalDrawDepth > 0) internalDrawDepth--; }

    private static void beginInternalGl() { internalGlDepth++; }

    private static void endInternalGl() { if (internalGlDepth > 0) internalGlDepth--; }

    private static boolean isNotConventionalTexturedArray(VAOManager.Attrib a) {
        return a == null || !a.enabled || a.genericPointer || a.vboId == 0 || a.size < 2;
    }

    private static boolean isStreaming(int vboId) {
        if (vboId == 0 || !streamingSources.contains(vboId)) return false;
        if (persistentSources.contains(vboId)) return true;
        if (frame - respecFrames.get(vboId) > STREAM_EXPIRE_FRAMES) {
            streamingSources.remove(vboId);
            respecFrames.remove(vboId);
            return false;
        }
        return true;
    }

    public static boolean maybeBind(int mode, int first, int count) {
        final ImmediateExtendedAttribHandler h = GLSMHooks.immediateExtendedHandler;
        if (h == null) return false;
        if (internalDrawDepth > 0) return false;
        final int extPrim = ImmediateExtendedAttribHandler.extPrimVerts(mode, count);
        if (extPrim == 0 || first < 0) return false;
        if (!h.wantsExtended()) return false;

        final VAOManager.Attrib pos = VAOManager.get(POSITION_LOC);
        final VAOManager.Attrib uv = VAOManager.get(UV_LOC);
        if (isNotConventionalTexturedArray(pos) || isNotConventionalTexturedArray(uv)) return false;
        if (isStreaming(pos.vboId) || isStreaming(uv.vboId)) return false;

        VAOManager.Attrib normal = extPrim == 3 ? VAOManager.get(NORMAL_LOC) : null;
        if (normal != null && (!normal.enabled || normal.genericPointer || normal.vboId == 0 || normal.size < 3
            || isStreaming(normal.vboId))) {
            normal = null;
        }

        return bindExt(h, mode, first, count, extPrim, pos, uv, normal);
    }

    public static boolean maybeBindIndexed(int mode, int indexCount, int indexType, long indicesOffset) {
        final ImmediateExtendedAttribHandler h = GLSMHooks.immediateExtendedHandler;
        if (h == null) return false;
        if (internalDrawDepth > 0) return false;
        if (mode != GL11.GL_TRIANGLES || indexCount <= 0 || indexCount % 6 != 0) return false;
        if (!h.wantsExtended()) return false;

        final VAOManager.Attrib pos = VAOManager.get(POSITION_LOC);
        final VAOManager.Attrib uv = VAOManager.get(UV_LOC);
        if (isNotConventionalTexturedArray(pos) || isNotConventionalTexturedArray(uv)) return false;
        if (isStreaming(pos.vboId) || isStreaming(uv.vboId) || isStreaming(VAOManager.boundEBO)) return false;

        final int indexSize = indexTypeBytes(indexType);
        if (indexSize == 0) return false;
        if (indicesOffset % (6L * indexSize) != 0) return false;
        final int first = (int) (indicesOffset / indexSize) / 6 * 4;
        final int count = indexCount / 6 * 4;

        if (!isQuadPatternEbo(VAOManager.boundEBO, indicesOffset, indexCount, indexType, indexSize, first)) return false;

        // Build/bind as GL_QUADS (the real primitive): face-normal tangents, 4-vertex tiles, no per-vertex normal.
        return bindExt(h, GL11.GL_QUADS, first, count, 4, pos, uv, null);
    }

    private static boolean isQuadPatternEbo(int ebo, long offset, int indexCount, int indexType, int indexSize, int firstVertex) {
        if (ebo == 0) return false;
        eboLookup.set(ebo, offset, indexCount, indexType);
        final Boolean cached = eboPatternCache.get(eboLookup);
        if (cached != null) return cached;
        if (eboPatternCache.size() >= MAX_CACHE) {
            eboPatternCache.clear();
            eboKeysBySource.clear();
        }

        final ByteBuffer buf = memCalloc(indexCount, indexSize);
        RENDER_BACKEND.getBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, offset, buf);
        final long addr = memAddress0(buf);
        boolean match = true;
        for (int q = 0, quadCount = indexCount / 6; q < quadCount; q++) {
            final int base = firstVertex + q * 4;
            final long p = addr + (long) q * 6 * indexSize;
            if (readIndex(p, 0, indexSize) != base
                || readIndex(p, 1, indexSize) != base + 1
                || readIndex(p, 2, indexSize) != base + 2
                || readIndex(p, 3, indexSize) != base + 2
                || readIndex(p, 4, indexSize) != base + 3
                || readIndex(p, 5, indexSize) != base) {
                match = false;
                break;
            }
        }
        memFree(buf);
        final EboKey stored = eboLookup.copy();
        eboPatternCache.put(stored, match);
        ObjectSet<EboKey> ebos = eboKeysBySource.get(stored.ebo);
        if (ebos == null) eboKeysBySource.put(stored.ebo, ebos = new ObjectOpenHashSet<>());
        ebos.add(stored);
        return match;
    }

    private static long readIndex(long base, int i, int indexSize) {
        return switch (indexSize) {
            case 1 -> memGetByte(base + i) & 0xFFL;
            case 2 -> memGetShort(base + (long) i * 2) & 0xFFFFL;
            default -> memGetInt(base + (long) i * 4) & 0xFFFFFFFFL;
        };
    }

    private static int indexTypeBytes(int indexType) {
        return switch (indexType) {
            case GL11.GL_UNSIGNED_BYTE -> 1;
            case GL11.GL_UNSIGNED_SHORT -> 2;
            case GL11.GL_UNSIGNED_INT -> 4;
            default -> 0;
        };
    }

    private static boolean bindExt(ImmediateExtendedAttribHandler h, int mode, int first, int count, int extPrim,
                                   VAOManager.Attrib pos, VAOManager.Attrib uv, VAOManager.Attrib normal) {
        final int extVbo = getOrBuild(h, pos, uv, normal, first, count, extPrim, mode);
        if (extVbo == 0) return false;

        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
        ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, ImmediateExtendedAttribHandler.EXT_STRIDE);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
        return true;
    }

    public static void unbind() {
        GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_MID_TEX);
        GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_TANGENT);
        setNeutralCurrentValues();
    }

    public static void setNeutralCurrentValues() {
        GLStateManager.glVertexAttrib2f(ImmediateExtendedAttribHandler.LOC_MID_TEX, 0.5f, 0.5f);
        GLStateManager.glVertexAttrib4f(ImmediateExtendedAttribHandler.LOC_TANGENT, 1.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void onDeleteBuffer(int vboId) {
        if (internalGlDepth > 0 || vboId == 0) return;
        dropDerivedFrom(vboId);
        respecCounts.remove(vboId);
        respecFrames.remove(vboId);
        respecRunStart.remove(vboId);
        streamingSources.remove(vboId);
        persistentSources.remove(vboId);
    }

    public static void onBufferRespecified(int vboId) {
        if (internalGlDepth > 0 || vboId == 0) return;
        if (streamingSources.contains(vboId)) {
            respecFrames.put(vboId, frame);
            return;
        }
        if (!dropDerivedFrom(vboId)) return;
        final boolean sameRun = respecCounts.containsKey(vboId) && frame - respecFrames.get(vboId) <= RESPEC_DECAY_FRAMES;
        if (!sameRun) {
            startRespecRun(vboId);
            return;
        }
        final int respecs = respecCounts.get(vboId) + 1;
        final int span = frame - respecRunStart.get(vboId);
        if (respecs >= MAX_RESPECS && span >= MIN_STREAM_SPAN_FRAMES && span <= respecs) {
            markStreaming(vboId);
            return;
        }
        if (span > respecs) {
            startRespecRun(vboId);
            return;
        }
        respecCounts.put(vboId, respecs);
        respecFrames.put(vboId, frame);
    }

    private static void startRespecRun(int vboId) {
        respecCounts.put(vboId, 1);
        respecRunStart.put(vboId, frame);
        respecFrames.put(vboId, frame);
    }

    public static void markStreaming(int vboId) {
        if (internalGlDepth > 0 || vboId == 0) return;
        respecCounts.remove(vboId);
        respecRunStart.remove(vboId);
        respecFrames.put(vboId, frame);
        if (streamingSources.add(vboId)) dropDerivedFrom(vboId);
    }

    public static void markPersistent(int vboId) {
        if (internalGlDepth > 0 || vboId == 0) return;
        markStreaming(vboId);
        persistentSources.add(vboId);
    }

    public static void reset() {
        invalidateAll();
        respecCounts.clear();
        respecFrames.clear();
        respecRunStart.clear();
        streamingSources.clear();
        persistentSources.clear();
    }

    private static boolean dropDerivedFrom(int srcId) {
        final ObjectSet<EboKey> ebos = eboKeysBySource.remove(srcId);
        if (ebos != null) {
            for (EboKey k : ebos) eboPatternCache.remove(k);
        }
        final ObjectSet<Key> keys = keysBySource.get(srcId);
        if (keys == null || keys.isEmpty()) return ebos != null;
        IntArrayList toFree = null;
        for (Key k : keys.toArray(new Key[0])) {
            final ExtBuffer buf = cache.remove(k);
            if (buf != null && buf.vbo != 0) {
                if (toFree == null) toFree = new IntArrayList();
                toFree.add(buf.vbo);
            }
            unindexKey(k);
        }
        freeExtVbos(toFree);
        return true;
    }

    private static void indexKey(Key k) {
        addToBucket(k.posVBO, k);
        if (k.uvVBO != k.posVBO) addToBucket(k.uvVBO, k);
        if (k.normalVBO != k.posVBO && k.normalVBO != k.uvVBO) addToBucket(k.normalVBO, k);
    }

    private static void unindexKey(Key k) {
        removeFromBucket(k.posVBO, k);
        if (k.uvVBO != k.posVBO) removeFromBucket(k.uvVBO, k);
        if (k.normalVBO != k.posVBO && k.normalVBO != k.uvVBO) removeFromBucket(k.normalVBO, k);
    }

    private static void addToBucket(int src, Key k) {
        if (src == 0) return;
        ObjectSet<Key> set = keysBySource.get(src);
        if (set == null) keysBySource.put(src, set = new ObjectOpenHashSet<>());
        set.add(k);
    }

    private static void removeFromBucket(int src, Key k) {
        if (src == 0) return;
        final ObjectSet<Key> set = keysBySource.get(src);
        if (set != null && set.remove(k) && set.isEmpty()) keysBySource.remove(src);
    }

    private static int getOrBuild(ImmediateExtendedAttribHandler h, VAOManager.Attrib pos, VAOManager.Attrib uv,
                                  VAOManager.Attrib normal, int first, int count, int extPrim, int mode) {
        if (first + count > MAX_EXT_VERTS) return 0;
        lookup.set(pos, uv, normal, mode, first % extPrim);
        ExtBuffer buf = cache.get(lookup);
        if (buf == null) {
            if (cache.size() >= MAX_CACHE) invalidateExtBuffers();
            buf = new ExtBuffer();
            final Key stored = lookup.copy();
            cache.put(stored, buf);
            indexKey(stored);
        }

        final long range = (long) first << 32 | (count & 0xFFFFFFFFL);
        if (buf.vbo != 0 && buf.builtRanges.contains(range)) return buf.vbo;
        if (!ensureCapacity(buf, first + count)) return 0;
        buildRange(h, buf.vbo, pos, uv, normal, first, count, extPrim);
        buf.builtRanges.add(range);
        return buf.vbo;
    }

    private static boolean ensureCapacity(ExtBuffer buf, int neededVerts) {
        if (buf.vbo != 0 && buf.capacityVerts >= neededVerts) return true;
        final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
        final int newCap = Math.min(MAX_EXT_VERTS, Math.max(neededVerts, buf.capacityVerts * 2));
        beginInternalGl();
        try {
            final int vbo = GLStateManager.glGenBuffers();
            if (vbo == 0) return false;
            final int savedVBO = GLStateManager.getBoundVBO();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, (long) newCap * extStride, GL15.GL_STATIC_DRAW);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
            if (buf.vbo != 0) {
                final int savedRead = GLStateManager.getBoundCopyReadBuffer();
                final int savedWrite = GLStateManager.getBoundCopyWriteBuffer();
                GLStateManager.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buf.vbo);
                GLStateManager.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, vbo);
                GLStateManager.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0L, 0L,
                    (long) buf.capacityVerts * extStride);
                GLStateManager.glBindBuffer(GL31.GL_COPY_READ_BUFFER, savedRead);
                GLStateManager.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, savedWrite);
                GLStateManager.glDeleteBuffers(buf.vbo);
            }
            buf.vbo = vbo;
            buf.capacityVerts = newCap;
            return true;
        } finally {
            endInternalGl();
        }
    }

    private static void buildRange(ImmediateExtendedAttribHandler h, int extVbo, VAOManager.Attrib pos,
                                   VAOManager.Attrib uv, VAOManager.Attrib normal, int first, int count, int extPrim) {
        final int posStride = pos.effectiveStride();
        final int uvStride = uv.effectiveStride();
        final boolean smooth = normal != null;
        final int normalStride = smooth ? normal.effectiveStride() : 0;
        final int srcStride = smooth ? SRC_STRIDE_SMOOTH : SRC_STRIDE_FLAT;
        final int srcNormalOffset = smooth ? SRC_NORMAL_OFFSET : -1;

        final ByteBuffer posBuf = readRange(pos, first, count, posStride);
        final ByteBuffer uvBuf = readRange(uv, first, count, uvStride);
        final ByteBuffer normalBuf = smooth ? readRange(normal, first, count, normalStride) : null;

        final ByteBuffer src = memAlloc(count * srcStride).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            final int pb = i * posStride, ub = i * uvStride, d = i * srcStride;
            src.putFloat(d, pos.readComponent(posBuf, pb, 0));
            src.putFloat(d + 4, pos.readComponent(posBuf, pb, 1));
            src.putFloat(d + 8, pos.size >= 3 ? pos.readComponent(posBuf, pb, 2) : 0f);
            src.putFloat(d + SRC_TEX_OFFSET, uv.readComponent(uvBuf, ub, 0));
            src.putFloat(d + SRC_TEX_OFFSET + 4, uv.readComponent(uvBuf, ub, 1));
            if (smooth) {
                final int nb = i * normalStride;
                src.putInt(d + SRC_NORMAL_OFFSET, NormI8.pack(
                    normal.readComponent(normalBuf, nb, 0),
                    normal.readComponent(normalBuf, nb, 1),
                    normal.readComponent(normalBuf, nb, 2)));
            }
        }

        final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
        final ByteBuffer ext = memCalloc(count, extStride);
        h.buildPacked(memAddress0(src), srcStride, 0, SRC_TEX_OFFSET, srcNormalOffset, count, extPrim, memAddress0(ext), extStride);

        beginInternalGl();
        try {
            final int savedVBO = GLStateManager.getBoundVBO();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
            ext.position(0).limit(count * extStride);
            GLStateManager.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) first * extStride, ext);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
        } finally {
            endInternalGl();
        }

        memFree(posBuf);
        memFree(uvBuf);
        if (normalBuf != null) memFree(normalBuf);
        memFree(src);
        memFree(ext);
    }

    private static ByteBuffer readRange(VAOManager.Attrib a, int first, int count, int stride) {
        final int elementBytes = a.size * a.typeSizeBytes();
        final int bytes = (count - 1) * stride + elementBytes;
        final ByteBuffer dst = memAlloc(bytes).order(ByteOrder.nativeOrder());
        dst.position(0).limit(bytes);
        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, a.vboId);
        RENDER_BACKEND.getBufferSubData(GL15.GL_ARRAY_BUFFER, a.offset + (long) first * stride, dst);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
        dst.position(0).limit(bytes);
        return dst;
    }

    private static void invalidateAll() {
        eboPatternCache.clear();
        eboKeysBySource.clear();
        invalidateExtBuffers();
    }

    private static void invalidateExtBuffers() {
        if (cache.isEmpty()) return;
        final IntArrayList toFree = new IntArrayList(cache.size());
        for (ExtBuffer buf : cache.values()) {
            if (buf.vbo != 0) toFree.add(buf.vbo);
        }
        cache.clear();
        keysBySource.clear();
        freeExtVbos(toFree);
    }

    private static void freeExtVbos(IntArrayList vbos) {
        if (vbos == null || vbos.isEmpty()) return;
        beginInternalGl();
        try {
            for (int i = 0; i < vbos.size(); i++) {
                GLStateManager.glDeleteBuffers(vbos.getInt(i));
            }
        } finally {
            endInternalGl();
        }
    }

    private static final class ExtBuffer {
        int vbo;
        int capacityVerts;
        final LongOpenHashSet builtRanges = new LongOpenHashSet();
    }

    private static final class EboKey {
        int ebo, count, type;
        long offset;

        void set(int ebo, long offset, int count, int type) {
            this.ebo = ebo;
            this.offset = offset;
            this.count = count;
            this.type = type;
        }

        EboKey copy() {
            final EboKey k = new EboKey();
            k.set(ebo, offset, count, type);
            return k;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EboKey k)) return false;
            return ebo == k.ebo && count == k.count && type == k.type && offset == k.offset;
        }

        @Override
        public int hashCode() {
            int h = ebo;
            h = h * 31 + count;
            h = h * 31 + type;
            h = h * 31 + Long.hashCode(offset);
            return h;
        }
    }

    private static final class Key {
        int posVBO, uvVBO, normalVBO, posStride, uvStride, normalStride, posFmt, uvFmt, normalFmt, mode, phase;
        long posOffset, uvOffset, normalOffset;

        void set(VAOManager.Attrib pos, VAOManager.Attrib uv, VAOManager.Attrib normal, int mode, int phase) {
            posVBO = pos.vboId; posOffset = pos.offset; posStride = pos.effectiveStride(); posFmt = fmt(pos);
            uvVBO = uv.vboId; uvOffset = uv.offset; uvStride = uv.effectiveStride(); uvFmt = fmt(uv);
            if (normal != null) {
                normalVBO = normal.vboId; normalOffset = normal.offset;
                normalStride = normal.effectiveStride(); normalFmt = fmt(normal);
            } else {
                normalVBO = 0; normalOffset = 0; normalStride = 0; normalFmt = 0;
            }
            this.mode = mode;
            this.phase = phase;
        }

        private static int fmt(VAOManager.Attrib a) {
            return (a.size & 0x7F) | (a.normalized ? 0x80 : 0) | (a.type << 8);
        }

        Key copy() {
            final Key k = new Key();
            k.posVBO = posVBO; k.posOffset = posOffset; k.posStride = posStride; k.posFmt = posFmt;
            k.uvVBO = uvVBO; k.uvOffset = uvOffset; k.uvStride = uvStride; k.uvFmt = uvFmt;
            k.normalVBO = normalVBO; k.normalOffset = normalOffset; k.normalStride = normalStride; k.normalFmt = normalFmt;
            k.mode = mode; k.phase = phase;
            return k;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return posVBO == k.posVBO && uvVBO == k.uvVBO && normalVBO == k.normalVBO
                && posStride == k.posStride && uvStride == k.uvStride && normalStride == k.normalStride
                && posFmt == k.posFmt && uvFmt == k.uvFmt && normalFmt == k.normalFmt
                && mode == k.mode && phase == k.phase
                && posOffset == k.posOffset && uvOffset == k.uvOffset && normalOffset == k.normalOffset;
        }

        @Override
        public int hashCode() {
            int h = posVBO;
            h = h * 31 + uvVBO;
            h = h * 31 + normalVBO;
            h = h * 31 + posStride;
            h = h * 31 + uvStride;
            h = h * 31 + normalStride;
            h = h * 31 + posFmt;
            h = h * 31 + uvFmt;
            h = h * 31 + normalFmt;
            h = h * 31 + mode;
            h = h * 31 + phase;
            h = h * 31 + Long.hashCode(posOffset);
            h = h * 31 + Long.hashCode(uvOffset);
            h = h * 31 + Long.hashCode(normalOffset);
            return h;
        }
    }
}
