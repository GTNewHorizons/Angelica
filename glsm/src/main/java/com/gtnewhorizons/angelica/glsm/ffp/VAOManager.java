package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLTypes;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class VAOManager {
    public static final int MAX_ATTRIBS = 16;

    public final Int2ObjectOpenHashMap<VAOData> vaoMap = new Int2ObjectOpenHashMap<>();

    final Int2ObjectOpenHashMap<VAOData> eboOwners = new Int2ObjectOpenHashMap<>();

    private VAOData current = null;
    public static final int CONV_POSITION = 0;
    public static final int CONV_NORMAL = 1;
    public static final int CONV_COLOR = 2;
    public static final int CONV_SECONDARY_COLOR = 3;
    public static final int CONV_FOG_COORD = 4;
    public static final int CONV_INDEX = 5;
    public static final int CONV_EDGE_FLAG = 6;
    public static final int CONV_TEX0 = 7;
    public static final int TEX_COORD_UNITS = 8;
    public static final int CONV_COUNT = CONV_TEX0 + TEX_COORD_UNITS;

    private static final int[] CONV_LOCATION = new int[CONV_COUNT];
    private static final int[] LOCATION_CONV = new int[MAX_ATTRIBS];
    static {
        Arrays.fill(CONV_LOCATION, -1);
        Arrays.fill(LOCATION_CONV, -1);
        mapConventional(CONV_POSITION, Usage.POSITION);
        mapConventional(CONV_NORMAL, Usage.NORMAL);
        mapConventional(CONV_COLOR, Usage.COLOR);
        mapConventional(CONV_TEX0, Usage.PRIMARY_UV);
        mapConventional(CONV_TEX0 + 1, Usage.SECONDARY_UV);
    }

    private static void mapConventional(int conv, Usage usage) {
        final int location = usage.getAttributeLocation();
        CONV_LOCATION[conv] = location;
        LOCATION_CONV[location] = conv;
    }

    public static int conventionalLocation(int conv) {
        return CONV_LOCATION[conv];
    }

    private static final int T_BYTE = 1, T_UBYTE = 1 << 1, T_SHORT = 1 << 2, T_USHORT = 1 << 3, T_INT = 1 << 4, T_UINT = 1 << 5, T_FLOAT = 1 << 6;
    private static final int T_DOUBLE = 1 << 10, T_HALF = 1 << 11, T_FIXED = 1 << 12, T_PACKED = 1 << 13, T_PACKED_FLOAT = 1 << 14;
    private static final int T_INTEGER = T_BYTE | T_UBYTE | T_SHORT | T_USHORT | T_INT | T_UINT;
    private static final int T_COLOR = T_INTEGER | T_HALF | T_FLOAT | T_DOUBLE | T_PACKED;
    private static final int T_GENERIC = T_COLOR | T_FIXED | T_PACKED_FLOAT;
    private static final int[] LEGAL_TYPES = new int[CONV_COUNT];
    private static final int[] MIN_SIZE = new int[CONV_COUNT];
    private static final int[] MAX_SIZE = new int[CONV_COUNT];
    static {
        legal(CONV_POSITION, T_SHORT | T_INT | T_FLOAT | T_DOUBLE | T_HALF | T_PACKED, 2, 4);
        legal(CONV_NORMAL, T_BYTE | T_SHORT | T_INT | T_HALF | T_FLOAT | T_DOUBLE | T_PACKED, 3, 3);
        legal(CONV_COLOR, T_COLOR, 3, 4);
        legal(CONV_SECONDARY_COLOR, T_COLOR, 3, 4);
        legal(CONV_FOG_COORD, T_HALF | T_FLOAT | T_DOUBLE, 1, 1);
        legal(CONV_INDEX, T_UBYTE | T_SHORT | T_INT | T_FLOAT | T_DOUBLE, 1, 1);
        legal(CONV_EDGE_FLAG, T_UBYTE, 1, 1);
        for (int unit = 0; unit < TEX_COORD_UNITS; unit++) {
            legal(CONV_TEX0 + unit, T_SHORT | T_INT | T_HALF | T_FLOAT | T_DOUBLE | T_PACKED, 1, 4);
        }
    }

    private static void legal(int conv, int types, int minSize, int maxSize) {
        LEGAL_TYPES[conv] = types;
        MIN_SIZE[conv] = minSize;
        MAX_SIZE[conv] = maxSize;
    }

    private static int typeBit(int type) {
        if (type >= GL11.GL_BYTE && type <= GL30.GL_HALF_FLOAT) return 1 << type - GL11.GL_BYTE;
        return switch (type) {
            case GL41.GL_FIXED -> T_FIXED;
            case GL12.GL_UNSIGNED_INT_2_10_10_10_REV, GL33.GL_INT_2_10_10_10_REV -> T_PACKED;
            case GL30.GL_UNSIGNED_INT_10F_11F_11F_REV -> T_PACKED_FLOAT;
            default -> 0;
        };
    }

    private static boolean isLegal(int types, int minSize, int maxSize, boolean bgra, int size, int type) {
        final int bit = typeBit(type);
        if ((types & bit) == 0) return false;
        if (size == GL12.GL_BGRA) return bgra && (bit & (T_UBYTE | T_PACKED)) != 0;
        return size >= minSize && size <= maxSize;
    }

    public static boolean isLegalConventional(int conv, int size, int type) {
        return isLegal(LEGAL_TYPES[conv], MIN_SIZE[conv], MAX_SIZE[conv], conv == CONV_COLOR || conv == CONV_SECONDARY_COLOR, size, type);
    }

    public static boolean isLegalGeneric(int index, int size, int type, boolean integer) {
        return index >= 0 && index < MAX_ATTRIBS && isLegal(integer ? T_INTEGER : T_GENERIC, 1, 4, !integer, size, type);
    }

    private int currentVertexFlags = 0;
    private int genericEnabled = 0;
    private int conventionalEnabled = 0;
    private Attrib[] currentAttribs = null;
    private Attrib[] currentGeneric = null;
    private Attrib[] currentConventional = null;
    public int boundEBO = 0;

    private int clientSideEnabledCount = 0;
    private int arrayVersion = 0;
    private int versionClock = 0;
    private static final int DELETED_NAME_PRUNE_THRESHOLD = 4096;
    private static final int DEAD_EPOCH = -1;
    private final int pruneThreshold;
    private int bufferEpoch = 0;
    private final Int2IntOpenHashMap bufferDeletedAt = new Int2IntOpenHashMap();

    public VAOManager() {
        this(DELETED_NAME_PRUNE_THRESHOLD);
    }

    VAOManager(int pruneThreshold) {
        this.pruneThreshold = pruneThreshold;
    }

    private void touch() {
        arrayVersion = ++versionClock;
    }

    private boolean isBufferLive(int buffer, int sinceEpoch) {
        return buffer == 0 || bufferDeletedAt.get(buffer) <= sinceEpoch;
    }

    private int retiredEpoch(int buffer, int epoch, boolean rebase) {
        if (rebase) return epoch == DEAD_EPOCH ? DEAD_EPOCH : 0;
        return isBufferLive(buffer, epoch) ? epoch : DEAD_EPOCH;
    }

    private void retire(@Nullable Attrib[] slots, boolean rebase) {
        if (slots == null) return;
        for (final Attrib a : slots) {
            if (a != null) a.epoch = retiredEpoch(a.vboId, a.epoch, rebase);
        }
    }

    private void retireAll(ClientArrays[] savedStack, boolean rebase) {
        for (final VAOData data : vaoMap.values()) {
            retire(data.attribs, rebase);
            retire(data.generic, rebase);
            retire(data.conventional, rebase);
        }
        retire(currentAttribs, rebase);
        retire(currentGeneric, rebase);
        retire(currentConventional, rebase);
        for (final ClientArrays saved : savedStack) {
            if (saved == null) continue;
            saved.vboEpoch = retiredEpoch(saved.boundVBO, saved.vboEpoch, rebase);
            saved.eboEpoch = retiredEpoch(saved.boundEBO, saved.eboEpoch, rebase);
            retire(saved.generic, rebase);
            retire(saved.conventional, rebase);
        }
    }

    private void pruneDeletedNames(ClientArrays[] savedStack) {
        retireAll(savedStack, false);
        bufferDeletedAt.clear();
        retireAll(savedStack, true);
        bufferEpoch = 0;
    }

    public boolean isBufferLive(Attrib a) {
        return isBufferLive(a.vboId, a.epoch);
    }

    public void init(int defaultVAO) {
        final VAOData data = new VAOData();
        current = data;
        currentAttribs = data.attribs;
        vaoMap.put(defaultVAO, data);
    }

    public boolean isInitialized() {
        return current != null;
    }

    public void onBindEBO(int ebo) {
        boundEBO = ebo;
    }

    public void onBindVertexArrayPre(int vaoId) {
        // Save old VAO data
        if (current != null) {
            current.vertexFlags = currentVertexFlags;
            current.genericEnabled = genericEnabled;
            current.conventionalEnabled = conventionalEnabled;
            current.generic = currentGeneric;
            current.conventional = currentConventional;
            if (current.ebo != boundEBO) {
                removeEboOwner(current.ebo, current);
                current.ebo = boundEBO;
                addEboOwner(current.ebo, current);
            }
            current.attribs = currentAttribs;
            current.clientSideEnabledCount = clientSideEnabledCount;
            current.arrayVersion = arrayVersion;
        }

        VAOData data = vaoMap.get(vaoId);
        if (data == null) {
            data = new VAOData();
            vaoMap.put(vaoId, data);
        }
        current = data;
        currentVertexFlags = data.vertexFlags;
        genericEnabled = data.genericEnabled;
        conventionalEnabled = data.conventionalEnabled;
        currentAttribs = data.attribs;
        currentGeneric = data.generic;
        currentConventional = data.conventional;
        boundEBO = data.ebo;
        clientSideEnabledCount = data.clientSideEnabledCount;
        arrayVersion = data.arrayVersion;
    }

    public void onDeleteVertexArray(int vaoId) {
        final VAOData data = vaoMap.remove(vaoId);
        if (data == null) return;
        removeEboOwner(data.ebo, data);
        if (data == current) current = null;
    }

    private static boolean detach(Attrib[] slots, int buffer) {
        boolean detached = false;
        for (final Attrib a : slots) {
            if (a != null && a.vboId == buffer) {
                a.vboId = 0;
                detached = true;
            }
        }
        return detached;
    }

    public void onDeleteBuffer(int buffer, ClientArrays[] savedStack) {
        if (buffer == 0) return;
        bufferDeletedAt.put(buffer, ++bufferEpoch);
        if (bufferEpoch >= pruneThreshold) pruneDeletedNames(savedStack);
        if (currentAttribs != null) {
            boolean detached = detach(currentGeneric != null ? currentGeneric : currentAttribs, buffer);
            if (currentConventional != null) detached |= detach(currentConventional, buffer);
            if (detached) touch();
        }
        VAOData owner = eboOwners.remove(buffer);
        while (owner != null) {
            final VAOData next = owner.nextEboOwner;
            owner.nextEboOwner = null;
            owner.ebo = 0;
            owner = next;
        }
    }

    private void addEboOwner(int ebo, VAOData data) {
        if (ebo == 0) return;
        data.nextEboOwner = eboOwners.put(ebo, data);
    }

    private void removeEboOwner(int ebo, VAOData data) {
        if (ebo == 0) return;
        VAOData node = eboOwners.get(ebo);
        if (node == data) {
            final VAOData next = data.nextEboOwner;
            if (next == null) eboOwners.remove(ebo); else eboOwners.put(ebo, next);
            data.nextEboOwner = null;
            return;
        }
        while (node != null) {
            final VAOData next = node.nextEboOwner;
            if (next == data) {
                node.nextEboOwner = data.nextEboOwner;
                data.nextEboOwner = null;
                return;
            }
            node = next;
        }
    }

    public void enableClientVertexFlag(int flag) { currentVertexFlags |= flag; }
    public void disableClientVertexFlag(int flag) { currentVertexFlags &= ~flag; }
    public int getVertexFlags() { return currentVertexFlags; }
    public boolean isConventionalEnabled(int conv) { return (conventionalEnabled >> conv & 1) != 0; }
    public static void setCurrentVertexFlags(int flags) { GLStateManager.ctx().vaos.currentVertexFlags = flags; }

    public static final class VAOData {
        public Attrib[] attribs;
        public int vertexFlags;
        public int genericEnabled;
        public int conventionalEnabled;
        public Attrib[] generic;
        public Attrib[] conventional;
        public int ebo;
        public int clientSideEnabledCount;
        int arrayVersion;
        private VAOData nextEboOwner;

        public VAOData() {
            attribs = new Attrib[MAX_ATTRIBS];
        }
    }

    public boolean setAttribute(int index, int size, int type, boolean normalized, int stride, long offset, int vboId) {
        return setGeneric(index, size, type, normalized, false, stride, offset, vboId, null);
    }

    public boolean setAttribute(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        return setGeneric(index, size, type, normalized, false, stride, 0, 0, pointer);
    }

    public boolean setIntegerAttribute(int index, int size, int type, int stride, long offset, int vboId) {
        return setGeneric(index, size, type, false, true, stride, offset, vboId, null);
    }

    public boolean setIntegerAttribute(int index, int size, int type, int stride, ByteBuffer pointer) {
        return setGeneric(index, size, type, false, true, stride, 0, 0, pointer);
    }

    public boolean setConventional(int conv, int size, int type, boolean normalized, int stride, long offset, int vboId) {
        return setConventional(conv, size, type, normalized, stride, offset, vboId, null);
    }

    public boolean setConventional(int conv, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        return setConventional(conv, size, type, normalized, stride, 0, 0, pointer);
    }

    private boolean setGeneric(int index, int size, int type, boolean normalized, boolean integer, int stride, long offset, int vboId, ByteBuffer pointer) {
        if (index < 0 || index >= MAX_ATTRIBS) return false;
        final Attrib g = genericSlot(index);
        store(g, size, type, normalized, integer, stride, offset, vboId, pointer);
        if (currentAttribs[index] == g) return true;
        if (!isGenericEnabled(index) && isLocationConventionalEnabled(index)) return false;
        apply(index, g);
        return true;
    }

    private boolean setConventional(int conv, int size, int type, boolean normalized, int stride, long offset, int vboId, ByteBuffer pointer) {
        if (conv < 0 || conv >= CONV_COUNT) return false;
        final Attrib c = conventionalSlot(conv);
        store(c, size, type, normalized, false, stride, offset, vboId, pointer);
        final int index = CONV_LOCATION[conv];
        if (index < 0) return false;
        if (currentAttribs[index] == c) return true;
        if (isGenericEnabled(index)) return false;
        apply(index, c);
        return true;
    }

    private void store(Attrib a, int size, int type, boolean normalized, boolean integer, int stride, long offset, int vboId, ByteBuffer pointer) {
        final boolean wasClient = a.isClientSide();
        a.size = size;
        a.type = type;
        a.normalized = normalized;
        a.integer = integer;
        a.stride = stride;
        a.offset = offset;
        a.vboId = vboId;
        a.epoch = bufferEpoch;
        a.clientPointer = pointer;
        if (wasClient != a.isClientSide()) clientSideEnabledCount += wasClient ? -1 : 1;
        touch();
    }

    private void apply(int index, Attrib source) {
        final Attrib old = currentAttribs[index];
        if (old != null) {
            if (old.isClientSide()) clientSideEnabledCount--;
            source.enabled = old.enabled;
            old.enabled = false;
            if (source.isClientSide()) clientSideEnabledCount++;
        }
        currentAttribs[index] = source;
    }

    private boolean isGenericEnabled(int index) {
        return (genericEnabled >> index & 1) != 0;
    }

    private boolean isLocationConventionalEnabled(int index) {
        final int conv = LOCATION_CONV[index];
        return conv >= 0 && isConventionalEnabled(conv);
    }

    private Attrib generic(int index) {
        return (currentGeneric != null ? currentGeneric : currentAttribs)[index];
    }

    private Attrib genericSlot(int index) {
        final Attrib[] slots = currentGeneric != null ? currentGeneric : currentAttribs;
        Attrib g = slots[index];
        if (g == null) {
            g = new Attrib();
            g.genericPointer = true;
            slots[index] = g;
            if (currentAttribs[index] == null) currentAttribs[index] = g;
        }
        return g;
    }

    private Attrib conventionalSlot(int conv) {
        if (currentConventional == null) {
            currentConventional = new Attrib[CONV_COUNT];
            currentGeneric = currentAttribs.clone();
        }
        Attrib c = currentConventional[conv];
        if (c == null) {
            c = new Attrib();
            currentConventional[conv] = c;
        }
        return c;
    }

    private boolean refresh(int index) {
        touch();
        boolean repoint = false;
        final boolean conventional = isLocationConventionalEnabled(index);
        final Attrib winner = isGenericEnabled(index) ? genericSlot(index) : conventional ? currentConventional[LOCATION_CONV[index]] : null;
        if (winner != null && winner != currentAttribs[index]) {
            apply(index, winner);
            repoint = true;
        }
        final boolean enabled = winner != null;
        Attrib e = currentAttribs[index];
        if (e == null) {
            if (!enabled) return repoint;
            e = genericSlot(index);
        }
        if (e.enabled != enabled) {
            if (e.clientPointer != null) clientSideEnabledCount += enabled ? 1 : -1;
            e.enabled = enabled;
        }
        return repoint;
    }

    public boolean enableAttribute(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return false;
        genericEnabled |= 1 << index;
        return refresh(index);
    }

    public boolean disableAttribute(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return false;
        genericEnabled &= ~(1 << index);
        return refresh(index);
    }

    public boolean setConventionalEnabled(int conv, boolean enabled) {
        if (conv < 0 || conv >= CONV_COUNT) return false;
        conventionalSlot(conv);
        if (enabled) {
            conventionalEnabled |= 1 << conv;
        } else {
            conventionalEnabled &= ~(1 << conv);
        }
        final int index = CONV_LOCATION[conv];
        if (index >= 0) return refresh(index);
        touch();
        return false;
    }

    public boolean setDivisor(int index, int divisor) {
        if (index < 0 || index >= MAX_ATTRIBS) return true;
        final Attrib g = genericSlot(index);
        g.divisor = divisor;
        touch();
        return currentAttribs[index] == g;
    }

    public @Nullable Attrib conventional(int conv) {
        return conv >= 0 && currentConventional != null ? currentConventional[conv] : null;
    }

    public static final class ClientArrays {
        public int boundVAO;
        public int boundVBO;
        public int boundEBO;
        public int clientActiveTextureUnit;
        int arrayVersion;
        int vboEpoch;
        int eboEpoch;
        int vertexFlags;
        int genericEnabled;
        int conventionalEnabled;
        int genericPresent;
        int conventionalPresent;
        int effectiveGeneric;
        final Attrib[] generic = new Attrib[MAX_ATTRIBS];
        final Attrib[] conventional = new Attrib[CONV_COUNT];
    }

    private static void saveSlot(Attrib[] saved, int i, Attrib source) {
        if (saved[i] == null) saved[i] = new Attrib();
        saved[i].copyPointer(source);
    }

    public boolean isSavedVboLive(ClientArrays saved) {
        return isBufferLive(saved.boundVBO, saved.vboEpoch);
    }

    public boolean isSavedEboLive(ClientArrays saved) {
        return isBufferLive(saved.boundEBO, saved.eboEpoch);
    }

    public void saveClientArrays(ClientArrays out) {
        out.arrayVersion = arrayVersion;
        out.vboEpoch = bufferEpoch;
        out.eboEpoch = bufferEpoch;
        out.vertexFlags = currentVertexFlags;
        out.genericEnabled = genericEnabled;
        out.conventionalEnabled = conventionalEnabled;
        out.genericPresent = 0;
        out.conventionalPresent = 0;
        out.effectiveGeneric = 0;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            final Attrib g = generic(i);
            if (g != null) {
                out.genericPresent |= 1 << i;
                saveSlot(out.generic, i, g);
            }
            final Attrib e = currentAttribs[i];
            if (e != null && e.genericPointer) out.effectiveGeneric |= 1 << i;
        }
        if (currentConventional == null) return;
        for (int i = 0; i < CONV_COUNT; i++) {
            final Attrib c = currentConventional[i];
            if (c != null) {
                out.conventionalPresent |= 1 << i;
                saveSlot(out.conventional, i, c);
            }
        }
    }

    private final Attrib[] restoreBefore = new Attrib[MAX_ATTRIBS];

    public int restoreClientArrays(ClientArrays in) {
        currentVertexFlags = in.vertexFlags;
        if (arrayVersion == in.arrayVersion) return 0;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            if (restoreBefore[i] == null) restoreBefore[i] = new Attrib();
            final Attrib e = currentAttribs[i];
            if (e == null) {
                restoreBefore[i].clearPointer();
                restoreBefore[i].enabled = false;
            } else {
                restoreBefore[i].copyPointer(e);
                restoreBefore[i].enabled = e.enabled;
            }
        }
        genericEnabled = in.genericEnabled;
        conventionalEnabled = in.conventionalEnabled;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            if ((in.genericPresent >> i & 1) != 0) {
                genericSlot(i).copyPointer(in.generic[i]);
            } else {
                final Attrib g = generic(i);
                if (g != null) g.clearPointer();
            }
        }
        if (currentConventional != null || in.conventionalPresent != 0) {
            for (int i = 0; i < CONV_COUNT; i++) {
                if ((in.conventionalPresent >> i & 1) != 0) {
                    conventionalSlot(i).copyPointer(in.conventional[i]);
                } else if (currentConventional != null && currentConventional[i] != null) {
                    currentConventional[i].clearPointer();
                }
            }
        }
        clientSideEnabledCount = 0;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            final int conv = LOCATION_CONV[i];
            final Attrib g = generic(i);
            final Attrib c = conventional(conv);
            final boolean conventional = isLocationConventionalEnabled(i);
            Attrib e;
            if (isGenericEnabled(i)) {
                e = genericSlot(i);
            } else if (conventional) {
                e = conventionalSlot(conv);
            } else {
                e = c == null || (in.effectiveGeneric >> i & 1) != 0 ? g : c;
            }
            if (g != null) g.enabled = false;
            if (c != null) c.enabled = false;
            currentAttribs[i] = e;
            if (e != null) {
                e.enabled = isGenericEnabled(i) || conventional;
                if (e.isClientSide()) clientSideEnabledCount++;
            }
        }
        int changed = 0;
        for (int i = 0; i < MAX_ATTRIBS; i++) {
            final Attrib e = currentAttribs[i];
            final Attrib before = restoreBefore[i];
            if (e == null) {
                if (before.enabled) changed |= 0x10000 << i;
            } else {
                if (e.enabled != before.enabled) changed |= 0x10000 << i;
                if (!e.samePointer(before)) changed |= 1 << i;
            }
            before.clientPointer = null;
        }
        arrayVersion = in.arrayVersion;
        return changed;
    }

    public boolean isAttribEnabled(int index) {
        if (index < 0 || index >= MAX_ATTRIBS) return false;
        final Attrib a = currentAttribs[index];
        return a != null && a.enabled;
    }

    public Attrib attrib(int index) {
        return currentAttribs[index];
    }

    public int attribEffectiveStride(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.effectiveStride() : 0;
    }

    public long attribOffset(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.offset : 0;
    }

    public int attribVBO(int index) {
        final Attrib attrib = currentAttribs[index];
        return attrib != null ? attrib.vboId : 0;
    }

    public static @Nullable Attrib get(int index) {
        return GLStateManager.ctx().vaos.attrib(index);
    }



    /**
     * Returns true if any currently enabled vertex attribute was registered without a VBO
     * (i.e. as a client-side pointer). In core profile, such attribs are treated as null
     * offsets into a non-existent buffer, causing a native crash at draw time.
     */
    public boolean hasAnyClientSideEnabledAttrib() {
        return clientSideEnabledCount > 0;
    }

    /**
     * If any enabled vertex attribute uses a client-side pointer (no VBO), upload all such
     * attribs into a shared stream VBO so the draw succeeds under core profile.
     */
    private int clientArraysVBO = 0;
    private int clientArraysVBOCapacity = 0;
    private final int[] clientArraysVBOOffsets = new int[VAOManager.MAX_ATTRIBS];

    public void uploadClientArraysToVBO() {
        int totalBytes = 0;
        for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
            clientArraysVBOOffsets[i] = -1;
            final VAOManager.Attrib a = currentAttribs[i];
            if (a == null || !a.enabled || a.clientPointer == null) continue;
            clientArraysVBOOffsets[i] = totalBytes;
            totalBytes += a.clientPointer.remaining();
        }
        if (totalBytes == 0) return;

        if (clientArraysVBO == 0) {
            clientArraysVBO = RENDER_BACKEND.genBuffers();
        }

        final int savedVBO = GLSMBuffers.getBoundVBO();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, clientArraysVBO);

        if (totalBytes > clientArraysVBOCapacity) {
            int newCap = Math.max(4096, clientArraysVBOCapacity);
            while (newCap < totalBytes) newCap *= 2;
            clientArraysVBOCapacity = newCap;
        }
        RENDER_BACKEND.bufferData(GL15.GL_ARRAY_BUFFER, clientArraysVBOCapacity, GL15.GL_STREAM_DRAW);

        for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
            if (clientArraysVBOOffsets[i] < 0) continue;
            final VAOManager.Attrib a = currentAttribs[i];
            RENDER_BACKEND.bufferSubData(GL15.GL_ARRAY_BUFFER, clientArraysVBOOffsets[i], a.clientPointer.duplicate());
            if (a.integer) {
                RENDER_BACKEND.vertexAttribIPointer(i, a.size, a.type, a.stride, (long) clientArraysVBOOffsets[i]);
            } else {
                RENDER_BACKEND.vertexAttribPointer(i, a.size, a.type, a.normalized, a.stride, (long) clientArraysVBOOffsets[i]);
            }
        }

        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
    }


    public static final class Attrib {
        public boolean enabled;
        public int size;
        public int type;
        public boolean normalized;
        public boolean integer;
        public int stride;
        public long offset;
        public int vboId;
        public int divisor;
        int epoch;
        public ByteBuffer clientPointer;
        public boolean genericPointer;

        public boolean isClientSide() {
            return enabled && clientPointer != null;
        }

        void copyPointer(Attrib source) {
            size = source.size;
            type = source.type;
            normalized = source.normalized;
            integer = source.integer;
            stride = source.stride;
            offset = source.offset;
            vboId = source.vboId;
            divisor = source.divisor;
            epoch = source.epoch;
            clientPointer = source.clientPointer;
        }

        boolean samePointer(Attrib other) {
            return size == other.size && type == other.type && normalized == other.normalized && integer == other.integer
                && stride == other.stride && offset == other.offset && vboId == other.vboId && divisor == other.divisor
                && clientPointer == other.clientPointer;
        }

        void clearPointer() {
            size = 0;
            type = 0;
            normalized = false;
            integer = false;
            stride = 0;
            offset = 0;
            vboId = 0;
            divisor = 0;
            clientPointer = null;
        }

        public int effectiveStride() {
            return (stride != 0) ? stride : size * typeSizeBytes();
        }

        public int typeSizeBytes() {
            return GLTypes.sizeBytes(type);
        }

        public float readComponent(ByteBuffer buf, int base, int component) {
            return readComponent(type, normalized, buf, base, component);
        }

        public static float readComponent(int type, boolean normalized, ByteBuffer buf, int base, int component) {
            return switch (type) {
                case GL11.GL_FLOAT -> buf.getFloat(base + component * 4);
                case GL11.GL_DOUBLE -> (float) buf.getDouble(base + component * 8);
                case GL11.GL_INT -> {
                    final int v = buf.getInt(base + component * 4);
                    yield normalized ? v / (float) Integer.MAX_VALUE : (float) v;
                }
                case GL11.GL_UNSIGNED_INT -> {
                    final int v = buf.getInt(base + component * 4);
                    yield normalized ? (v & 0xFFFFFFFFL) / (float) 0xFFFFFFFFL : (float) (v & 0xFFFFFFFFL);
                }
                case GL11.GL_SHORT -> {
                    final short v = buf.getShort(base + component * 2);
                    yield normalized ? v / (float) Short.MAX_VALUE : (float) v;
                }
                case GL11.GL_UNSIGNED_SHORT -> {
                    final int v = buf.getShort(base + component * 2) & 0xFFFF;
                    yield normalized ? v / (float) 0xFFFF : (float) v;
                }
                case GL11.GL_BYTE -> {
                    final byte v = buf.get(base + component);
                    yield normalized ? v / 127.0f : (float) v;
                }
                case GL11.GL_UNSIGNED_BYTE -> {
                    final int v = buf.get(base + component) & 0xFF;
                    yield normalized ? v / 255.0f : (float) v;
                }
                default -> 0.0f;
            };
        }
    }
}
