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

package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import io.netty.buffer.ByteBuf;

import java.awt.*;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class BeaconBeamDTO implements IBaseDTO<DhBlockPos>, INetworkObject
{
	public DhBlockPos blockPos;
	public Color color;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public BeaconBeamDTO(DhBlockPos blockPos, Color color)
	{
		this.blockPos = blockPos;
		this.color = color;
	}
	
	
	
	//=========//
	// network //
	//=========//
	
	@Override
	public void encode(ByteBuf out)
	{
		this.blockPos.encode(out);
		out.writeInt(this.color.getRGB());
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.blockPos = INetworkObject.decodeToInstance(new DhBlockPos(), in);
		this.color = new Color(in.readInt());
	}
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public DhBlockPos getKey() { return this.blockPos; }
	
	@Override
	public void close()
	{ /* no closing needed */ }
	
	
	
}
