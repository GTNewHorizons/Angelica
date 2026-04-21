package com.gtnewhorizons.angelica.mixins.early.dragonapi;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Pseudo
@Mixin(targets = { "Reika/DragonAPI/IO/Shaders/ShaderRegistry" }, remap = false)
public class MixinShaderRegistry_ParseError {

    @Redirect(method = "parseError",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glGetShaderInfoLog(II)Ljava/lang/String;"))
    private static String angelica$routeInfoLogByType(int id, int maxLen) {
        return GLStateManager.glIsProgram(id) ? GLStateManager.glGetProgramInfoLog(id, maxLen) : GLStateManager.glGetShaderInfoLog(id, maxLen);
    }
}
