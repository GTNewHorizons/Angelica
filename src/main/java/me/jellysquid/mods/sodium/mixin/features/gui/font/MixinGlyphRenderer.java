package me.jellysquid.mods.sodium.mixin.features.gui.font;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.GlyphVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlyphRenderer.class)
public class MixinGlyphRenderer {
    @Shadow
    @Final
    private float minX;

    @Shadow
    @Final
    private float maxX;

    @Shadow
    @Final
    private float minY;

    @Shadow
    @Final
    private float maxY;

    @Shadow
    @Final
    private float minU;

    @Shadow
    @Final
    private float minV;

    @Shadow
    @Final
    private float maxV;

    @Shadow
    @Final
    private float maxU;

    /**
     * @reason Use intrinsics
     * @author JellySquid
     */
    @Overwrite
    public void draw(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer vertexConsumer, float red, float green, float blue, float alpha, int light) {
        float x1 = x + this.minX;
        float x2 = x + this.maxX;
        float y1 = this.minY - 3.0F;
        float y2 = this.maxY - 3.0F;
        float h1 = y + y1;
        float h2 = y + y2;
        float w1 = italic ? 1.0F - 0.25F * y1 : 0.0F;
        float w2 = italic ? 1.0F - 0.25F * y2 : 0.0F;

        int color = ColorABGR.pack(red, green, blue, alpha);

        GlyphVertexSink drain = VertexDrain.of(vertexConsumer)
                .createSink(VanillaVertexTypes.GLYPHS);
        drain.ensureCapacity(4);
        drain.writeGlyph(matrix, x1 + w1, h1, 0.0F, color, this.minU, this.minV, light);
        drain.writeGlyph(matrix, x1 + w2, h2, 0.0F, color, this.minU, this.maxV, light);
        drain.writeGlyph(matrix, x2 + w2, h2, 0.0F, color, this.maxU, this.maxV, light);
        drain.writeGlyph(matrix, x2 + w1, h1, 0.0F, color, this.maxU, this.minV, light);
        drain.flush();
    }
}
