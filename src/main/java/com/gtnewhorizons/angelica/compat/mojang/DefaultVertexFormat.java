package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement.Type;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormatElement.Usage;


public class DefaultVertexFormat {

    public static final VertexFormatElement POSITION_ELEMENT = new VertexFormatElement(0, Type.FLOAT, Usage.POSITION, 3);
    public static final VertexFormatElement COLOR_ELEMENT = new VertexFormatElement(0, Type.UBYTE, Usage.COLOR, 4);
    public static final VertexFormatElement TEXTURE_0_ELEMENT = new VertexFormatElement(0, Type.FLOAT, Usage.UV, 2);
    public static final VertexFormatElement OVERLAY_ELEMENT = new VertexFormatElement(1, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement LIGHT_ELEMENT = new VertexFormatElement(2, Type.SHORT, Usage.UV, 2);
    public static final VertexFormatElement NORMAL_ELEMENT = new VertexFormatElement(0, Type.BYTE, Usage.NORMAL, 3);
    public static final VertexFormatElement PADDING_ELEMENT = new VertexFormatElement(0, Type.BYTE, Usage.PADDING, 1);
    public static final VertexFormat POSITION_COLOR_TEXTURE_LIGHT_NORMAL = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).add(TEXTURE_0_ELEMENT).add(LIGHT_ELEMENT).add(NORMAL_ELEMENT).add(PADDING_ELEMENT).build());
    public static final VertexFormat POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).add(TEXTURE_0_ELEMENT).add(OVERLAY_ELEMENT).add(LIGHT_ELEMENT).add(NORMAL_ELEMENT).add(PADDING_ELEMENT).build());
    public static final VertexFormat POSITION_TEXTURE_COLOR_LIGHT = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(TEXTURE_0_ELEMENT).add(COLOR_ELEMENT).add(LIGHT_ELEMENT).build());
    public static final VertexFormat POSITION = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).build());
    public static final VertexFormat POSITION_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).build());
    public static final VertexFormat POSITION_COLOR_LIGHT = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).add(LIGHT_ELEMENT).build());
    public static final VertexFormat POSITION_TEXTURE = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(TEXTURE_0_ELEMENT).build());
    public static final VertexFormat POSITION_COLOR_TEXTURE = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).add(TEXTURE_0_ELEMENT).build());
    public static final VertexFormat POSITION_TEXTURE_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(TEXTURE_0_ELEMENT).add(COLOR_ELEMENT).build());
    public static final VertexFormat POSITION_COLOR_TEX_LIGHTMAP = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(COLOR_ELEMENT).add(TEXTURE_0_ELEMENT).add(LIGHT_ELEMENT).build());
    public static final VertexFormat POSITION_TEXTURE_LIGHT_COLOR = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(TEXTURE_0_ELEMENT).add(LIGHT_ELEMENT).add(COLOR_ELEMENT).build());
    public static final VertexFormat POSITION_TEXTURE_COLOR_NORMAL = new VertexFormat(new ImmutableList.Builder<VertexFormatElement>().add(POSITION_ELEMENT).add(TEXTURE_0_ELEMENT).add(COLOR_ELEMENT).add(NORMAL_ELEMENT).add(PADDING_ELEMENT).build());


}
