package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.PlayerReflectionCapture;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;

@Mixin(ModelBiped.class)
public abstract class MixinModelBiped {

    @Unique private static final Matrix4f angelica$mat = new Matrix4f();
    @Unique private static final Vector3f angelica$vec = new Vector3f();

    @Inject(method = "render(Lnet/minecraft/entity/Entity;FFFFFF)V", at = @At("RETURN"))
    private void angelica$capturePlayerReflection(Entity entity, float limbSwing, float limbSwingAmount,
        float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {

        if (!PlayerReflectionCapture.shouldCapture()) return;
        if ((Object) this != PlayerReflectionCapture.getTarget()) return;
        if (entity != PlayerReflectionCapture.getTargetEntity()) return;

        final ModelBiped model = (ModelBiped) (Object) this;
        final float[] data = PlayerReflectionCapture.vertexScratch();

        // Six 48-vertex blocks = body parts, each base box + overlay box
        int o = 0;
        o = angelica$emitBox(model.bipedHead,       data, o, scale);
        o = angelica$emitBox(model.bipedHeadwear,   data, o, scale);
        o = angelica$emitBox(model.bipedRightArm,   data, o, scale);
        o = angelica$emitBox(model.bipedRightArm,   data, o, scale);
        o = angelica$emitBox(model.bipedLeftLeg,    data, o, scale);
        o = angelica$emitBox(model.bipedLeftLeg,    data, o, scale);
        o = angelica$emitBox(model.bipedLeftArm,    data, o, scale);
        o = angelica$emitBox(model.bipedLeftArm,    data, o, scale);
        o = angelica$emitBox(model.bipedRightLeg,   data, o, scale);
        o = angelica$emitBox(model.bipedRightLeg,   data, o, scale);
        o = angelica$emitBox(model.bipedBody,       data, o, scale);
            angelica$emitBox(model.bipedBody,       data, o, scale);

        PlayerReflectionCapture.drawPlayerCapture(data);
    }

    private static int angelica$emitBox(ModelRenderer part, float[] out, int o, float scale) {
        if (part == null || part.cubeList == null || part.cubeList.isEmpty()) {
            final int end = o + 24 * PlayerReflectionCapture.FLOATS_PER_VERTEX;
            Arrays.fill(out, o, end, 0.0f);
            return end;
        }

        final Matrix4f m = angelica$mat.identity();
        m.translate(part.offsetX, part.offsetY, part.offsetZ);
        m.translate(part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale);
        if (part.rotateAngleZ != 0.0f) m.rotateZ(part.rotateAngleZ);
        if (part.rotateAngleY != 0.0f) m.rotateY(part.rotateAngleY);
        if (part.rotateAngleX != 0.0f) m.rotateX(part.rotateAngleX);

        final ModelBox box = part.cubeList.getFirst();
        final TexturedQuad[] quads = ((AccessorModelBox) (Object) box).angelica$getQuadList();
        final Vector3f v = angelica$vec;

        for (int q = 0; q < 6; q++) {
            final PositionTextureVertex[] corners = quads[q].vertexPositions;
            for (int c = 0; c < 4; c++) {
                final PositionTextureVertex corner = corners[c];
                v.set((float) corner.vector3D.xCoord * scale,
                      (float) corner.vector3D.yCoord * scale,
                      (float) corner.vector3D.zCoord * scale);
                m.transformPosition(v);
                out[o++] = v.x;
                out[o++] = v.y;
                out[o++] = v.z;
                out[o++] = corner.texturePositionX;
                out[o++] = corner.texturePositionY * 0.5f;
            }
        }
        return o;
    }
}
