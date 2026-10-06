package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelFormat;
import com.gtnewhorizons.angelica.glsm.texture.PixelType;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import net.coderbot.iris.gl.image.ImageInformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class CustomImageAtomicsTest {

    private RenderBackend original;
    private RenderBackend backend;

    @BeforeEach
    void installBackend() {
        original = BackendManager.RENDER_BACKEND;
        backend = mock(RenderBackend.class, delegatesTo(original));
        doAnswer(call -> call.<Integer>getArgument(0) == 40 && call.<Integer>getArgument(1) == 10 && call.<Integer>getArgument(2) == 1).when(backend).supportsComputeImageAtomics(anyInt(), anyInt(), anyInt());
        Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, backend);
    }

    @AfterEach
    void restoreBackend() {
        Reflect.setStaticFinal(BackendManager.class, "RENDER_BACKEND", RenderBackend.class, original);
        RwImageStoreExtractor.setActiveCustomImages(null);
    }

    private static ImageInformation image(String name, int width, int height, boolean relative) {
        return new ImageInformation(name, name + "_sampler", TextureType.TEXTURE_2D, PixelFormat.RED_INTEGER, InternalTextureFormat.R32I, PixelType.INT, width, height, 1, false, relative, relative ? 1.0f : 0.0f, relative ? 1.0f : 0.0f);
    }

    private static Map<String, ImageInformation> images() {
        return Map.of("small_img", image("small_img", 40, 10, false), "large_img", image("large_img", 64, 64, false), "screen_img", image("screen_img", 40, 10, true));
    }

    @Test
    void onlyImagesTheBackendAcceptsBySizeKeepAtomics() {
        doReturn(false).when(backend).supportsComputeImageAtomics();
        RwImageStoreExtractor.setActiveCustomImages(images());

        assertTrue(RwImageStoreExtractor.supportsAtomics("small_img"));
        assertFalse(RwImageStoreExtractor.supportsAtomics("large_img"));
        assertFalse(RwImageStoreExtractor.supportsAtomics("screen_img"));
        assertFalse(RwImageStoreExtractor.supportsAtomics("colortex0"));
        assertEquals("small_img", RwImageStoreExtractor.atomicCustomImagesKey());
    }

    @Test
    void backendWithImageAtomicsEverywhereNeedsNoPerImageList() {
        doReturn(true).when(backend).supportsComputeImageAtomics();
        RwImageStoreExtractor.setActiveCustomImages(images());

        assertTrue(RwImageStoreExtractor.supportsAtomics("small_img"));
        assertTrue(RwImageStoreExtractor.supportsAtomics("large_img"));
        assertTrue(RwImageStoreExtractor.supportsAtomics("screen_img"));
        assertTrue(RwImageStoreExtractor.supportsAtomics("colortex0"));
        assertEquals("", RwImageStoreExtractor.atomicCustomImagesKey());
    }

    @Test
    void clearingTheImagesClearsTheList() {
        doReturn(false).when(backend).supportsComputeImageAtomics();
        RwImageStoreExtractor.setActiveCustomImages(images());
        RwImageStoreExtractor.setActiveCustomImages(null);

        assertFalse(RwImageStoreExtractor.supportsAtomics("small_img"));
        assertEquals("", RwImageStoreExtractor.atomicCustomImagesKey());
    }
}
