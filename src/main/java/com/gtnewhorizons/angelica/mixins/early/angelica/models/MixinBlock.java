package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ModeledBlock;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.gtnewhorizons.angelica.models.VanillaModels.*;

@Mixin(Block.class)
public abstract class MixinBlock implements ModeledBlock {

    @Getter
    @Setter
    private QuadProvider model = null;

    @Inject(method = "registerBlocks", at = @At(value = "TAIL"))
    private static void angelica$registerInjectedQP(CallbackInfo ci) {

        if (!AngelicaConfig.injectQPRendering)
            return;

        ((ModeledBlock) Blocks.stone).setModel(STONE);
        ((ModeledBlock) Blocks.glass).setModel(GLASS);
        ((ModeledBlock) Blocks.crafting_table).setModel(WORKBENCH);
        ((ModeledBlock) Blocks.leaves).setModel(OLD_LEAF);
        ((ModeledBlock) Blocks.leaves2).setModel(NEW_LEAF);
        ((ModeledBlock) Blocks.log).setModel(OLD_LOG);
        ((ModeledBlock) Blocks.log2).setModel(NEW_LOG);
    }
}
