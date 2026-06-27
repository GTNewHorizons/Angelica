package com.gtnewhorizons.angelica.compat.etfuturum;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.ReflectionHelper;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.gtnewhorizons.angelica.compat.ModStatus.LOGGER;

public final class EtFuturumCompat {
    private static final String IELYTRA_PLAYER = "ganymedes01.etfuturum.api.elytra.IElytraPlayer";

    @Getter
    private static boolean loaded;
    private static Class<?> elytraPlayerClass;
    private static final MethodHandle isElytraFlyingHandle;

    static {
        MethodHandle handle = null;
        try {
            if (Loader.isModLoaded("etfuturum")) {
                elytraPlayerClass = ReflectionHelper.getClass(EtFuturumCompat.class.getClassLoader(), IELYTRA_PLAYER);
                handle = MethodHandles.lookup().findVirtual(elytraPlayerClass, "etfu$isElytraFlying",
                    MethodType.methodType(boolean.class));
                loaded = true;
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to load Et Futurum Requiem elytra compat", t);
            loaded = false;
        }
        isElytraFlyingHandle = handle;
    }

    private EtFuturumCompat() {
    }

    public static boolean isElytraFlying(EntityPlayer player) {
        if (!loaded || isElytraFlyingHandle == null || !elytraPlayerClass.isInstance(player)) {
            return false;
        }
        try {
            return (boolean) isElytraFlyingHandle.invoke(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
