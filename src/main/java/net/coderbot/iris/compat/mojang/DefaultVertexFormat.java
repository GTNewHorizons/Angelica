package net.coderbot.iris.compat.mojang;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.compat.mojang.VertexFormatElement.Type;
import net.coderbot.iris.compat.mojang.VertexFormatElement.Usage;

public class DefaultVertexFormat {

    public static final VertexFormatElement ELEMENT_POSITION = new VertexFormatElement(0, Type.FLOAT, Usage.POSITION, 3);
    public static final VertexFormatElement ELEMENT_COLOR = new VertexFormatElement(0, Type.UBYTE, Usage.COLOR, 4);
    public static final VertexFormatElement ELEMENT_UV0 = new VertexFormatElement(0, Type.FLOAT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV1 = new VertexFormatElement(1, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV2 = new VertexFormatElement(2, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_NORMAL = new VertexFormatElement(0, Type.BYTE, Usage.NORMAL, 3);
    public static final VertexFormatElement ELEMENT_PADDING = new VertexFormatElement(0, Type.BYTE, Usage.PADDING, 1);

    public static final VertexFormat POSITION = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).build());
    public static final VertexFormat POSITION_TEX = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).build());

}
