package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;

/**
 * Used to give additional features to {@link DhApiTerrainDataPoint}
 * that shouldn't be available outside the API.
 */
public class DhApiTerrainDataPointUtil
{
	
	public static DhApiTerrainDataPoint createApiDatapoint(IDhApiLevelWrapper levelWrapper, FullDataPointIdMap mapping, byte detailLevel, long dataPoint)
	{ return createApiDatapoint(levelWrapper.getMinHeight(), mapping, detailLevel, dataPoint); }
	public static DhApiTerrainDataPoint createApiDatapoint(int minLevelHeight, FullDataPointIdMap mapping, byte detailLevel, long dataPoint)
	{
		IBlockStateWrapper blockState = mapping.getBlockStateWrapper(FullDataPointUtil.getId(dataPoint));
		IBiomeWrapper biomeWrapper = mapping.getBiomeWrapper(FullDataPointUtil.getId(dataPoint));
		
		int bottomY = FullDataPointUtil.getBottomY(dataPoint) + minLevelHeight;
		int height = FullDataPointUtil.getHeight(dataPoint);
		int topY = bottomY + height;
		
		return DhApiTerrainDataPoint.create(
				detailLevel,
				FullDataPointUtil.getBlockLight(dataPoint), FullDataPointUtil.getSkyLight(dataPoint),
				bottomY, topY,
				blockState, biomeWrapper);
	}
	
}
