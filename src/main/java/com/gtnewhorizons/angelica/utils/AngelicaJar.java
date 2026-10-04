package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AngelicaJar {
    private AngelicaJar() {}

    /** The jar or classes directory Angelica was loaded from, or null if something really went wrong. */
    @Nullable
    public static Path location() {
        try {
            String location = AngelicaJar.class.getProtectionDomain().getCodeSource().getLocation().toString();
            if (location.startsWith("jar:")) {
                final int separator = location.indexOf("!/");
                location = location.substring("jar:".length(), separator < 0 ? location.length() : separator);
            }
            return Paths.get(new URI(location));
        } catch (Exception e) {
            return null;
        }
    }

    public static void configureShaderDiskCache(@Nullable File jar) {
        if (jar == null || !jar.isFile()) return;
        ShaderDiskCache.configure(Minecraft.getMinecraft().mcDataDir.toPath().resolve("angelica").resolve("shadercache"),
            Tags.VERSION + "|" + jar.length() + "|" + jar.lastModified());
    }
}
