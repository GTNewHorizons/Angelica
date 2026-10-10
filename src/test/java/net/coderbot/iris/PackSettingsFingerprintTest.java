package net.coderbot.iris;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.shaderpack.StringPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PackSettingsFingerprintTest {
    @TempDir Path dir;

    private static Object describe(String name, Path pack, Properties properties, Iterable<StringPair> defines, boolean byContent) {
        return Reflect.invokeStatic(Iris.class, "describeSettings", new Class<?>[] { String.class, Path.class, Properties.class, Iterable.class, boolean.class },
            name, pack, properties, defines, byContent);
    }

    @Test
    void effectiveFallbackVersionInvalidatesPreparedAndCompiledPackSettings() throws Exception {
        final Path pack = Files.write(dir.resolve("pack.zip"), new byte[] {1});
        final int previous = AngelicaConfig.modernFallbackMcVersion;
        try {
            for (boolean byContent : new boolean[] {false, true}) {
                AngelicaConfig.modernFallbackMcVersion = 0;
                final Object defaults = describe("pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent);
                AngelicaConfig.modernFallbackMcVersion = 260101;
                assertEquals(defaults, describe("pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent));
                AngelicaConfig.modernFallbackMcVersion = 12001;
                assertNotEquals(defaults, describe("pack", pack, new Properties(), List.of(new StringPair("MC_VERSION", "10710")), byContent));
            }
        } finally {
            AngelicaConfig.modernFallbackMcVersion = previous;
        }
    }
}
