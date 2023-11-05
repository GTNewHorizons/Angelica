package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement.Type;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement.Usage;

public class DefaultVertexFormat {

    public static final VertexFormatElement ELEMENT_POSITION = new VertexFormatElement(0, Type.FLOAT, Usage.POSITION, 3);
    public static final VertexFormatElement ELEMENT_COLOR = new VertexFormatElement(0, Type.UBYTE, Usage.COLOR, 4);
    public static final VertexFormatElement ELEMENT_UV0 = new VertexFormatElement(0, Type.FLOAT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV1 = new VertexFormatElement(1, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV2 = new VertexFormatElement(2, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_NORMAL = new VertexFormatElement(0, Type.BYTE, Usage.NORMAL, 3);
    public static final VertexFormatElement ELEMENT_PADDING = new VertexFormatElement(0, Type.BYTE, Usage.PADDING, 1);
    public static final VertexFormat BLOCK = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV0).add(ELEMENT_UV2).add(ELEMENT_NORMAL).add(ELEMENT_PADDING).build());
    public static final VertexFormat NEW_ENTITY = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV0).add(ELEMENT_UV1).add(ELEMENT_UV2).add(ELEMENT_NORMAL).add(ELEMENT_PADDING).build());
    public static final VertexFormat PARTICLE = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).add(ELEMENT_COLOR).add(ELEMENT_UV2).build());
    public static final VertexFormat POSITION = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).build());
    public static final VertexFormat POSITION_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).build());
    public static final VertexFormat POSITION_COLOR_LIGHTMAP = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV2).build());
    public static final VertexFormat POSITION_TEX = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).build());
    public static final VertexFormat POSITION_COLOR_TEX = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV0).build());
    public static final VertexFormat POSITION_TEX_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).add(ELEMENT_COLOR).build());
    public static final VertexFormat POSITION_COLOR_TEX_LIGHTMAP = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV0).add(ELEMENT_UV2).build());
    public static final VertexFormat POSITION_TEX_LIGHTMAP_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).add(ELEMENT_UV2).add(ELEMENT_COLOR).build());
    public static final VertexFormat POSITION_TEX_COLOR_NORMAL = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(ELEMENT_POSITION).add(ELEMENT_UV0).add(ELEMENT_COLOR).add(ELEMENT_NORMAL).add(ELEMENT_PADDING).build());


}
