package me.jellysquid.mods.sodium.mixin.features.model;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.WeightedBakedModel;
import net.minecraft.util.math.Direction;
import net.minecraftforge.client.model.data.IModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;
import java.util.Random;

@Mixin(WeightedBakedModel.class)
public class MixinWeightedBakedModel {
    @Shadow
    @Final
    private List<WeightedBakedModel.Entry> models;

    @Shadow
    @Final
    private int totalWeight;

    /**
     * @author JellySquid
     * @reason Avoid excessive object allocations
     */
    @Overwrite
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random, IModelData modelData) {
    	WeightedBakedModel.Entry entry = getAt(this.models, Math.abs((int) random.nextLong()) % this.totalWeight);

        if (entry != null) {
            return entry.model.getQuads(state, face, random, modelData);
        }

        return Collections.emptyList();
    }

    private static <T extends WeightedBakedModel.Entry> T getAt(List<T> pool, int totalWeight) {
        int i = 0;
        int len = pool.size();

        T weighted;

        do {
            if (i >= len) {
                return null;
            }

            weighted = pool.get(i++);
            totalWeight -= weighted.weight;
        } while (totalWeight >= 0);

        return weighted;
    }
}
