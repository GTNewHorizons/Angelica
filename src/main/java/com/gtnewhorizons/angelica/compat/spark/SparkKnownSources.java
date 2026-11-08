package com.gtnewhorizons.angelica.compat.spark;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SparkKnownSources {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final String PLUGIN = "me/lucko/spark/forge/plugin/Forge1710SparkPlugin";
    private static final String SOURCE_METADATA = "me.lucko.spark.common.sampler.source.SourceMetadata";

    private static MethodHandle constructor;
    private static boolean failed;

    private SparkKnownSources() {}

    public static boolean mixinApplies() {
        try (InputStream stream = SparkKnownSources.class.getResourceAsStream("/" + PLUGIN + ".class")) {
            if (stream == null) {
                return false;
            }
            final boolean[] declared = new boolean[1];
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if ("getKnownSources".equals(name) && "()Ljava/util/Collection;".equals(desc)) {
                        declared[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (declared[0]) {
                LOGGER.info("spark already reports mod sources; skipping");
                return false;
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Could not inspect spark plugin class; not adding mod sources", t);
            return false;
        }
    }

    public static Collection<Object> gather(ClassLoader loader) {
        if (failed) {
            return Collections.emptyList();
        }
        try {
            if (constructor == null) constructor = MethodHandles.publicLookup().findConstructor(Class.forName(SOURCE_METADATA, false, loader),
                MethodType.methodType(void.class, String.class, String.class, String.class, String.class));
            final List<ModContainer> mods = Loader.instance().getActiveModList();
            final List<Object> sources = new ArrayList<>(mods.size());
            for (int i = 0; i < mods.size(); i++) {
                final ModContainer mod = mods.get(i);
                sources.add(constructor.invoke(mod.getModId(), mod.getVersion(), (String) null, (String) null));
            }
            return sources;
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("Could not build spark mod sources; profiles will omit mod versions", t);
            return Collections.emptyList();
        }
    }
}
