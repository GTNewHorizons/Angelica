package com.seibel.distanthorizons.api.enums.rendering;

/**
 * contains the indices used by shaders to determine 
 * how different block types should be rendered. <br><br>
 * 
 * UNKNOWN, <br>
 * LEAVES, <br>
 * STONE, <br>
 * WOOD, <br>
 * METAL, <br>
 * DIRT, <br>
 * LAVA, <br>
 * DEEPSLATE, <br>
 * SNOW, <br>
 * SAND, <br>
 * TERRACOTTA, <br>
 * NETHER_STONE, <br>
 * WATER, <br>
 * GRASS, <br>
 * AIR, <br>
 * ILLUMINATED, <br>
 * 
 * @author IMS
 * @author James Seibel
 * @since API 3.0.0
 * @version 2024-7-11
 */
public enum EDhApiBlockMaterial
{
	UNKNOWN(0),
	LEAVES(1),
	STONE(2),
	WOOD(3),
	METAL(4),
	DIRT(5),
	LAVA(6),
	DEEPSLATE(7),
	SNOW(8),
	SAND(9),
	TERRACOTTA(10),
	NETHER_STONE(11),
	WATER(12),
	GRASS(13),
	/** shouldn't normally be needed, but just in case */
	AIR(14),
	ILLUMINATED(15); // Max value
	
	
	
	public final byte index;
	
	EDhApiBlockMaterial(int index) { this.index = (byte)index;}
	
	public static EDhApiBlockMaterial getFromIndex(int index)
	{
		for(EDhApiBlockMaterial material : EDhApiBlockMaterial.values())
		{
			if (material.index == index)
			{
				return material;
			}
		}
		
		return EDhApiBlockMaterial.UNKNOWN;
	}
	
}
