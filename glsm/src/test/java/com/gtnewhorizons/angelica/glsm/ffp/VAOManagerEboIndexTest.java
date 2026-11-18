package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VAOManagerEboIndexTest {

    private static final int DEFAULT_VAO = 0;
    private static final int NO_VAO = -1;
    private static final VAOManager.ClientArrays[] NO_SAVED = new VAOManager.ClientArrays[0];

    private final VAOManager vaos = new VAOManager();
    private final Int2IntOpenHashMap expectedEbo = new Int2IntOpenHashMap();
    private int currentVao;

    @BeforeEach
    void reset() {
        drainManagerState();
        expectedEbo.clear();
        vaos.init(DEFAULT_VAO);
        expectedEbo.put(DEFAULT_VAO, 0);
        currentVao = DEFAULT_VAO;
    }

    @AfterEach
    void drain() {
        drainManagerState();
    }

    private void drainManagerState() {
        for (int vao : vaos.vaoMap.keySet().toIntArray()) {
            vaos.onDeleteVertexArray(vao);
        }
        vaos.boundEBO = 0;
        assertEquals(0, vaos.eboOwners.size(), "draining every VAO left entries in the index");
    }

    private void bindVao(int vao) {
        if (currentVao != NO_VAO) expectedEbo.put(currentVao, vaos.boundEBO);
        vaos.onBindVertexArrayPre(vao);
        expectedEbo.putIfAbsent(vao, 0);
        currentVao = vao;
    }

    private void bindEbo(int ebo) {
        vaos.onBindEBO(ebo);
    }

    private int countOwners(int buffer) {
        int owners = 0;
        for (int vao : expectedEbo.keySet().toIntArray()) {
            if (expectedEbo.get(vao) == buffer) owners++;
        }
        return owners;
    }

    private void deleteBuffer(int buffer) {
        vaos.onDeleteBuffer(buffer, NO_SAVED);
        for (int vao : expectedEbo.keySet().toIntArray()) {
            if (expectedEbo.get(vao) == buffer) expectedEbo.put(vao, 0);
        }
    }

    private void deleteVao(int vao) {
        vaos.onDeleteVertexArray(vao);
        expectedEbo.remove(vao);
        if (vao == currentVao) currentVao = NO_VAO;
    }

    private void assertExpectedEbos(int op) {
        for (var entry : vaos.vaoMap.int2ObjectEntrySet()) {
            final int vao = entry.getIntKey();
            if (vao == currentVao) continue;
            assertEquals(expectedEbo.get(vao), entry.getValue().ebo, () -> "op " + op + ": vao " + vao + " ebo mismatch");
        }
    }

    @Test
    void everyVaoKeepsItsExpectedEboUnderRandomOps() {
        final Random random = new Random(0xC0FFEE);
        final IntArrayList liveVaos = new IntArrayList();
        final IntArrayList liveBuffers = new IntArrayList();
        int nextVao = 1;
        int nextBuffer = 1;
        int sharedEboDeletes = 0;

        for (int op = 0; op < 200_000; op++) {
            switch (random.nextInt(5)) {
                case 0 -> {
                    final int vao = nextVao++;
                    liveVaos.add(vao);
                    bindVao(vao);
                }
                case 1 -> {
                    if (liveVaos.isEmpty()) break;
                    bindVao(liveVaos.getInt(random.nextInt(liveVaos.size())));
                }
                case 2 -> {
                    if (currentVao == NO_VAO) break;
                    final int buffer = (random.nextBoolean() && nextBuffer > 8) ? 1 + random.nextInt(nextBuffer - 1) : nextBuffer++;
                    liveBuffers.add(buffer);
                    bindEbo(buffer);
                }
                case 3 -> {
                    if (liveBuffers.isEmpty()) break;
                    final int buffer = liveBuffers.removeInt(random.nextInt(liveBuffers.size()));
                    if (countOwners(buffer) > 1) sharedEboDeletes++;
                    deleteBuffer(buffer);
                }
                case 4 -> {
                    if (liveVaos.isEmpty()) break;
                    final int vao = liveVaos.removeInt(random.nextInt(liveVaos.size()));
                    final boolean wasBound = vao == currentVao;
                    deleteVao(vao);
                    if (wasBound) bindVao(DEFAULT_VAO);
                }
            }
            assertExpectedEbos(op);
        }
        assertTrue(sharedEboDeletes > 20, "op stream never freed a multi-owner EBO (hits: " + sharedEboDeletes + ")");
    }

    private static final int PRUNE_INTERVAL = 4;
    private static final int OPS = 40_000;
    private static final int SLOTS = 4;
    private static final int[] CONVS = {VAOManager.CONV_POSITION, VAOManager.CONV_COLOR, VAOManager.CONV_SECONDARY_COLOR, VAOManager.CONV_TEX0};
    private static final String[] CURRENT_FIELDS = {"currentAttribs", "currentGeneric", "currentConventional"};
    private static final int STACK_DEPTH = 16;
    private static final int MAX_VAOS = 8;
    private static final int MAX_LIVE_BUFFERS = 24;

    private final VAOManager pruned = new VAOManager(PRUNE_INTERVAL);
    private final VAOManager oracle = new VAOManager(OPS + 1);
    private final VAOManager[] pair = {pruned, oracle};
    private final VAOManager.ClientArrays[][] stacks = new VAOManager.ClientArrays[pair.length][STACK_DEPTH];
    private int deadSlotChecks;

    private static int pick(Random random, IntArrayList list) {
        return list.getInt(random.nextInt(list.size()));
    }

    private int staleName(Random random, IntArrayList vaoIds, IntLinkedOpenHashSet freeNames) {
        final VAOManager.VAOData data = oracle.vaoMap.get(pick(random, vaoIds));
        final VAOManager.Attrib[] slots = random.nextBoolean() ? data.attribs : data.conventional;
        if (slots == null) return 0;
        final VAOManager.Attrib a = slots[random.nextInt(slots.length)];
        return a != null && a.vboId != 0 && freeNames.remove(a.vboId) ? a.vboId : 0;
    }

    private void bindBoth(int vao) {
        if (vao == currentVao) return;
        for (VAOManager m : pair) m.onBindVertexArrayPre(vao);
        currentVao = vao;
    }

    private static void restore(VAOManager m, VAOManager.ClientArrays saved) {
        if (m.isSavedVboLive(saved)) m.restoreClientArrays(saved);
        if (m.boundEBO != saved.boundEBO && m.isSavedEboLive(saved)) m.onBindEBO(saved.boundEBO);
    }

    private static String where(int op, String kind, int id, int slot) {
        return "op " + op + ": " + kind + " " + id + " slot " + slot;
    }

    private void assertSameSlot(int op, String kind, int id, int slot, VAOManager.Attrib p, VAOManager.Attrib o, boolean countDead) {
        assertEquals(o == null, p == null, () -> where(op, kind, id, slot) + " presence");
        if (o == null) return;
        assertEquals(o.vboId, p.vboId, () -> where(op, kind, id, slot) + " vbo");
        final boolean live = oracle.isBufferLive(o);
        assertEquals(live, pruned.isBufferLive(p), () -> where(op, kind, id, slot) + " liveness of buffer " + o.vboId);
        if (countDead && !live && o.vboId != 0) deadSlotChecks++;
    }

    private void assertSameSlots(int op, String kind, int id, VAOManager.Attrib[] p, VAOManager.Attrib[] o, boolean countDead) {
        assertEquals(o == null, p == null, () -> where(op, kind, id, -1) + " array presence");
        if (o == null) return;
        for (int i = 0; i < o.length; i++) assertSameSlot(op, kind, id, i, p[i], o[i], countDead);
    }

    private void assertSameLiveness(int op, int sp) {
        assertEquals(oracle.vaoMap.keySet(), pruned.vaoMap.keySet(), () -> "op " + op + ": vao sets differ");
        for (var entry : oracle.vaoMap.int2ObjectEntrySet()) {
            final int vao = entry.getIntKey();
            final VAOManager.VAOData o = entry.getValue();
            final VAOManager.VAOData p = pruned.vaoMap.get(vao);
            final boolean countDead = vao != currentVao;
            assertSameSlots(op, "vao attribs", vao, p.attribs, o.attribs, countDead);
            assertSameSlots(op, "vao generic", vao, p.generic, o.generic, countDead);
            assertSameSlots(op, "vao conventional", vao, p.conventional, o.conventional, countDead);
        }
        for (String field : CURRENT_FIELDS) {
            assertSameSlots(op, field, currentVao, Reflect.get(pruned, field), Reflect.get(oracle, field), false);
        }
        for (int s = 0; s < sp; s++) {
            final VAOManager.ClientArrays p = stacks[0][s];
            final VAOManager.ClientArrays o = stacks[1][s];
            final int depth = s;
            assertEquals(oracle.isSavedVboLive(o), pruned.isSavedVboLive(p), () -> "op " + op + ": saved " + depth + " vbo liveness");
            assertEquals(oracle.isSavedEboLive(o), pruned.isSavedEboLive(p), () -> "op " + op + ": saved " + depth + " ebo liveness");
            for (int i = 0; i < VAOManager.MAX_ATTRIBS; i++) {
                if ((o.genericPresent >> i & 1) != 0) assertSameSlot(op, "saved generic", s, i, p.generic[i], o.generic[i], true);
            }
            for (int i = 0; i < VAOManager.CONV_COUNT; i++) {
                if ((o.conventionalPresent >> i & 1) != 0) assertSameSlot(op, "saved conventional", s, i, p.conventional[i], o.conventional[i], true);
            }
        }
    }

    @Test
    void prunedDeletedNamesAnswerLivenessLikeAnUnprunedReference() {
        final Random random = new Random(0x5EED);
        for (VAOManager m : pair) m.init(DEFAULT_VAO);
        for (VAOManager.ClientArrays[] stack : stacks) {
            for (int s = 0; s < STACK_DEPTH; s++) stack[s] = new VAOManager.ClientArrays();
        }
        final IntArrayList vaoIds = IntArrayList.of(DEFAULT_VAO);
        final IntArrayList liveBuffers = new IntArrayList();
        final IntLinkedOpenHashSet freeNames = new IntLinkedOpenHashSet();
        final IntOpenHashSet everDeleted = new IntOpenHashSet();
        int sp = 0;
        int nextVao = 1;
        int nextBuffer = 1;
        int prunes = 0;
        int reusedDeletes = 0;

        for (int op = 0; op < OPS; op++) {
            final int epochBefore = Reflect.get(pruned, "bufferEpoch");
            switch (random.nextInt(11)) {
                case 0, 1 -> {
                    if (liveBuffers.size() >= MAX_LIVE_BUFFERS) break;
                    final int stale = random.nextInt(3) == 0 ? staleName(random, vaoIds, freeNames) : 0;
                    liveBuffers.add(stale != 0 ? stale : !freeNames.isEmpty() && random.nextInt(3) == 0 ? freeNames.removeLastInt() : nextBuffer++);
                }
                case 2, 3 -> {
                    if (liveBuffers.isEmpty()) break;
                    final int buffer = liveBuffers.removeInt(random.nextInt(liveBuffers.size()));
                    if (!everDeleted.add(buffer)) reusedDeletes++;
                    for (int m = 0; m < pair.length; m++) {
                        if (pair[m].boundEBO == buffer) pair[m].onBindEBO(0);
                        pair[m].onDeleteBuffer(buffer, stacks[m]);
                    }
                    freeNames.add(buffer);
                }
                case 4 -> {
                    final int index = random.nextInt(SLOTS);
                    final int buffer = liveBuffers.isEmpty() || random.nextInt(8) == 0 ? 0 : pick(random, liveBuffers);
                    for (VAOManager m : pair) m.setAttribute(index, 4, GL11.GL_FLOAT, false, 0, 0L, buffer);
                }
                case 5 -> {
                    final int conv = CONVS[random.nextInt(CONVS.length)];
                    final int buffer = liveBuffers.isEmpty() || random.nextInt(8) == 0 ? 0 : pick(random, liveBuffers);
                    for (VAOManager m : pair) m.setConventional(conv, 4, GL11.GL_FLOAT, false, 0, 0L, buffer);
                }
                case 6 -> {
                    final int vao;
                    if (vaoIds.size() < MAX_VAOS && random.nextBoolean()) {
                        vao = nextVao++;
                        vaoIds.add(vao);
                    } else {
                        vao = pick(random, vaoIds);
                    }
                    bindBoth(vao);
                }
                case 7 -> {
                    final int ebo = liveBuffers.isEmpty() || random.nextInt(4) == 0 ? 0 : pick(random, liveBuffers);
                    for (VAOManager m : pair) m.onBindEBO(ebo);
                }
                case 8 -> {
                    if (sp == STACK_DEPTH) break;
                    final int vbo = liveBuffers.isEmpty() || random.nextInt(4) == 0 ? 0 : pick(random, liveBuffers);
                    for (int m = 0; m < pair.length; m++) {
                        final VAOManager.ClientArrays saved = stacks[m][sp];
                        saved.boundVAO = currentVao;
                        saved.boundVBO = vbo;
                        saved.boundEBO = pair[m].boundEBO;
                        pair[m].saveClientArrays(saved);
                    }
                    sp++;
                }
                case 9 -> {
                    if (sp == 0) break;
                    sp--;
                    final int vao = stacks[1][sp].boundVAO;
                    if (!oracle.vaoMap.containsKey(vao)) break;
                    bindBoth(vao);
                    for (int m = 0; m < pair.length; m++) restore(pair[m], stacks[m][sp]);
                }
                case 10 -> {
                    if (vaoIds.size() == 1) break;
                    final int vao = vaoIds.removeInt(1 + random.nextInt(vaoIds.size() - 1));
                    for (VAOManager m : pair) m.onDeleteVertexArray(vao);
                    if (vao == currentVao) {
                        currentVao = NO_VAO;
                        bindBoth(DEFAULT_VAO);
                    }
                }
            }
            final int epoch = Reflect.get(pruned, "bufferEpoch");
            final int at = op;
            assertTrue(epoch < PRUNE_INTERVAL, () -> "op " + at + ": epoch " + epoch + " escaped the prune interval");
            if (epoch < epochBefore) prunes++;
            assertSameLiveness(op, sp);
        }
        assertTrue(prunes >= 800, "op stream pruned only " + prunes + " times");
        assertTrue(deadSlotChecks >= 400_000, "op stream compared only " + deadSlotChecks + " dead slots");
        assertTrue(reusedDeletes >= 1_000, "op stream deleted only " + reusedDeletes + " reused names");
    }
}
