package com.prupe.mcpatcher.mal.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;

class TexturePackChangeHandlerDeferralTest {

    private static final class CountingHandler extends TexturePackChangeHandler {

        int before;
        int after;
        int after2;

        CountingHandler() {
            super("counting", 0);
        }

        @Override
        public void beforeChange() {
            before++;
        }

        @Override
        public void afterChange() {
            after++;
        }

        @Override
        public void afterChange2() {
            after2++;
        }
    }

    private boolean savedFeatures;
    private Object savedLoader;
    private Object savedMinecraft;
    private Object savedResourceList;
    private List<TexturePackChangeHandler> savedHandlers;
    private LoadController controller;

    @BeforeEach
    void stubLoader() {
        savedFeatures = AngelicaConfig.enableMCPatcherForgeFeatures;
        savedLoader = Reflect.getStatic(Loader.class, "instance");
        savedMinecraft = Reflect.getStatic(Minecraft.class, "theMinecraft");
        savedResourceList = Reflect.getStatic(ResourceList.class, "instance");
        final List<TexturePackChangeHandler> handlers = Reflect.getStatic(TexturePackChangeHandler.class, "handlers");
        savedHandlers = new ArrayList<>(handlers);
        handlers.clear();

        AngelicaConfig.enableMCPatcherForgeFeatures = true;
        controller = Reflect.allocate(LoadController.class);
        setState(LoaderState.PREINITIALIZATION);
        final Loader loader = Reflect.allocate(Loader.class);
        Reflect.setDeclared(Loader.class, loader, "modController", controller);
        Reflect.setStatic(Loader.class, "instance", loader);

        final Minecraft minecraft = mock(Minecraft.class);
        when(minecraft.getResourceManager()).thenReturn(mock(IResourceManager.class));
        Reflect.setStatic(Minecraft.class, "theMinecraft", minecraft);

        resetGate();
    }

    @AfterEach
    void restore() {
        final List<TexturePackChangeHandler> handlers = Reflect.getStatic(TexturePackChangeHandler.class, "handlers");
        handlers.clear();
        handlers.addAll(savedHandlers);
        resetGate();
        Reflect.setStatic(ResourceList.class, "instance", savedResourceList);
        Reflect.setStatic(Minecraft.class, "theMinecraft", savedMinecraft);
        Reflect.setStatic(Loader.class, "instance", savedLoader);
        AngelicaConfig.enableMCPatcherForgeFeatures = savedFeatures;
    }

    private void setState(LoaderState state) {
        Reflect.setDeclared(LoadController.class, controller, "state", state);
    }

    private static void resetGate() {
        Reflect.setStatic(TexturePackChangeHandler.class, "loaderAvailable", false);
        Reflect.setStatic(TexturePackChangeHandler.class, "deferredDepth", 0);
        Reflect.setStatic(TexturePackChangeHandler.class, "recurseDepth", 0);
    }

    private static int recurseDepth() {
        return Reflect.getStatic(TexturePackChangeHandler.class, "recurseDepth");
    }

    private static int deferredDepth() {
        return Reflect.getStatic(TexturePackChangeHandler.class, "deferredDepth");
    }

    private static void reload() {
        TexturePackChangeHandler.beforeChange1();
        TexturePackChangeHandler.afterChange1();
    }

    @Test
    void beforeAvailableRegistrationAndReloadsDoNothing() {
        final ResourceList sentinel = Reflect.allocate(ResourceList.class);
        Reflect.setStatic(ResourceList.class, "instance", sentinel);
        final CountingHandler handler = new CountingHandler();

        TexturePackChangeHandler.register(handler);
        reload();
        TexturePackChangeHandler.beforeChange1();
        reload();
        TexturePackChangeHandler.afterChange1();

        assertTrue(TexturePackChangeHandler.isBootDeferred());
        assertEquals(0, handler.before);
        assertEquals(0, handler.after);
        assertEquals(0, handler.after2);
        assertEquals(0, recurseDepth());
        assertEquals(0, deferredDepth());
        assertSame(sentinel, Reflect.getStatic(ResourceList.class, "instance"));
        assertTrue(Reflect.<List<TexturePackChangeHandler>>getStatic(TexturePackChangeHandler.class, "handlers").contains(handler));
    }

    @Test
    void firstReloadAfterAvailableRunsDeferredHandlersOnce() {
        final CountingHandler handler = new CountingHandler();
        TexturePackChangeHandler.register(handler);
        setState(LoaderState.AVAILABLE);

        reload();

        assertEquals(1, handler.before);
        assertEquals(1, handler.after);
        assertEquals(1, handler.after2);
        assertEquals(0, recurseDepth());
        assertNull(Reflect.getStatic(ResourceList.class, "instance"));
    }

    @Test
    void availableLatchesThroughLaterStates() {
        setState(LoaderState.AVAILABLE);
        assertFalse(TexturePackChangeHandler.isBootDeferred());
        setState(LoaderState.ERRORED);
        assertFalse(TexturePackChangeHandler.isBootDeferred());
        final CountingHandler handler = new CountingHandler();
        reload();
        TexturePackChangeHandler.register(handler);
        reload();
        assertEquals(2, handler.before);
        assertEquals(2, handler.after);
    }

    @Test
    void registrationAfterAvailableInitializesImmediately() {
        setState(LoaderState.AVAILABLE);
        final CountingHandler handler = new CountingHandler();
        TexturePackChangeHandler.register(handler);
        assertEquals(1, handler.before);
        assertEquals(1, handler.after);
        assertEquals(0, handler.after2);
    }

    @Test
    void disabledFeaturesNeverDefer() {
        AngelicaConfig.enableMCPatcherForgeFeatures = false;
        final CountingHandler handler = new CountingHandler();
        TexturePackChangeHandler.register(handler);
        assertFalse(TexturePackChangeHandler.isBootDeferred());
        assertEquals(1, handler.before);
        assertEquals(1, handler.after);
    }

    @Test
    void bracketOpenedDeferredStaysDeferredAcrossAvailable() {
        final CountingHandler handler = new CountingHandler();
        TexturePackChangeHandler.register(handler);
        TexturePackChangeHandler.beforeChange1();
        setState(LoaderState.AVAILABLE);
        reload();
        TexturePackChangeHandler.afterChange1();

        assertEquals(0, handler.before);
        assertEquals(0, handler.after);
        assertEquals(0, deferredDepth());
        assertEquals(0, recurseDepth());

        reload();
        assertEquals(1, handler.before);
        assertEquals(1, handler.after);
        assertEquals(0, recurseDepth());
    }
}
