package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gtnewhorizons.angelica.mixins.interfaces.TextureMetadataExtension;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.client.resources.data.TextureMetadataSectionSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/**
 * Reads the modern {@code "mipmap_strategy"} key out of a sprite's {@code texture} mcmeta section.
 */
@Mixin(TextureMetadataSectionSerializer.class)
public class MixinTextureMetadataSectionSerializer {

    @Inject(
        method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/resources/data/TextureMetadataSection;",
        at = @At("RETURN"))
    private void angelica$readMipmapStrategy(JsonElement json, Type type, JsonDeserializationContext context,
                                             CallbackInfoReturnable<TextureMetadataSection> cir) {
        final TextureMetadataSection section = cir.getReturnValue();
        if (section == null || !json.isJsonObject()) {
            return;
        }
        final JsonObject object = json.getAsJsonObject();
        if (!object.has("mipmap_strategy")) {
            return;
        }
        final JsonElement value = object.get("mipmap_strategy");
        if (!value.isJsonPrimitive()) {
            return;
        }
        ((TextureMetadataExtension) section).angelica$setMipmapStrategy(MipmapStrategy.byName(value.getAsString()));
    }
}
