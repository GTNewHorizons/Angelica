package com.seibel.distanthorizons.api.objects.data;

import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;

import java.util.List;

/**
 * Represents a single full LOD backed by Distant Horizons' ID system.
 * 
 * @see IDhApiWorldGenerator
 * @since API 4.0.0
 */
public interface IDhApiFullDataSource
{
	/** @return how many data columns wide this data source is */
	int getWidthInDataColumns();
	
	/** 
	 * Sets the data column at the relative X and Z position to the list given.
	 * The given list may be resorted based on the internal format DH requires.
	 * 
	 * @param relX can be in the range 0 to {@link IDhApiFullDataSource#getWidthInDataColumns()}-1 (both inclusive)
	 * @param relZ can be in the range 0 to {@link IDhApiFullDataSource#getWidthInDataColumns()}-1 (both inclusive)
	 * 
	 * @return the same columnDataPoints list after it has been imported into the data source.
	 *          The returned list and contained objects can then be re-used. 
	 *          
	 * @throws IndexOutOfBoundsException if the relative positions are negative or outside the bounds of this data source.
	 */
	List<DhApiTerrainDataPoint> setApiDataPointColumn(int relX, int relZ, List<DhApiTerrainDataPoint> columnDataPoints)
			throws IndexOutOfBoundsException, IllegalArgumentException;
	
	/** 
	 * @param relX can be in the range 0 to {@link IDhApiFullDataSource#getWidthInDataColumns()}-1 (both inclusive)
	 * @param relZ can be in the range 0 to {@link IDhApiFullDataSource#getWidthInDataColumns()}-1 (both inclusive)
	 * 
	 * @return a {@link List} of {@link DhApiTerrainDataPoint} representing the data for the given relative position. 
	 * 
	 * @throws IndexOutOfBoundsException if the relative positions are negative or outside the bounds of this data source.
	 */
	List<DhApiTerrainDataPoint> getApiDataPointColumn(int relX, int relZ) throws IndexOutOfBoundsException;
	
	
	
}
