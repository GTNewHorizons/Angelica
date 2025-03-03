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

package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.level.AbstractDhLevel;
import com.seibel.distanthorizons.core.logging.SpamReducedLogger;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * A helper class that is used to access the data from a long
 * formatted as a render data point. <br><br>
 *
 * To access data from a long formatted as a full data point see: {@link FullDataPointUtil}
 *
 * <strong>DataPoint Format: </strong><br>
 * <code>
 * BM: block material id <br>
 * A: color alpha <br>
 * R: color red <br>
 * G: color green <br>
 * B: color blue <br>
 * H: column height <br>
 * D: column depth (what Y value the column starts at) <br>
 * BL: block light <br>
 * SL: sky light <br>
 *
 * =======Bit layout=======	<br>
 * BM BM BM BM A  A  A  A  |	<br>
 * R  R  R  R  R  R  R  R  |	<br>
 * G  G  G  G  G  G  G  G  |	<br>
 * B  B  B  B  B  B  B  B  |	<br><br>
 *
 * H  H  H  H  H  H  H  H  |	<br>
 * H  H  H  H  D  D  D  D  |	<br>
 * D  D  D  D  D  D  D  D  |	<br>
 * BL BL BL BL SL SL SL SL |	<br>
 * </code>
 *
 * @see FullDataPointUtil
 */
public class RenderDataPointUtil
{
	// Reminder: bytes have range of [-128, 127].
	// When converting to or from an int a 128 should be added or removed.
	// If there is a bug with color then it's probably caused by this.
	
	public static final boolean RUN_VALIDATION = ModInfo.IS_DEV_BUILD;
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	public final static int EMPTY_DATA = 0;
	public final static int MAX_WORLD_Y_SIZE = 4096;
	
	public final static int ALPHA_DOWNSIZE_SHIFT = 4;
	
	
	public final static int IRIS_BLOCK_MATERIAL_ID_SHIFT = 60;
	
	public final static int COLOR_SHIFT = 32;
	public final static int BLUE_SHIFT = COLOR_SHIFT;
	public final static int GREEN_SHIFT = BLUE_SHIFT + 8;
	public final static int RED_SHIFT = GREEN_SHIFT + 8;
	public final static int ALPHA_SHIFT = RED_SHIFT + 8;
	
	public final static int HEIGHT_SHIFT = 20;
	public final static int DEPTH_SHIFT = 8;
	public final static int BLOCK_LIGHT_SHIFT = 4;
	public final static int SKY_LIGHT_SHIFT = 0;
	
	public final static long ALPHA_MASK = 0xF;
	public final static long RED_MASK = 0xFF;
	public final static long GREEN_MASK = 0xFF;
	public final static long BLUE_MASK = 0xFF;
	public final static long COLOR_MASK = 0xFFFFFF;
	public final static long HEIGHT_MASK = 0xFFF;
	public final static long DEPTH_MASK = 0xFFF;
	public final static long HEIGHT_DEPTH_MASK = 0xFFFFFF;
	public final static long BLOCK_LIGHT_MASK = 0xF;
	public final static long SKY_LIGHT_MASK = 0xF;
	public final static long IRIS_BLOCK_MATERIAL_ID_MASK = 0xF;
	public final static long COMPARE_SHIFT = 0b111;
	
	public final static long HEIGHT_SHIFTED_MASK = HEIGHT_MASK << HEIGHT_SHIFT;
	public final static long DEPTH_SHIFTED_MASK = DEPTH_MASK << DEPTH_SHIFT;
	
	public final static long VOID_SETTER = HEIGHT_SHIFTED_MASK | DEPTH_SHIFTED_MASK;
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public static long createDataPoint(int height, int depth, int color, int lightSky, int lightBlock, int irisBlockMaterialId)
	{
		return createDataPoint(
				ColorUtil.getAlpha(color),
				ColorUtil.getRed(color),
				ColorUtil.getGreen(color),
				ColorUtil.getBlue(color),
				height, depth, lightSky, lightBlock, irisBlockMaterialId);
	}
	
