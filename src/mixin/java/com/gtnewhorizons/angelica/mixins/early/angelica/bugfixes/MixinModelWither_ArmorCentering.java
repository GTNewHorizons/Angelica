package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.WitherArmorState;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelWither;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creates a centered armor model for the Wither.
 *
 * Vanilla 1.7.10 uses glScalef(1.1) on the base model for the armor overlay, which
 * shifts off-center and rotating parts away from their correct positions. Modern Minecraft
 * (1.21+) replaced this with a separate model using CubeDeformation(0.5).
 * This mixin backports that approach.
 */
@Mixin(ModelWither.class)
public class MixinModelWither_ArmorCentering {

    // Contains body segments: [0] shoulder, [1] upper body + ribs, [2] tail
    @Shadow private ModelRenderer[] field_82905_a;

    // Heads: [0] center, [1] left, [2] right
    @Shadow private ModelRenderer[] field_82904_b;

    @Unique
    private static final float ARMOR_INFLATE = 0.5F;

    // Ribs armor size is reduced by 2^22 ULPs to sit just inside the base spine, preventing z-fighting.
    @Unique
    private static final float NO_RIBS_Z_INFLATE = Float.intBitsToFloat(Float.floatToIntBits(ARMOR_INFLATE) - 4194304);

    // Side head armor is inflated by 2^22 ULPs to make the armor sit above the base shoulder, preventing z-fighting.
    @Unique
    private static final float NO_HEADS_Z_INFLATE = Float.intBitsToFloat(Float.floatToIntBits(ARMOR_INFLATE) + 4194304);

    // If the armor inflate flag is set, rebuild all boxes with uniform inflate.
    @Inject(method = "<init>", at = @At("TAIL"))
    private void angelica$applyInflate(CallbackInfo ci) {
        if (!WitherArmorState.pendingInflate) return;
        WitherArmorState.pendingInflate = false;

        ModelWither self = (ModelWither) (Object) this;

        // [0] Shoulder
        field_82905_a[0] = new ModelRenderer(self, 0, 16);
        field_82905_a[0].addBox(-10.0F, 3.9F, -0.5F, 20, 3, 3, ARMOR_INFLATE);

        // [1] Upper body (spine + ribs things)
        field_82905_a[1] = (new ModelRenderer(self)).setTextureSize(self.textureWidth, self.textureHeight);
        field_82905_a[1].setRotationPoint(-2.0F, 6.9F, -0.5F);
        field_82905_a[1].setTextureOffset(0, 22).addBox(0.0F, 0.0F, 0.0F, 3, 10, 3, ARMOR_INFLATE);
        field_82905_a[1].setTextureOffset(24, 22).addBox(-4.0F, 1.5F, 0.5F, 11, 2, 2, NO_RIBS_Z_INFLATE);
        field_82905_a[1].setTextureOffset(24, 22).addBox(-4.0F, 4.0F, 0.5F, 11, 2, 2, NO_RIBS_Z_INFLATE);
        field_82905_a[1].setTextureOffset(24, 22).addBox(-4.0F, 6.5F, 0.5F, 11, 2, 2, NO_RIBS_Z_INFLATE);

        // [2] Tail
        field_82905_a[2] = new ModelRenderer(self, 12, 22);
        field_82905_a[2].addBox(0.0F, 0.0F, 0.0F, 3, 6, 3, ARMOR_INFLATE);

        // [0] Center head
        field_82904_b[0] = new ModelRenderer(self, 0, 0);
        field_82904_b[0].addBox(-4.0F, -4.0F, -4.0F, 8, 8, 8, ARMOR_INFLATE);

        // [1] Left head
        field_82904_b[1] = new ModelRenderer(self, 32, 0);
        field_82904_b[1].addBox(-4.0F, -4.0F, -4.0F, 6, 6, 6, NO_HEADS_Z_INFLATE);
        field_82904_b[1].rotationPointX = -8.0F;
        field_82904_b[1].rotationPointY = 4.0F;

        // [2] Right head
        field_82904_b[2] = new ModelRenderer(self, 32, 0);
        field_82904_b[2].addBox(-4.0F, -4.0F, -4.0F, 6, 6, 6, NO_HEADS_Z_INFLATE);
        field_82904_b[2].rotationPointX = 10.0F;
        field_82904_b[2].rotationPointY = 4.0F;
    }
}
