package me.jellysquid.mods.sodium.mixin.features.buffer_builder.fast_advance;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.buffer.ExtendedVertexFormat;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thanks to Maximum for this optimization, taken from Fireblanket.
 */
@Mixin(VertexFormat.class)
public class MixinVertexFormat implements ExtendedVertexFormat {
    @Shadow
    @Final
    private ImmutableList<VertexFormatElement> elements;

    private Element[] embeddium$extendedElements;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void embeddium$createElementArray(ImmutableList<VertexFormatElement> immutableList, CallbackInfo ci) {
        this.embeddium$extendedElements = new Element[this.elements.size()];

        if (this.elements.size() == 0)
            return; // prevent crash with mods that create empty VertexFormats

        VertexFormatElement currentElement = elements.get(0);
        int id = 0;
        for (VertexFormatElement element : this.elements) {
            if (element.getType() == VertexFormatElement.Type.PADDING) continue;

            int oldId = id;
            int byteLength = 0;

            do {
                if (++id >= this.embeddium$extendedElements.length)
                    id -= this.embeddium$extendedElements.length;
                byteLength += currentElement.getByteLength();
                currentElement = this.elements.get(id);
            } while (currentElement.getType() == VertexFormatElement.Type.PADDING);

            this.embeddium$extendedElements[oldId] = new Element(element, id - oldId, byteLength);
        }
    }

    @Override
    public Element[] embeddium$getExtendedElements() {
        return this.embeddium$extendedElements;
    }
}