	public static long createDataPoint(int alpha, int red, int green, int blue, int height, int depth, int lightSky, int lightBlock, int irisBlockMaterialId)
	{
		if (RUN_VALIDATION)
		{
			// assertions are inside if-blocks to prevent unnecessary string concatenations
			if (height < 0 || height >= MAX_WORLD_Y_SIZE)
			{
				LodUtil.assertNotReach("Trying to create datapoint with height[" + height + "] out of range!");
			}
			if (depth < 0 || depth >= MAX_WORLD_Y_SIZE)
			{
				LodUtil.assertNotReach("Trying to create datapoint with depth[" + depth + "] out of range!");
			}
			
			if (lightSky < 0 || lightSky >= 16)
			{
				LodUtil.assertNotReach("Trying to create datapoint with lightSky[" + lightSky + "] out of range!");
			}
			if (lightBlock < 0 || lightBlock >= 16)
			{
				LodUtil.assertNotReach("Trying to create datapoint with lightBlock[" + lightBlock + "] out of range!");
			}
			
			if (irisBlockMaterialId < 0 || irisBlockMaterialId >= 256)
			{
				LodUtil.assertNotReach("Trying to create datapoint with irisBlockMaterialId[" + irisBlockMaterialId + "] out of range!");
			}
			
			if (alpha < 0 || alpha >= 256)
			{
				LodUtil.assertNotReach("Trying to create datapoint with alpha[" + alpha + "] out of range!");
			}
			if (red < 0 || red >= 256)
			{
				LodUtil.assertNotReach("Trying to create datapoint with red[" + red + "] out of range!");
			}
			if (green < 0 || green >= 256)
			{
				LodUtil.assertNotReach("Trying to create datapoint with green[" + green + "] out of range!");
			}
			if (blue < 0 || blue >= 256)
			{
				LodUtil.assertNotReach("Trying to create datapoint with blue[" + blue + "] out of range!");
			}
			
			
			if (depth > height)
			{
				LodUtil.assertNotReach("Trying to create datapoint with depth[" + depth + "] greater than height[" + height + "]!");
			}
		}
		
		
		long out = (long) (alpha >>> ALPHA_DOWNSIZE_SHIFT) << ALPHA_SHIFT
				| (red & RED_MASK) << RED_SHIFT
				| (green & GREEN_MASK) << GREEN_SHIFT
				| (blue & BLUE_MASK) << BLUE_SHIFT
				| (height & HEIGHT_MASK) << HEIGHT_SHIFT
				| (depth & DEPTH_MASK) << DEPTH_SHIFT
				| (lightBlock & BLOCK_LIGHT_MASK) << BLOCK_LIGHT_SHIFT
				| (lightSky & SKY_LIGHT_MASK) << SKY_LIGHT_SHIFT
				| (irisBlockMaterialId & IRIS_BLOCK_MATERIAL_ID_MASK) << IRIS_BLOCK_MATERIAL_ID_SHIFT
				;
		
		return out;
	}
	
	public static long shiftHeightAndDepth(long dataPoint, short offset)
	{
		long height = (dataPoint + ((long) offset << HEIGHT_SHIFT)) & HEIGHT_SHIFTED_MASK;
		long depth = (dataPoint + (offset << DEPTH_SHIFT)) & DEPTH_SHIFTED_MASK;
		
		return dataPoint & ~(HEIGHT_SHIFTED_MASK | DEPTH_SHIFTED_MASK) | height | depth;
	}
	
	/** AKA the ending/top/highest Y value above {@link AbstractDhLevel#getMinY()} */
	public static short getYMax(long dataPoint) { return (short) ((dataPoint >>> HEIGHT_SHIFT) & HEIGHT_MASK); }
	/** AKA the starting/bottom/lowest Y value above {@link AbstractDhLevel#getMinY()} */
	public static short getYMin(long dataPoint) { return (short) ((dataPoint >>> DEPTH_SHIFT) & DEPTH_MASK); }
	public static long setYMin(long dataPoint, int depth) { return (long) ((dataPoint & ~(DEPTH_MASK << DEPTH_SHIFT)) | (depth & DEPTH_MASK) << DEPTH_SHIFT); }
	
	public static short getAlpha(long dataPoint) { return (short) ((((dataPoint >>> ALPHA_SHIFT) & ALPHA_MASK) << ALPHA_DOWNSIZE_SHIFT) | 0b1111); }
	public static short getRed(long dataPoint) { return (short) ((dataPoint >>> RED_SHIFT) & RED_MASK); }
	public static short getGreen(long dataPoint) { return (short) ((dataPoint >>> GREEN_SHIFT) & GREEN_MASK); }
	public static short getBlue(long dataPoint) { return (short) ((dataPoint >>> BLUE_SHIFT) & BLUE_MASK); }
	
