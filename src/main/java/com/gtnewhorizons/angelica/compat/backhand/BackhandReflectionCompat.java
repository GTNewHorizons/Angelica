package com.gtnewhorizons.angelica.compat.backhand;

import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.gtnewhorizons.angelica.compat.ModStatus.LOGGER;

public class BackhandReflectionCompat {
    private static final String BACKHAND_CLASS = "xonin.backhand.Backhand";
    private static final String ITEM_RENDERER_HOOKS_CLASS = "xonin.backhand.client.hooks.ItemRendererHooks";
    private static final String BACKHAND_UTILS_CLASS = "xonin.backhand.api.core.BackhandUtils";

    private static boolean isLoaded;
    private static final MethodHandle renderOffhandReturn;
    private static final MethodHandle getOffhandItem;

    static {
        MethodHandle renderOffhandReturnTemp = null;
        MethodHandle getOffhandItemTemp = null;

        try {
            final Class<?> backhandClass = ReflectionHelper.getClass(BackhandReflectionCompat.class.getClassLoader(), BACKHAND_CLASS);
            final boolean versionCheck = new DefaultArtifactVersion("1.6.9").compareTo(LoadControllerHelper.getOwningMod(backhandClass).getProcessedVersion()) <= 0;

            if (versionCheck) {
                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                final Class<?> itemRendererHooksClass = ReflectionHelper.getClass(BackhandReflectionCompat.class.getClassLoader(), ITEM_RENDERER_HOOKS_CLASS);
                final Class<?> backhandUtilsClass = ReflectionHelper.getClass(BackhandReflectionCompat.class.getClassLoader(), BACKHAND_UTILS_CLASS);

                renderOffhandReturnTemp = lookup.findStatic(itemRendererHooksClass, "renderOffhandReturn", MethodType.methodType(void.class, float.class));
                getOffhandItemTemp = lookup.findStatic(backhandUtilsClass, "getOffhandItem", MethodType.methodType(ItemStack.class, EntityPlayer.class));
                isLoaded = true;
                LOGGER.info("Backhand compat loaded");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Backhand compat", e);
            isLoaded = false;
        }

        renderOffhandReturn = renderOffhandReturnTemp;
        getOffhandItem = getOffhandItemTemp;
    }

    public static boolean isBackhandLoaded() {
        return isLoaded;
    }

    public static void renderOffhand(float partialTicks) {
        if (isLoaded && renderOffhandReturn != null) {
            try {
                renderOffhandReturn.invokeExact(partialTicks);
            } catch (Exception e) {
                LOGGER.error("Failed to invoke Backhand renderOffhandReturn", e);
                isLoaded = false;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ItemStack getOffhandItem(EntityPlayer player) {
        if (isLoaded && getOffhandItem != null) {
            try {
                return (ItemStack) getOffhandItem.invokeExact(player);
            } catch (Exception e) {
                LOGGER.error("Failed to invoke Backhand getOffhandItem", e);
                isLoaded = false;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
