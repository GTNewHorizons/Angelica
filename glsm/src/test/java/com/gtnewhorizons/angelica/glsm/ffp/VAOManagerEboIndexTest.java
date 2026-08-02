package com.gtnewhorizons.angelica.glsm.ffp;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VAOManagerEboIndexTest {

    private static final int DEFAULT_VAO = 0;
    private static final int NO_VAO = -1;

    private final Int2IntOpenHashMap expectedEbo = new Int2IntOpenHashMap();
    private int currentVao;

    @BeforeEach
    void reset() {
        drainManagerState();
        expectedEbo.clear();
        VAOManager.init(DEFAULT_VAO);
        expectedEbo.put(DEFAULT_VAO, 0);
        currentVao = DEFAULT_VAO;
    }

    @AfterEach
    void drain() {
        drainManagerState();
    }

    private static void drainManagerState() {
        for (int vao : VAOManager.vaoMap.keySet().toIntArray()) {
            VAOManager.onDeleteVertexArray(vao);
        }
        VAOManager.boundEBO = 0;
        assertEquals(0, VAOManager.eboOwners.size(), "draining every VAO left entries in the index");
    }

    private void bindVao(int vao) {
        if (currentVao != NO_VAO) expectedEbo.put(currentVao, VAOManager.boundEBO);
        VAOManager.onBindVertexArrayPre(vao);
        expectedEbo.putIfAbsent(vao, 0);
        currentVao = vao;
    }

    private void bindEbo(int ebo) {
        VAOManager.onBindEBO(ebo);
    }

    private int countOwners(int buffer) {
        int owners = 0;
        for (int vao : expectedEbo.keySet().toIntArray()) {
            if (expectedEbo.get(vao) == buffer) owners++;
        }
        return owners;
    }

    private void deleteBuffer(int buffer) {
        VAOManager.onDeleteBuffer(buffer);
        for (int vao : expectedEbo.keySet().toIntArray()) {
            if (expectedEbo.get(vao) == buffer) expectedEbo.put(vao, 0);
        }
    }

    private void deleteVao(int vao) {
        VAOManager.onDeleteVertexArray(vao);
        expectedEbo.remove(vao);
        if (vao == currentVao) currentVao = NO_VAO;
    }

    private void assertExpectedEbos(int op) {
        for (var entry : VAOManager.vaoMap.int2ObjectEntrySet()) {
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

    @Test
    void sharedEboClearsEveryOwner() {
        final int ebo = 77;
        bindVao(1);
        bindEbo(ebo);
        bindVao(2);
        bindEbo(ebo);
        bindVao(3);
        bindEbo(ebo);
        bindVao(DEFAULT_VAO);

        deleteBuffer(ebo);

        assertEquals(0, VAOManager.vaoMap.get(1).ebo, "vao 1 kept a freed shared EBO");
        assertEquals(0, VAOManager.vaoMap.get(2).ebo, "vao 2 kept a freed shared EBO");
        assertEquals(0, VAOManager.vaoMap.get(3).ebo, "vao 3 kept a freed shared EBO");
        assertFalse(VAOManager.eboOwners.containsKey(ebo), "index kept owners for a freed EBO");
    }

    @Test
    void sharedEboEntryDropsOnlyAfterItsLastOwnerGoes() {
        final int ebo = 88;
        bindVao(1);
        bindEbo(ebo);
        bindVao(2);
        bindEbo(ebo);
        bindVao(DEFAULT_VAO);

        deleteVao(1);
        assertTrue(VAOManager.eboOwners.containsKey(ebo), "index dropped a still-live owner");
        deleteVao(2);
        assertFalse(VAOManager.eboOwners.containsKey(ebo), "index kept an entry after its last owner went away");
    }

    @Test
    void deletingTheBoundVaoDoesNotOrphanAnIndexEntry() {
        final int ebo = 99;
        bindVao(1);
        bindEbo(ebo);

        deleteVao(1);
        bindVao(DEFAULT_VAO);

        assertFalse(VAOManager.eboOwners.containsKey(ebo), "deleting the bound VAO left an unreachable owner in the index");
        assertEquals(0, VAOManager.eboOwners.size(), "index leaked an entry for a deleted VAO");
    }

    @Test
    void indexEmptiesOutWhenEverythingIsDeleted() {
        for (int vao = 1; vao <= 64; vao++) {
            bindVao(vao);
            bindEbo(1000 + vao);
        }
        bindVao(DEFAULT_VAO);
        for (int vao = 1; vao <= 64; vao++) {
            deleteVao(vao);
        }
        assertEquals(0, VAOManager.eboOwners.size(), "index leaked entries after every VAO was deleted");
    }
}
