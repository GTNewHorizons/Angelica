/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.render.vertexFormat;

import com.google.common.collect.ImmutableList;

/**
 * A (almost) exact copy of MC's
 * DefaultVertexFormats class.
 *
 * @author James Seibel
 * @version 12-8-2021
 */
public class DefaultLodVertexFormats
{
	public static final LodVertexFormatElement ELEMENT_POSITION = new LodVertexFormatElement(3, LodVertexFormatElement.DataType.USHORT, 3, false);
	public static final LodVertexFormatElement ELEMENT_COLOR = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.UBYTE, 4, false);
	public static final LodVertexFormatElement ELEMENT_UV = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.FLOAT, 2, false);
	public static final LodVertexFormatElement ELEMENT_LIGHT_MAP_UV = new LodVertexFormatElement(1, LodVertexFormatElement.DataType.SHORT, 2, false);
	public static final LodVertexFormatElement ELEMENT_NORMAL = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.BYTE, 3, false);
	public static final LodVertexFormatElement ELEMENT_BYTE_PADDING = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.BYTE, 1, true);
	
	public static final LodVertexFormatElement ELEMENT_LIGHT = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.UBYTE, 1, false);
	public static final LodVertexFormatElement ELEMENT_IRIS_MATERIAL_INDEX = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.BYTE, 1, false);
	public static final LodVertexFormatElement ELEMENT_IRIS_NORMAL_INDEX = new LodVertexFormatElement(0, LodVertexFormatElement.DataType.BYTE, 1, false);
	
	
	public static final LodVertexFormat POSITION = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).build());
	public static final LodVertexFormat POSITION_COLOR = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).add(ELEMENT_COLOR).build());
	public static final LodVertexFormat POSITION_COLOR_LIGHTMAP = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_LIGHT_MAP_UV).build());
	public static final LodVertexFormat POSITION_TEX = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).add(ELEMENT_UV).build());
	public static final LodVertexFormat POSITION_COLOR_TEX = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV).build());
	public static final LodVertexFormat POSITION_COLOR_TEX_LIGHTMAP = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder().add(ELEMENT_POSITION).add(ELEMENT_COLOR).add(ELEMENT_UV).add(ELEMENT_LIGHT_MAP_UV).build());
	
	public static final LodVertexFormat POSITION_COLOR_BLOCK_LIGHT_SKY_LIGHT = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder()
			.add(ELEMENT_POSITION)
			.add(ELEMENT_BYTE_PADDING)
			.add(ELEMENT_LIGHT)
			.add(ELEMENT_COLOR)
			.build());	
	
	public static final LodVertexFormat POSITION_COLOR_BLOCK_LIGHT_SKY_LIGHT_MATERIAL_ID_NORMAL_INDEX = new LodVertexFormat(ImmutableList.<LodVertexFormatElement>builder()
			.add(ELEMENT_POSITION)
			.add(ELEMENT_BYTE_PADDING)
			.add(ELEMENT_LIGHT)
			.add(ELEMENT_COLOR)
			.add(ELEMENT_IRIS_MATERIAL_INDEX)
			.add(ELEMENT_IRIS_NORMAL_INDEX)
			.add(ELEMENT_BYTE_PADDING)
			.add(ELEMENT_BYTE_PADDING) // padding is to make sure the format is a multiple of 4
			.build());
	
}