	public static byte getLightSky(long dataPoint) { return (byte) ((dataPoint >>> SKY_LIGHT_SHIFT) & SKY_LIGHT_MASK); }
	public static byte getLightBlock(long dataPoint) { return (byte) ((dataPoint >>> BLOCK_LIGHT_SHIFT) & BLOCK_LIGHT_MASK); }
	
	public static byte getBlockMaterialId(long dataPoint) { return (byte) ((dataPoint >>> IRIS_BLOCK_MATERIAL_ID_SHIFT) & IRIS_BLOCK_MATERIAL_ID_MASK); }
	
	
	public static boolean isVoid(long dataPoint) { return (((dataPoint >>> DEPTH_SHIFT) & HEIGHT_DEPTH_MASK) == 0); }
	
	public static boolean doesDataPointExist(long dataPoint) { return dataPoint != EMPTY_DATA; }
	
	public static int getColor(long dataPoint)
	{
		long alpha = getAlpha(dataPoint);
		return (int) (((dataPoint >>> COLOR_SHIFT) & COLOR_MASK) | (alpha << (ALPHA_SHIFT - COLOR_SHIFT)));
	}
	
	/** Return (>0) if dataA should replace dataB, (0) if equal, (<0) if dataB should replace dataA */
	public static int compareDatapointPriority(long dataA, long dataB) { return (int) ((dataA >> COMPARE_SHIFT) - (dataB >> COMPARE_SHIFT)); }
	
	/** This is used to convert a dataPoint to string (useful for the print function) */
	@SuppressWarnings("unused")
	public static String toString(long dataPoint)
	{
		if (!doesDataPointExist(dataPoint))
		{
			return "null";
		}
		else if (isVoid(dataPoint))
		{
			return "void";
		}
		else
		{
			return "Y+:" + getYMax(dataPoint) +
					" Y-:" + getYMin(dataPoint) +
					" argb:" + getAlpha(dataPoint) + " " +
					getRed(dataPoint) + " " +
					getGreen(dataPoint) + " " +
					getBlue(dataPoint) +
					" BL:" + getLightBlock(dataPoint) +
					" SL:" + getLightSky(dataPoint) +
					" MAT:" + getBlockMaterialId(dataPoint) + "["+ EDhApiBlockMaterial.getFromIndex(getBlockMaterialId(dataPoint))+"]";
		}
	}
	
	
	
	//=================//
	// ColumnArrayView //
	//=================//
	// TODO this should probably be moved
	
	// TODO what is the purpose of these?
	//these were needed by the old logic for mergeMultiData(),
	//which has now been replaced by RenderDataPointReducingList.
	//so, these are no longer necessary, but left here for the same
	//reason the old logic is left here: in case it's ever needed again.
	/*
	private static final ThreadLocal<int[]> tLocalIndices = new ThreadLocal<>();
	private static final ThreadLocal<boolean[]> tLocalIncreaseIndex = new ThreadLocal<>();
	private static final ThreadLocal<boolean[]> tLocalIndexHandled = new ThreadLocal<>();
	private static final ThreadLocal<short[]> tLocalHeightAndDepth = new ThreadLocal<>();
	private static final ThreadLocal<int[]> tDataIndexCache = new ThreadLocal<>();
	*/
	
	/**
	 * This method merge column of multiple data together
	 *
	 * @param sourceData one or more columns of data
	 * @param output one column of space for the result to be written to
	 */
	public static void mergeMultiData(IColumnDataView sourceData, ColumnArrayView output)
	{
		int target = output.verticalSize();
		if (target <= 0)
		{
			// I expect this to never be the case,
			// but RenderDataPointReducingList handles it sanely,
			// so I might as well handle it sanely here too.
			output.fill(EMPTY_DATA);
		}
		else if (target == 1)
		{
			output.set(0, RenderDataPointReducingList.reduceToOne(sourceData));
			for (int index = 1, size = output.size(); index < size; index++)
			{
				output.set(index, EMPTY_DATA);
			}
		}
		else
		{
			try (RenderDataPointReducingList list = new RenderDataPointReducingList(sourceData))
			{
				list.reduce(output.verticalSize());
				list.copyTo(output);
			}
		}
	}
	
}