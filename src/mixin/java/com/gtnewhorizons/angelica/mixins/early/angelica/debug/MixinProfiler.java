package com.gtnewhorizons.angelica.mixins.early.angelica.debug;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Profiler.class)
public class MixinProfiler {

    @Unique private static final Reference2ObjectOpenHashMap<String, String> angelica$groupNames = new Reference2ObjectOpenHashMap<>();
    @Unique private static final int ANGELICA$GROUP_NAMES_CAP = 2048;

    @Inject(method = "startSection(Ljava/lang/String;)V", at = @At("HEAD"))
    private void debugStartSection(String name, CallbackInfo ci) {
        if (!GLDebug.isActive()) return;
        String group = angelica$groupNames.get(name);
        if (group == null) {
            group = "minecraft:" + name;
            if (angelica$groupNames.size() > ANGELICA$GROUP_NAMES_CAP) angelica$groupNames.clear();
            angelica$groupNames.put(name, group);
        }
        GLDebug.pushGroup(group);
    }

    @Inject(method = "endSection()V", at = @At("HEAD"))
    private void debugEndSection(CallbackInfo ci) {
        GLDebug.popGroup();
    }
}
