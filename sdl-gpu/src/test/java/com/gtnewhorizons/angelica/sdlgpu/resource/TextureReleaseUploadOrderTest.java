package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureReleaseUploadOrderTest {

    private static final long HANDLE = 0xBEEFL;

    private static List<Long> deferredReleases(ResourceManager rm) {
        return Reflect.get(rm, "deferredTextureReleases");
    }

    private static void setUploadSeqs(ResourceManager rm, long lastUpload) {
        Reflect.setDeclared(ResourceManager.class, rm, "lastTextureUploadSeq", lastUpload);
    }

    private static Stream<Arguments> releasePaths() {
        final BiConsumer<ResourceManager, Integer> delete = ResourceManager::deleteTexture;
        final BiConsumer<ResourceManager, Integer> realloc = ResourceManager::releaseTextureHandleForRealloc;
        return Stream.of(
            Arguments.of("glDeleteTextures", delete, 50L, 10L),
            Arguments.of("glTexImage re-specification", realloc, 7L, 0L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releasePaths")
    void releaseIsDeferredWhileTextureUploadsAreStillQueued(String path, BiConsumer<ResourceManager, Integer> release, long lastUpload, long submitted) {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = rig.installTransferThread();
        setUploadSeqs(rm, lastUpload);
        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", submitted);

        rm.trackTextureHandle(HANDLE);
        final int glId = rm.genTexture();
        SdlReflect.putTextureHandle(rm, glId, HANDLE);

        release.accept(rm, glId);
        assertEquals(List.of(HANDLE), deferredReleases(rm), "the free must wait for the queued upload");
    }

    @Test
    void releaseIsImmediateOnceTheTransferThreadHasCaughtUp() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = rig.installTransferThread();
        setUploadSeqs(rm, 50L);

        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", 49L);
        assertTrue(rm.shouldDeferTextureRelease(), "one upload still unsubmitted is enough to defer");

        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", 50L);
        assertFalse(rm.shouldDeferTextureRelease(), "nothing is in flight, so no need to defer");
    }

    @Test
    void aQueuedInlineBatchAlsoDefersTheRelease() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        rig.frameManager.setResourceManager(rm);
        setUploadSeqs(rm, 50L);
        assertFalse(rm.shouldDeferTextureRelease(), "nothing queued yet");

        rig.frameManager.frame().pendingTexHandles.add(HANDLE);
        assertTrue(rm.hasBatchedUploads());
        assertTrue(rm.shouldDeferTextureRelease(), "a queued batch entry must outlive the release");

        rig.frameManager.frame().pendingTexHandles.clear();
        assertFalse(rm.shouldDeferTextureRelease());
    }

    @Test
    void releasingATextureBlanksItsQueuedBatchEntries() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        rig.frameManager.setResourceManager(rm);
        final var handles = rig.frameManager.frame().pendingTexHandles;
        handles.add(HANDLE);
        handles.add(0xFEEDL);
        handles.add(HANDLE);

        rm.dropBatchedUploadsTargeting(HANDLE);

        assertEquals(0L, handles.getLong(0), "every entry for the dying texture is blanked");
        assertEquals(0xFEEDL, handles.getLong(1), "other destinations are untouched");
        assertEquals(0L, handles.getLong(2));
    }
}
