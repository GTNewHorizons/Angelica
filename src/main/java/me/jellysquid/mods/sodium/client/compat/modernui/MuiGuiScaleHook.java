package me.jellysquid.mods.sodium.client.compat.modernui;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;
import java.util.stream.Stream;

/**
 * Ugly hack to get around Modern UI overwriting calculateScaleFactor and not conforming to vanilla standards
 * by returning the max size when scale = 0.
 */
public class MuiGuiScaleHook {
    private static final Method calcGuiScalesMethod;
    static {
        calcGuiScalesMethod = Stream.of("icyllis.modernui.forge.MForgeCompat", "icyllis.modernui.forge.MuiForgeApi").flatMap(clzName -> {
            try {
                return Stream.of(Class.forName(clzName));
            } catch(Throwable e) {
                return Stream.of();
            }
        }).flatMap(clz -> {
            try {
                Method m = clz.getDeclaredMethod("calcGuiScales");
                m.setAccessible(true);
                return Stream.of(m);
            } catch(Throwable e) {
                return Stream.of();
            }
        }).findFirst().orElse(null);
        if(calcGuiScalesMethod != null)
            SodiumClientMod.logger().info("Found ModernUI GUI scale hook");
    }
    public static int getMaxGuiScale() {
        if(calcGuiScalesMethod != null) {
            try {
                return ((int)calcGuiScalesMethod.invoke(null)) & 0xf;
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
        // default vanilla logic
        return MinecraftClient.getInstance().getWindow().calculateScaleFactor(0, MinecraftClient.getInstance().forcesUnicodeFont());
    }
}
