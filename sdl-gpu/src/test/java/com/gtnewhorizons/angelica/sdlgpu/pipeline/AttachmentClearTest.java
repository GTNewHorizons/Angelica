package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.RealDeviceExtension;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(RealDeviceExtension.class)
class AttachmentClearTest {

    private static SdlTestRig rig;
    private static ShaderManager sm;
    private static PipelineStore store;
    private static AttachmentClear attachmentClear;

    private static final int SIZE = 32;

    private static long colorTexture;
    private static int colorFormat;
    private static long depthTexture;
    private static int depthFormat;

    @BeforeAll
    static void setUp() throws Exception {
        rig = RealDeviceExtension.rig();
        sm = new ShaderManager(rig.device);
        store = new PipelineStore(rig.device);
        attachmentClear = new AttachmentClear(rig.frameManager, store, sm);

        final RealDeviceExtension.Target color = RealDeviceExtension.colorTarget(9700, SIZE, SIZE);
        colorTexture = color.texture();
        colorFormat = color.sdlFormat();
        PipelineCache.setSwapchainFormats(new int[]{ colorFormat });

        final RealDeviceExtension.Target depth = RealDeviceExtension.depthStencilTarget(9701, SIZE, SIZE);
        depthTexture = depth.texture();
        depthFormat = depth.sdlFormat();
    }

    private static FboState makeFbo() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = colorTexture;
        fbo.colorFormats[0] = colorFormat;
        fbo.colorAttachmentCount = 1;
        fbo.drawBuffers = new int[]{ 0 };
        fbo.cachedColorFormats = new int[]{ colorFormat };
        fbo.depthTexture = depthTexture;
        fbo.depthFormat = depthFormat;
        fbo.width = SIZE;
        fbo.height = SIZE;
        fbo.recomputeTargets();
        return fbo;
    }

    @Test
    void onePipelinePerLayoutModeAndMask() {
        final FboState fbo = makeFbo();
        final long p1 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 0, true, false, 0, 0);
        final long p1Again = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 0, true, false, 0, 0);
        assertTrue(p1 != 0, "pipeline build must succeed");
        assertEquals(p1, p1Again, "the same (layout, mode, mask) must reuse the cached pipeline");

        final long p2 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 0, false, true, 0xFF, 0);
        assertNotEquals(p1, p2, "a different clear mode must build a different pipeline");

        final long p3 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 0, false, true, 0x0F, 0);
        assertNotEquals(p2, p3, "a different stencil write mask must build a different pipeline");

        final FboState depthOnlyLayout = makeFbo();
        depthOnlyLayout.drawBuffers = new int[0];
        depthOnlyLayout.cachedColorFormats = new int[0];
        final long p4 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(depthOnlyLayout), 0, true, false, 0, 0);
        assertTrue(p4 != 0, "a depth-only layout pipeline must still build");
        assertNotEquals(p1, p4, "a different color-target layout must build a different pipeline");

        final long pc1 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 1, false, false, 0, 0xF);
        final long pc1Again = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 1, false, false, 0, 0xF);
        assertTrue(pc1 != 0, "color pipeline build must succeed");
        assertEquals(pc1, pc1Again, "the same color mask must reuse the cached pipeline");

        final long pc2 = attachmentClear.getOrCreatePipeline(attachmentClear.forFbo(fbo), 1, false, false, 0, 0x9);
        assertNotEquals(pc1, pc2, "a different color write mask must build a different pipeline");
    }
}
