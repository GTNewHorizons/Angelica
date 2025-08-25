package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizon.gtnhlib.client.model.BakedModel;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ModeledBlock;
import com.gtnewhorizons.angelica.models.VanillaModels;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Block.class)
public abstract class MixinBlock implements ModeledBlock {

    @Getter
    @Setter
    private BakedModel model = null;

    @Inject(method = "registerBlocks", at = @At(value = "TAIL"))
    private static void angelica$registerInjectedQP(CallbackInfo ci) {

        if (!AngelicaConfig.injectQPRendering)
            return;

        ((ModeledBlock) Blocks.stone).setModel(VanillaModels.STONE);
        ((ModeledBlock) Blocks.glass).setModel(VanillaModels.GLASS);
        ((ModeledBlock) Blocks.crafting_table).setModel(VanillaModels.WORKBENCH);
        ((ModeledBlock) Blocks.leaves).setModel(VanillaModels.OLD_LEAF);
        ((ModeledBlock) Blocks.leaves2).setModel(VanillaModels.NEW_LEAF);
        //((ModeledBlock) Blocks.log).setModel(VanillaModels.OLD_LOG);
        //((ModeledBlock) Blocks.log2).setModel(VanillaModels.NEW_LOG);
    }
}
