package com.gtnewhorizons.angelica.mixins.late.client.journeymap;

import journeymap.client.render.map.TileDrawStep;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileDrawStep.class)
public class MixinTileDrawStep {
    @Redirect(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Ljourneymap/client/render/map/TileDrawStep;debug:Z", remap = false))
    private boolean getDebug(TileDrawStep instance) {
        return false;
    }
}
