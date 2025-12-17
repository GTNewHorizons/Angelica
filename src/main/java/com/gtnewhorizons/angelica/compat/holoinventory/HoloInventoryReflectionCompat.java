package com.gtnewhorizons.angelica.compat.holoinventory;

import net.minecraftforge.client.event.RenderGameOverlayEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import static com.gtnewhorizons.angelica.compat.ModStatus.LOGGER;

public class HoloInventoryReflectionCompat {
    private static final String RENDERER_CLASS = "net.dries007.holoInventory.client.Renderer";

    private static boolean isLoaded;
    private static final MethodHandle renderEventPostMethod;
    private static final MethodHandle renderEventMethod;
    private static final MethodHandle angelicaOverrideSetter;

    static {
        MethodHandle renderEventPostMethodTemp = null;
        MethodHandle renderEventMethodTemp = null;
        MethodHandle angelicaOverrideSetterTemp = null;
        final Object rendererInstance;
        final Class<?> rendererClass;

        try {
            rendererClass = Class.forName(RENDERER_CLASS);

            // Get and cache the Renderer.INSTANCE
            final Field instanceField = rendererClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            rendererInstance = instanceField.get(null);

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                renderEventPostMethodTemp = lookup.findVirtual(rendererClass, "renderEvent", MethodType.methodType(void.class, RenderGameOverlayEvent.Post.class));
            } catch (NoSuchMethodException e) {
                // Fallback for 2.5.0-GTNH and earlier
                renderEventMethodTemp = lookup.findVirtual(rendererClass, "renderEvent", MethodType.methodType(void.class, RenderGameOverlayEvent.class));
            }

            final Field angelicaOverrideField = rendererClass.getDeclaredField("angelicaOverride");
            angelicaOverrideField.setAccessible(true);
            angelicaOverrideSetterTemp = lookup.unreflectSetter(angelicaOverrideField);

            // Bind the method handles to the instance
            if (rendererInstance != null) {
                if (renderEventPostMethodTemp != null) {
                    renderEventPostMethodTemp = renderEventPostMethodTemp.bindTo(rendererInstance);
                }
                if (renderEventMethodTemp != null) {
                    renderEventMethodTemp = renderEventMethodTemp.bindTo(rendererInstance);
                }
                angelicaOverrideSetterTemp = angelicaOverrideSetterTemp.bindTo(rendererInstance);
            }

            isLoaded = true;
            LOGGER.info("Successfully initialized HoloInventory compatibility layer");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize HoloInventory compatibility layer", e);
            isLoaded = false;
        }

        renderEventPostMethod = renderEventPostMethodTemp;
        renderEventMethod = renderEventMethodTemp;
        angelicaOverrideSetter = angelicaOverrideSetterTemp;
    }

    public static void renderEvent(RenderGameOverlayEvent event) {
        if (!isLoaded) return;
        try {
            if (event instanceof RenderGameOverlayEvent.Post && renderEventPostMethod != null) {
                renderEventPostMethod.invokeExact((RenderGameOverlayEvent.Post) event);
            } else {
                renderEventMethod.invokeExact(event);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to invoke HoloInventory renderEvent", e);
            isLoaded = false;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void setAngelicaOverride(boolean value) {
        if (!isLoaded) return;
        try {
            angelicaOverrideSetter.invokeExact(value);
        } catch (Exception e) {
            LOGGER.error("Failed to set HoloInventory angelicaOverride", e);
            isLoaded = false;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
