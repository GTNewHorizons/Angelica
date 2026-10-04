package net.coderbot.iris;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.shaderpack.StringPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PackSettingsFingerprintTest {
    @TempDir Path dir;

    @Test
    void effectiveFallbackVersionInvalidatesPreparedAndCompiledPackSettings() throws Exception {
        final Path pack = Files.write(dir.resolve("pack.zip"), new byte[] {1});
        final Method describe = Iris.class.getDeclaredMethod("describeSettings", String.class, Path.class,
            Properties.class, Iterable.class, boolean.class);
        describe.setAccessible(true);
        final int previous = AngelicaConfig.modernFallbackMcVersion;
        try {
            for (boolean byContent : new boolean[] {false, true}) {
                AngelicaConfig.modernFallbackMcVersion = 0;
                final Object defaults = describe.invoke(null, "pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent);
                AngelicaConfig.modernFallbackMcVersion = 260101;
                assertEquals(defaults, describe.invoke(null, "pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent));
                AngelicaConfig.modernFallbackMcVersion = 12001;
                assertNotEquals(defaults, describe.invoke(null, "pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent));
            }
        } finally {
            AngelicaConfig.modernFallbackMcVersion = previous;
        }
    }
}
