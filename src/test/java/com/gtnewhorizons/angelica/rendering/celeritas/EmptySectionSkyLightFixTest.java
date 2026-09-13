package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.compat.ExtendedBlockStorageExt;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression test: underground all-air sections must no longer render at full sky brightness.
 *
 * Background: the server does not send a storage array for fully-air sections (e.g. large
 * underground cavities), so the client sees section == null. Before the fix,
 * {@code ClonedChunkSection.init} fell back to EMPTY_SECTION (hasSky=false), so
 * {@code getLightArray(Sky)} returned null and {@code WorldSlice.getLightLevel} fell back to
 * {@code defaultLightValues[Sky]}=15 (full bright) for any null light array, making an
 * underground cavity look sunlit. Vanilla returns {@code canBlockSeeTheSky ? 15 : 0} for null
 * sections instead.
 *
 * This test drives the real {@code buildEmptySectionWithSkyLight} via reflection with a mocked
 * Chunk, verifying that underground empty sections get skylight=0 after the fix, and contrasts
 * that with the old EMPTY_SECTION behavior (skylight array null -> 15 fallback).
 */
class EmptySectionSkyLightFixTest {

    /** Underground empty section: no column sees the sky -> skylight must be 0 after the fix. */
    @Test
    void undergroundEmptySectionGetsZeroSkyLight() throws Exception {
        final Chunk chunk = Mockito.mock(Chunk.class);
        final ChunkSectionPos pos = ChunkSectionPos.from(0, 2, 0); // y section 2 => world y 32..47
        when(chunk.canBlockSeeTheSky(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(false); // underground, no sky visible

        final ExtendedBlockStorage section = invokeBuildEmptySection(chunk, pos);

        assertNotNull(section, "the fix should supply an EBS for empty sections");
        assertNotNull(section.getSkylightArray(), "the fix must provide a skylight array (no 15 fallback)");
        assertEquals(0, section.getExtSkylightValue(0, 0, 0));
        assertEquals(0, section.getExtSkylightValue(7, 7, 7));
        assertEquals(0, section.getExtSkylightValue(15, 15, 15));

        // ExtendedBlockStorageExt must copy the skylight array and set hasSky=true so that
        // getLightArray(Sky) returns a real (0) array instead of null triggering the 15 fallback.
        final ExtendedBlockStorageExt ext = new ExtendedBlockStorageExt(chunk, section);
        assertTrue(ext.hasSky, "hasSky must be true after the fix");
        assertNotNull(ext.getSkylightArray(), "getLightArray(Sky) must no longer be null");
        assertEquals(0, ext.getSkylightArray().get(0, 0, 0));
    }

    /** All-sky empty section: default 15 is already correct -> fast-path to EMPTY_SECTION (no cost). */
    @Test
    void allSkyEmptySectionFallsBackToEmptSection() throws Exception {
        final Chunk chunk = Mockito.mock(Chunk.class);
        final ChunkSectionPos pos = ChunkSectionPos.from(0, 15, 0); // y section 15 => world y 240..255
        when(chunk.canBlockSeeTheSky(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true); // exposed to sky

        final ExtendedBlockStorage section = invokeBuildEmptySection(chunk, pos);

        assertNotNull(section);
        assertNull(section.getSkylightArray(),
            "all-sky empty sections keep EMPTY_SECTION (default skylight 15 is correct, no allocation)");
    }

    /** Old behavior demonstration: EMPTY_SECTION (hasSky=false) has no skylight array -> 15 fallback. */
    @Test
    void oldBehaviorEmptSectionHasNoSkyLightArray() throws Exception {
        final ExtendedBlockStorage EMPTY = revealEmptySection();
        assertNull(EMPTY.getSkylightArray(),
            "before the fix, empty sections had no skylight array, so WorldSlice fell back to 15 (the bug)");
    }

    /**
     * Demo test: prints a before/after comparison into the test output (handy as evidence for a PR/issue).
     * Scenario: a mega-hall-like underground empty section (world y 32..47, no column sees the sky).
     */
    @Test
    void printBeforeAfterEvidence() throws Exception {
        final Chunk chunk = Mockito.mock(Chunk.class);
        final ChunkSectionPos pos = ChunkSectionPos.from(0, 2, 0);
        when(chunk.canBlockSeeTheSky(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(false); // underground

        // BEFORE: old init path used EMPTY_SECTION directly
        final ExtendedBlockStorage before = revealEmptySection();
        final ExtendedBlockStorageExt beforeExt = new ExtendedBlockStorageExt(chunk, before);
        // WorldSlice.getLightLevel returns defaultLightValues[Sky] for null light arrays
        // = EnumSkyBlock.Sky.defaultLightValue (this dimension hasNoSky=false => 15)
        final int beforeFallback = net.minecraft.world.EnumSkyBlock.Sky.defaultLightValue;

        // AFTER: the fixed buildEmptySectionWithSkyLight
        final ExtendedBlockStorage after = invokeBuildEmptySection(chunk, pos);
        final ExtendedBlockStorageExt afterExt = new ExtendedBlockStorageExt(chunk, after);
        final int afterSky = afterExt.getSkylightArray().get(7, 7, 7);

        final StringBuilder sb = new StringBuilder();
        sb.append("\n===== EmptySectionSkyLight evidence (underground all-air section, world y 32..47) =====\n");
        sb.append("BEFORE: init -> EMPTY_SECTION hasSky=").append(beforeExt.hasSky)
            .append(" getLightArray(Sky)=null -> WorldSlice fallback Sky=").append(beforeFallback)
            .append("   <-- underground cavity rendered fully sunlit (the bug)\n");
        sb.append("AFTER : init -> buildEmptySectionWithSkyLight hasSky=").append(afterExt.hasSky)
            .append(" getLightArray(Sky) value=").append(afterSky)
            .append("   <-- buried = 0, matches vanilla canBlockSeeTheSky semantics\n");
        sb.append("================================================================================\n");
        System.out.print(sb);

        assertTrue(afterExt.hasSky, "hasSky must be true after the fix");
        assertEquals(0, afterSky, "underground empty section skylight must be 0");
    }

    /** Reflectively invoke private static buildEmptySectionWithSkyLight(Chunk, ChunkSectionPos). */
    private static ExtendedBlockStorage invokeBuildEmptySection(
            Chunk chunk, ChunkSectionPos pos) throws Exception {
        final Method m = ClonedChunkSection.class.getDeclaredMethod(
            "buildEmptySectionWithSkyLight", Chunk.class, ChunkSectionPos.class);
        m.setAccessible(true);
        return (ExtendedBlockStorage) m.invoke(null, chunk, pos);
    }

    /** Reflectively read the private static EMPTY_SECTION field of ClonedChunkSection. */
    private static ExtendedBlockStorage revealEmptySection() throws Exception {
        final java.lang.reflect.Field f = ClonedChunkSection.class.getDeclaredField("EMPTY_SECTION");
        f.setAccessible(true);
        return (ExtendedBlockStorage) f.get(null);
    }
}
