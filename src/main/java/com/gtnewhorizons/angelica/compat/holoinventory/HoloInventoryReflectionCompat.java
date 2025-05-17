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
    private static final MethodHandle renderEventMethod;
    private static final MethodHandle angelicaOverrideSetter;
    private static final Object rendererInstance;

    static {
        MethodHandle renderEventMethodTemp = null;
        MethodHandle angelicaOverrideSetterTemp = null;
        Object rendererInstanceTemp = null;

        try {
            final Class<?> rendererClass = Class.forName(RENDERER_CLASS);

            // Get and cache the Renderer.INSTANCE
            final Field instanceField = rendererClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            rendererInstanceTemp = instanceField.get(null);

            // Get the renderEvent method (RenderGameOverlayEvent base class)
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                renderEventMethodTemp = lookup.findVirtual(rendererClass, "renderEvent", MethodType.methodType(void.class, RenderGameOverlayEvent.Post.class));
            } catch (NoSuchMethodException e) {
                // Fallback for 2.5.0-GTNH and earlier
                renderEventMethodTemp = lookup.findVirtual(rendererClass, "renderEvent", MethodType.methodType(void.class, RenderGameOverlayEvent.class));
            }

            final Field angelicaOverrideField = rendererClass.getDeclaredField("angelicaOverride");
            angelicaOverrideField.setAccessible(true);
            angelicaOverrideSetterTemp = lookup.unreflectSetter(angelicaOverrideField);

            isLoaded = true;
            LOGGER.info("Successfully initialized HoloInventory compatibility layer");
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize HoloInventory compatibility layer", e);
            isLoaded = false;
        }

        renderEventMethod = renderEventMethodTemp;
        angelicaOverrideSetter = angelicaOverrideSetterTemp;
        rendererInstance = rendererInstanceTemp;
    }

    public static boolean isHoloInventoryLoaded() {
        return isLoaded;
    }

    public static void renderEvent(RenderGameOverlayEvent event) {
        if (!isLoaded) return;
        try {
            renderEventMethod.invoke(rendererInstance, event);
        } catch (Throwable e) {
            LOGGER.error("Failed to invoke HoloInventory renderEvent", e);
            isLoaded = false;
        }
    }

    public static void setAngelicaOverride(boolean value) {
        if (!isLoaded) return;
        try {
            angelicaOverrideSetter.invoke(rendererInstance, value);
        } catch (Throwable e) {
            LOGGER.error("Failed to set HoloInventory angelicaOverride", e);
            isLoaded = false;
        }
    }
}
