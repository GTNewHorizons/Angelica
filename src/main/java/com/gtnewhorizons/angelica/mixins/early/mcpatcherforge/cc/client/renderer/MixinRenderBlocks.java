package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.prupe.mcpatcher.cc.ColorizeBlock;
import com.prupe.mcpatcher.cc.Colorizer;
import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;
import com.prupe.mcpatcher.renderpass.RenderPass;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;

    @Shadow
    public boolean enableAO;

    @Shadow
    public float colorRedTopLeft;
    @Shadow
    public float colorRedBottomLeft;
    @Shadow
    public float colorRedBottomRight;
    @Shadow
    public float colorRedTopRight;
    @Shadow
    public float colorGreenTopLeft;
    @Shadow
    public float colorGreenBottomLeft;
    @Shadow
    public float colorGreenBottomRight;
    @Shadow
    public float colorGreenTopRight;
    @Shadow
    public float colorBlueTopLeft;
    @Shadow
    public float colorBlueBottomLeft;
    @Shadow
    public float colorBlueBottomRight;
    @Shadow
    public float colorBlueTopRight;

    @Shadow
    public abstract boolean hasOverrideBlockTexture();

    // Compute values once and reuse later

    @Unique
    private boolean mcpatcherforge$computeRedstoneWireColor;

    @Unique
    private float mcpatcherforge$redstoneWireColorRed;

    @Unique
    private float mcpatcherforge$redstoneWireColorGreen;

    @Unique
    private float mcpatcherforge$redstoneWireColorBlue;

    @Unique
    private void mcpatcherforge$setColorAndVertex(Tessellator tessellator, float red, float green, float blue, double x,
        double y, double z, double u, double v) {
        if (ColorizeBlock.isSmooth) {
            tessellator.setColorOpaque_F(red, green, blue);
        }
        tessellator.addVertexWithUV(x, y, z, u, v);
    }

    @Inject(
        method = "renderBlockCauldron(Lnet/minecraft/block/BlockCauldron;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockCauldron;getBlockTextureFromSide(I)Lnet/minecraft/util/IIcon;",
            shift = At.Shift.AFTER))
    private void modifyRenderBlockCauldron1(BlockCauldron block, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        ColorizeBlock.computeWaterColor();
        Tessellator.instance.setColorOpaque_F(Colorizer.setColor[0], Colorizer.setColor[1], Colorizer.setColor[2]);
    }

    @Inject(
        method = "renderBlockCauldron(Lnet/minecraft/block/BlockCauldron;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/BlockLiquid;getLiquidIcon(Ljava/lang/String;)Lnet/minecraft/util/IIcon;"))
    private void modifyRenderBlockCauldron2(BlockCauldron block, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        ColorizeBlock.computeWaterColor();
        Tessellator.instance.setColorOpaque_F(Colorizer.setColor[0], Colorizer.setColor[1], Colorizer.setColor[2]);
    }

    @Inject(method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z", at = @At("HEAD"))
    private void calculateComputeRedstoneWireColor(Block block, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        this.mcpatcherforge$computeRedstoneWireColor = ColorizeBlock
            .computeRedstoneWireColor(this.blockAccess.getBlockMetadata(x, y, z));
        this.mcpatcherforge$redstoneWireColorRed = Math.max(Colorizer.setColor[0], 0.0f);
        this.mcpatcherforge$redstoneWireColorGreen = Math.max(Colorizer.setColor[1], 0.0f);
        this.mcpatcherforge$redstoneWireColorBlue = Math.max(Colorizer.setColor[2], 0.0f);
    }

    @ModifyArgs(
        method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 0))
    private void modifyColorRedstoneWire1(Args args) {
        if (this.mcpatcherforge$computeRedstoneWireColor) {
            args.set(0, this.mcpatcherforge$redstoneWireColorRed);
            args.set(1, this.mcpatcherforge$redstoneWireColorGreen);
            args.set(2, this.mcpatcherforge$redstoneWireColorBlue);
        }
    }

    @ModifyArgs(
        method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 4))
    private void modifyColorRedstoneWire2(Args args) {
        if (this.mcpatcherforge$computeRedstoneWireColor) {
            args.set(0, this.mcpatcherforge$redstoneWireColorRed);
            args.set(1, this.mcpatcherforge$redstoneWireColorGreen);
            args.set(2, this.mcpatcherforge$redstoneWireColorBlue);
        }
    }

    @ModifyArgs(
        method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 6))
    private void modifyColorRedstoneWire3(Args args) {
        if (this.mcpatcherforge$computeRedstoneWireColor) {
            args.set(0, this.mcpatcherforge$redstoneWireColorRed);
            args.set(1, this.mcpatcherforge$redstoneWireColorGreen);
            args.set(2, this.mcpatcherforge$redstoneWireColorBlue);
        }
    }

    @ModifyArgs(
        method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 8))
    private void modifyColorRedstoneWire4(Args args) {
        if (this.mcpatcherforge$computeRedstoneWireColor) {
            args.set(0, this.mcpatcherforge$redstoneWireColorRed);
            args.set(1, this.mcpatcherforge$redstoneWireColorGreen);
            args.set(2, this.mcpatcherforge$redstoneWireColorBlue);
        }
    }

    @ModifyArgs(
        method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 10))
    private void modifyColorRedstoneWire5(Args args) {
        if (this.mcpatcherforge$computeRedstoneWireColor) {
            args.set(0, this.mcpatcherforge$redstoneWireColorRed);
            args.set(1, this.mcpatcherforge$redstoneWireColorGreen);
            args.set(2, this.mcpatcherforge$redstoneWireColorBlue);
        }
    }

    @Inject(
        method = "renderStandardBlock(Lnet/minecraft/block/Block;III)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isAmbientOcclusionEnabled()Z"))
    private void modifyRenderStandardBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {

        // TODO: capture local variables to prevent double math
        int l = block.colorMultiplier(this.blockAccess, x, y, z);
        float f = (float) (l >> 16 & 255) / 255.0F;
        float f1 = (float) (l >> 8 & 255) / 255.0F;
        float f2 = (float) (l & 255) / 255.0F;

        if (EntityRenderer.anaglyphEnable) {
            float f3 = (f * 30.0F + f1 * 59.0F + f2 * 11.0F) / 100.0F;
            float f4 = (f * 30.0F + f1 * 70.0F) / 100.0F;
            float f5 = (f * 30.0F + f2 * 70.0F) / 100.0F;
            f = f3;
            f1 = f4;
            f2 = f5;
        }
        RenderBlocksUtils
            .setupColorMultiplier(block, this.blockAccess, x, y, z, this.hasOverrideBlockTexture(), f, f1, f2);
    }

    @ModifyConstant(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        constant = { @Constant(floatValue = 0.5F), @Constant(floatValue = 0.6F), @Constant(floatValue = 0.8F) })
    private float redirectAOBaseMultiplier(float constant) {
        return RenderPass.getAOBaseMultiplier(constant);
    }

    // If only ordinal number was accessible ...

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 0),
        ordinal = 1)
    private boolean redirectColorMultiplier1(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(0);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 1),
        ordinal = 1)
    private boolean redirectColorMultiplier2(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(2);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 2),
        ordinal = 1)
    private boolean redirectColorMultiplier3(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(3);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 3),
        ordinal = 1)
    private boolean redirectColorMultiplier4(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(4);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 4),
        ordinal = 1)
    private boolean redirectColorMultiplier5(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(5);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 0),
        ordinal = 1)
    private boolean redirectColorMultiplierPartial1(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(0);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 1),
        ordinal = 1)
    private boolean redirectColorMultiplierPartial2(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(2);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 2),
        ordinal = 1)
    private boolean redirectColorMultiplierPartial3(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(3);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 3),
        ordinal = 1)
    private boolean redirectColorMultiplierPartial4(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(4);
    }

    @ModifyVariable(
        method = "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(value = "LOAD", ordinal = 4),
        ordinal = 1)
    private boolean redirectColorMultiplierPartial5(boolean value) {
        return RenderBlocksUtils.useColorMultiplier(5);
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 0))
    private void redirectColorMultiplier1(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(0),
            RenderBlocksUtils.getColorMultiplierGreen(0),
            RenderBlocksUtils.getColorMultiplierBlue(0));
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 1))
    private void redirectColorMultiplier2(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(1),
            RenderBlocksUtils.getColorMultiplierGreen(1),
            RenderBlocksUtils.getColorMultiplierBlue(1));
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 2))
    private void redirectColorMultiplier3(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(2),
            RenderBlocksUtils.getColorMultiplierGreen(2),
            RenderBlocksUtils.getColorMultiplierBlue(2));
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 4))
    private void redirectColorMultiplier4(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(3),
            RenderBlocksUtils.getColorMultiplierGreen(3),
            RenderBlocksUtils.getColorMultiplierBlue(3));
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 6))
    private void redirectColorMultiplier5(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(4),
            RenderBlocksUtils.getColorMultiplierGreen(4),
            RenderBlocksUtils.getColorMultiplierBlue(4));
    }

    @Redirect(
        method = "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 8))
    private void redirectColorMultiplier6(Tessellator instance, float red, float green, float blue) {
        Tessellator.instance.setColorOpaque_F(
            RenderBlocksUtils.getColorMultiplierRed(5),
            RenderBlocksUtils.getColorMultiplierGreen(5),
            RenderBlocksUtils.getColorMultiplierBlue(5));
    }

    // If I was able to access ordinal number the duplication wouldn't be necessary
    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 0))
    private boolean modifyRenderBlockSandFalling0(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 0);
    }

    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 1))
    private boolean modifyRenderBlockSandFalling1(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 1);
    }

    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 2))
    private boolean modifyRenderBlockSandFalling2(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 2);
    }

    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 3))
    private boolean modifyRenderBlockSandFalling3(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 3);
    }

    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 4))
    private boolean modifyRenderBlockSandFalling4(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 4);
    }

    @WrapWithCondition(
        method = "renderBlockSandFalling(Lnet/minecraft/block/Block;Lnet/minecraft/world/World;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 5))
    private boolean modifyRenderBlockSandFalling5(Tessellator tessellator, float x, float y, float z, Block block,
        World world) {
        return !ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, (int) x, (int) y, (int) z, 5);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V",
            ordinal = 0))
    private void mcpatcherforge$handleSmoothing(Tessellator tessellator, float red, float green, float blue,
        Block block, int x, int y, int z) {
        if (!(ColorizeBlock.isSmooth = ColorizeBlock
            .setupBlockSmoothing((RenderBlocks) (Object) this, block, this.blockAccess, x, y, z, 1 + 6))) {
            tessellator.setColorOpaque_F(red, green, blue);
        }
    }

    // Violate DRY

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 0))
    private void mcpatcherforge$redirectColor1(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopLeft,
            this.colorGreenTopLeft,
            this.colorBlueTopLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 1))
    private void mcpatcherforge$redirectColor2(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomLeft,
            this.colorGreenBottomLeft,
            this.colorBlueBottomLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 2))
    private void mcpatcherforge$redirectColor3(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomRight,
            this.colorGreenBottomRight,
            this.colorBlueBottomRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 3))
    private void mcpatcherforge$redirectColor4(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopRight,
            this.colorGreenTopRight,
            this.colorBlueTopRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 4))
    private void mcpatcherforge$redirectColor5(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopLeft,
            this.colorGreenTopLeft,
            this.colorBlueTopLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 5))
    private void mcpatcherforge$redirectColor6(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopRight,
            this.colorGreenTopRight,
            this.colorBlueTopRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 6))
    private void mcpatcherforge$redirectColor7(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomRight,
            this.colorGreenBottomRight,
            this.colorBlueBottomRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 7))
    private void mcpatcherforge$redirectColor8(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomLeft,
            this.colorGreenBottomLeft,
            this.colorBlueBottomLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Inject(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderFaceYNeg(Lnet/minecraft/block/Block;DDDLnet/minecraft/util/IIcon;)V"))
    private void mcpatcherforge$setEnableAO(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        this.enableAO = false;
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 8))
    private void mcpatcherforge$redirectColor11(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopLeft,
            this.colorGreenTopLeft,
            this.colorBlueTopLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 9))
    private void mcpatcherforge$redirectColor12(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomLeft,
            this.colorGreenBottomLeft,
            this.colorBlueBottomLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 10))
    private void mcpatcherforge$redirectColor13(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomRight,
            this.colorGreenBottomRight,
            this.colorBlueBottomRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 11))
    private void mcpatcherforge$redirectColor14(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopRight,
            this.colorGreenTopRight,
            this.colorBlueTopRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 12))
    private void mcpatcherforge$redirectColor15(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopRight,
            this.colorGreenTopRight,
            this.colorBlueTopRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 13))
    private void mcpatcherforge$redirectColor16(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomRight,
            this.colorGreenBottomRight,
            this.colorBlueBottomRight,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 14))
    private void mcpatcherforge$redirectColor17(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedBottomLeft,
            this.colorGreenBottomLeft,
            this.colorBlueBottomLeft,
            x,
            y,
            z,
            u,
            v);
    }

    @Redirect(
        method = "renderBlockLiquid(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V",
            ordinal = 15))
    private void mcpatcherforge$redirectColor18(Tessellator tessellator, double x, double y, double z, double u,
        double v) {
        mcpatcherforge$setColorAndVertex(
            tessellator,
            this.colorRedTopLeft,
            this.colorGreenTopLeft,
            this.colorBlueTopLeft,
            x,
            y,
            z,
            u,
            v);
    }
}
