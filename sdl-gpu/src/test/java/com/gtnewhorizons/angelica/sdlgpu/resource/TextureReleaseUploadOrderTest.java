package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void releaseIsImmediateWhenNoTransferThreadIsAttached() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        setUploadSeqs(rm, 50L);
        assertFalse(rm.shouldDeferTextureRelease(), "with no transfer thread there is nothing to outlive");
    }

    @Test
    void releaseIsDeferredWhileTextureUploadsAreStillQueued() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(rig.device, rm);
        rm.setTransferThread(tt);
        setUploadSeqs(rm, 50L);
        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", 10L);

        rm.trackTextureHandle(HANDLE);
        final int glId = rm.genTexture();
        SdlReflect.putTextureHandle(rm, glId, HANDLE);

        rm.deleteTexture(glId);
        assertEquals(List.of(HANDLE), deferredReleases(rm), "the free must wait for the queued upload");
    }

    @Test
    void releaseIsImmediateOnceTheTransferThreadHasCaughtUp() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(rig.device, rm);
        rm.setTransferThread(tt);
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

    @Test
    void reallocReleaseObeysTheSameRule() {
        final SdlTestRig rig = SdlTestRig.create();
        final ResourceManager rm = rig.resourceManager;
        final TransferThread tt = new TransferThread(rig.device, rm);
        rm.setTransferThread(tt);
        setUploadSeqs(rm, 7L);
        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", 0L);

        rm.trackTextureHandle(HANDLE);
        final int glId = rm.genTexture();
        SdlReflect.putTextureHandle(rm, glId, HANDLE);

        rm.releaseTextureHandleForRealloc(glId);
        assertEquals(List.of(HANDLE), deferredReleases(rm), "glTexImage2D re-specification frees the old handle on the same path as a delete");
    }
}
