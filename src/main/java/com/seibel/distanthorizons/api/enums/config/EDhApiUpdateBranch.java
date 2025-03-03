package com.seibel.distanthorizons.api.enums.config;

import com.seibel.distanthorizons.coreapi.ModInfo;

/**
 * AUTO, <br>
 * STABLE, <br>
 * NIGHTLY, <br><br>
 *
 * @since API 2.1.0
 * @version 2024-6-8
 */
public enum EDhApiUpdateBranch
{
	AUTO,
	STABLE,
	NIGHTLY;
	
	
	
	/** 
	 * If the updateBranch value is {@link EDhApiUpdateBranch#AUTO}
	 * this method will convert it either to {@link EDhApiUpdateBranch#STABLE} or {@link EDhApiUpdateBranch#NIGHTLY}
	 * based on this jar's state. <Br><br>
	 * 
	 * If updateBranch is {@link EDhApiUpdateBranch#STABLE} or {@link EDhApiUpdateBranch#NIGHTLY}
	 * it just returns.
	 */
	public static EDhApiUpdateBranch convertAutoToStableOrNightly(EDhApiUpdateBranch updateBranch)
	{
		if (updateBranch != EDhApiUpdateBranch.AUTO)
		{
			return updateBranch;
		}
		else
		{
			return ModInfo.IS_DEV_BUILD ? EDhApiUpdateBranch.NIGHTLY : EDhApiUpdateBranch.STABLE;
		}
	}
	
}
