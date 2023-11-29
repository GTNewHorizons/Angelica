package com.gtnewhorizons.angelica.mixins.early.notfine.glint;

import jss.notfine.core.Settings;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderItem.class)
public abstract class MixinRenderItem {

	/**
	 * @author jss2a98aj
	 * @reason Makes renderGlint faster and fixes glBlendFunc being left with the wrong values.
	 */
	@Overwrite
    private void renderGlint(int unused, int posX, int posY, int width, int height) {
        if(!(boolean)Settings.MODE_GLINT_INV.option.getStore()) {
            return;
        }
        final float timeUVSpeed = 0.00390625F;
        final Tessellator tessellator = Tessellator.instance;
        final long time = Minecraft.getSystemTime();

        float layerUVNoise = 4.0F;

        OpenGlHelper.glBlendFunc(772, 1, 0, 1);

        //for(int layer = 0; layer < 2; ++layer) {
        	final int timeUVDenominator = 3000 /*+ layer * 1873*/;
            final float timeUVNoise = (float)(time % (long)timeUVDenominator) / (float)timeUVDenominator * 256F;

            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(
            	posX, (posY + height), zLevel,
            	((timeUVNoise + (float)height * layerUVNoise) * timeUVSpeed), ((float)height * timeUVSpeed)
            );
            tessellator.addVertexWithUV(
            	(posX + width), (posY + height), zLevel,
            	((timeUVNoise + (float)width + (float)height * layerUVNoise) * timeUVSpeed), ((float)height * timeUVSpeed)
            );
            tessellator.addVertexWithUV(
            	(posX + width), posY, zLevel,
            	((timeUVNoise + (float)width) * timeUVSpeed), 0D
            );
            tessellator.addVertexWithUV(
            	posX, posY, zLevel,
            	(timeUVNoise * timeUVSpeed), 0D
            );
            tessellator.draw();

            //layerUVNoise = -1.0F;
        //}

        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
    }

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"
        ),
        remap = false
    )
    private boolean notFine$toggleGlint(ItemStack stack, int pass) {
        return stack.hasEffect(pass) && (boolean)Settings.MODE_GLINT_WORLD.option.getStore();
    }

    @Shadow public float zLevel;

}
