package me.jellysquid.mods.sodium.mixin.features.buffer_builder.fast_advance;

import me.jellysquid.mods.sodium.client.buffer.ExtendedVertexFormat;
import net.minecraft.client.render.*;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder extends FixedColorVertexConsumer implements BufferVertexConsumer {
    @Shadow
    private VertexFormatElement currentElement;

    @Shadow
    private int elementOffset;

    @Shadow
    private int currentElementId;

    private ExtendedVertexFormat.Element[] embeddium$vertexFormatExtendedElements;
    private ExtendedVertexFormat.Element embeddium$currentExtendedElement;

    @Inject(method = "method_23918",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/BufferBuilder;format:Lnet/minecraft/client/render/VertexFormat;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void onFormatChanged(VertexFormat format, CallbackInfo ci) {
        embeddium$vertexFormatExtendedElements = ((ExtendedVertexFormat) format).embeddium$getExtendedElements();
        embeddium$currentExtendedElement = embeddium$vertexFormatExtendedElements[0];
    }

    /**
     * @author JellySquid
     * @reason Remove modulo operations, recursion, and list dereference
     */
    @Override
    @Overwrite
    public void nextElement() {
        if ((currentElementId += embeddium$currentExtendedElement.increment) >= embeddium$vertexFormatExtendedElements.length)
            currentElementId -= embeddium$vertexFormatExtendedElements.length;
        elementOffset += embeddium$currentExtendedElement.byteLength;
        embeddium$currentExtendedElement = embeddium$vertexFormatExtendedElements[currentElementId];
        currentElement = embeddium$currentExtendedElement.actual;

        if (this.colorFixed && this.currentElement.getType() == VertexFormatElement.Type.COLOR) {
            BufferVertexConsumer.super.color(this.fixedRed, this.fixedGreen, this.fixedBlue, this.fixedAlpha);
        }
    }
}
