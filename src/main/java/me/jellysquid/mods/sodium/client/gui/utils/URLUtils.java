package me.jellysquid.mods.sodium.client.gui.utils;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.util.Util;

import java.io.IOException;

public class URLUtils {

    private static String[] getURLOpenCommand(String url) {
        return switch (Util.getOSType()) {
            case WINDOWS -> new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
            case OSX -> new String[]{"open", url};
            case UNKNOWN, LINUX, SOLARIS -> new String[]{"xdg-open", url};
        };
    }

    public static void open(String url) {
        try {
            Runtime.getRuntime().exec(getURLOpenCommand(url));
        } catch (IOException exception) {
            SodiumClientMod.logger().error("Couldn't open url '{}'", url, exception);
        }

    }

}
