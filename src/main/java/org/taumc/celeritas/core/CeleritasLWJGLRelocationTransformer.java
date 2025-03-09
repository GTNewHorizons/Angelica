package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.commons.ClassRemapper;
import org.spongepowered.asm.lib.commons.Remapper;
import org.taumc.celeritas.mixin.CeleritasArchaicMixinPlugin;

import java.util.regex.Pattern;

public class CeleritasLWJGLRelocationTransformer implements IClassTransformer {
    private static final Remapper LWJGL_REMAPPER = new LwjglRemapper();
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass != null && (transformedName.startsWith("org.embeddedt.embeddium") || transformedName.startsWith("org.taumc.celeritas"))) {
            try {
                var reader = new ClassReader(basicClass);
                var writer = new ClassWriter(0);
                var remapper = new ClassRemapper(writer, LWJGL_REMAPPER);
                reader.accept(remapper, 0);
                return writer.toByteArray();
            } catch(Exception e) {
                CeleritasArchaicMixinPlugin.LOGGER.error("Exception remapping class", e);
                return basicClass;
            }
        }
        return basicClass;
    }

    private static class LwjglRemapper extends Remapper {
        private static final Pattern LWJGL3 = Pattern.compile("^org/lwjgl/");
        @Override
        public String map(String internalName) {
            if(internalName.startsWith("org/lwjgl/")) {
                return LWJGL3.matcher(internalName).replaceFirst("org/lwjgl3/");
            } else {
                return internalName;
            }
        }
    }
}
