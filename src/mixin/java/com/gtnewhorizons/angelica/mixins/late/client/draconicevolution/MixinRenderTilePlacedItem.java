package com.gtnewhorizons.angelica.mixins.late.client.draconicevolution;

import com.brandon3055.draconicevolution.client.render.tile.RenderTilePlacedItem;
import com.brandon3055.draconicevolution.common.tileentities.TilePlacedItem;
import com.gtnewhorizons.angelica.compat.draconicevolution.PlacedItemRenderCompat;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderTilePlacedItem.class, remap = false)
public abstract class MixinRenderTilePlacedItem {
    @Unique
    private byte angelica$ordinaryItemBatchState = BatchEligibility.UNKNOWN;

    @Shadow(remap = false)
    public abstract void renderItem(TilePlacedItem tile);

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"), cancellable = true, remap = true)
    private void angelica$renderPlacedItem(TileEntity te, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        if (!(te instanceof TilePlacedItem tile) || tile.getWorldObj() == null) return;
        final ItemStack stack = tile.getStack();
        if (stack == null) return;
        final boolean custom = PlacedItemRenderCompat.requiresLiveRender(stack);
        if (!custom && !ModelPartBatcher.INSTANCE.isActive()) return;

        final byte next = PlacedItemRenderCompat.renderWithBatchState(x, y, z,
            custom ? BatchEligibility.DENIED : angelica$ordinaryItemBatchState, () -> renderItem(tile));
        if (!custom) angelica$ordinaryItemBatchState = next;
        ci.cancel();
    }
}
