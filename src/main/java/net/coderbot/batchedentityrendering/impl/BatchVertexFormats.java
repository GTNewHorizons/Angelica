package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.writers.IVertexAttributeWriter;
import net.minecraft.client.renderer.Tessellator;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.LIGHT_INDEX;

public final class BatchVertexFormats {
    public static final VertexFormatElement LIGHT_FLOAT_ELEMENT = new VertexFormatElement(1, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.SECONDARY_UV, 2, VertexFlags.BRIGHTNESS_BIT, new FloatLightVertexAttributeWriter());
    public static final VertexFormat POSITION_COLOR_TEXTURE_LIGHTF_NORMAL = new VertexFormat(DefaultVertexFormat.POSITION_ELEMENT, DefaultVertexFormat.COLOR_ELEMENT, DefaultVertexFormat.TEXTURE_ELEMENT, LIGHT_FLOAT_ELEMENT, DefaultVertexFormat.NORMAL_ELEMENT);

    private BatchVertexFormats() {}

    private static final class FloatLightVertexAttributeWriter implements IVertexAttributeWriter {
        @Override
        public int writeAttribute(long pointer, int[] data, int index) {
            final int light = data[index | LIGHT_INDEX];
            memPutFloat(pointer, light & 0xFFFF);
            memPutFloat(pointer + 4, light >>> 16);
            return 8;
        }

        @Override
        public int writeAttribute(long pointer, Tessellator tessellator) {
            final int light = tessellator.brightness;
            memPutFloat(pointer, light & 0xFFFF);
            memPutFloat(pointer + 4, light >>> 16);
            return 8;
        }

        @Override
        public int readAttribute(long pointer, Tessellator tessellator) {
            tessellator.brightness = (((int) memGetFloat(pointer + 4)) << 16) | (((int) memGetFloat(pointer)) & 0xFFFF);
            return 8;
        }
    }
}
