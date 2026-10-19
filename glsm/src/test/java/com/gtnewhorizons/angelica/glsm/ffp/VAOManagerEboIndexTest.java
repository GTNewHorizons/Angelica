package com.gtnewhorizons.angelica.glsm.ffp;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VAOManagerEboIndexTest {

    private static final int DEFAULT_VAO = 0;
    private static final int NO_VAO = -1;

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
        vaos.onDeleteBuffer(buffer);
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
}
