package org.embeddedt.archaicfix.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.embeddedt.archaicfix.asm.transformer.VampirismTransformer;

import java.util.*;

public class ArchaicTransformer implements IClassTransformer {

    private static final List<IClassTransformer> transformers = Arrays.asList(
            //new ThreadedBlockTransformer(),
            new VampirismTransformer()
    );

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        for(IClassTransformer transformer : transformers) {
            basicClass = transformer.transform(name, transformedName, basicClass);
        }
        return basicClass;
    }
}
