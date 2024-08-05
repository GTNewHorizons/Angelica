package net.coderbot.iris.vertices;

import com.google.common.collect.ImmutableList;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;

public class IrisVertexFormats {
	public static final VertexFormatElement ENTITY_ELEMENT;
	public static final VertexFormatElement MID_TEXTURE_ELEMENT;
	public static final VertexFormatElement TANGENT_ELEMENT;
	public static final VertexFormatElement MID_BLOCK_ELEMENT;

	public static final VertexFormat TERRAIN;
	public static final VertexFormat ENTITY;

	static {
		ENTITY_ELEMENT = new VertexFormatElement(11, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.GENERIC, 2);
		MID_TEXTURE_ELEMENT = new VertexFormatElement(12, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
		TANGENT_ELEMENT = new VertexFormatElement(13, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4);
		MID_BLOCK_ELEMENT = new VertexFormatElement(14, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 3);

		ImmutableList.Builder<VertexFormatElement> terrainElements = ImmutableList.builder();
		ImmutableList.Builder<VertexFormatElement> entityElements = ImmutableList.builder();

		terrainElements.add(DefaultVertexFormat.POSITION_ELEMENT); // 12
		terrainElements.add(DefaultVertexFormat.COLOR_ELEMENT); // 16
		terrainElements.add(DefaultVertexFormat.TEXTURE_ELEMENT); // 24
		terrainElements.add(DefaultVertexFormat.LIGHT_ELEMENT); // 28
		terrainElements.add(DefaultVertexFormat.NORMAL_ELEMENT); // 31
		terrainElements.add(DefaultVertexFormat.PADDING_ELEMENT); // 32
		terrainElements.add(ENTITY_ELEMENT); // 36
		terrainElements.add(MID_TEXTURE_ELEMENT); // 44
		terrainElements.add(TANGENT_ELEMENT); // 48
		terrainElements.add(MID_BLOCK_ELEMENT); // 51
		terrainElements.add(DefaultVertexFormat.PADDING_ELEMENT); // 52

		entityElements.add(DefaultVertexFormat.POSITION_ELEMENT); // 12
		entityElements.add(DefaultVertexFormat.COLOR_ELEMENT); // 16
		entityElements.add(DefaultVertexFormat.TEXTURE_ELEMENT); // 24
		entityElements.add(DefaultVertexFormat.LIGHT_ELEMENT); // 28
		entityElements.add(DefaultVertexFormat.NORMAL_ELEMENT); // 31
		entityElements.add(DefaultVertexFormat.PADDING_ELEMENT); // 32
		entityElements.add(MID_TEXTURE_ELEMENT); // 40
		entityElements.add(TANGENT_ELEMENT); // 44

		TERRAIN = new VertexFormat(terrainElements.build());
		ENTITY = new VertexFormat(entityElements.build());
	}
}
