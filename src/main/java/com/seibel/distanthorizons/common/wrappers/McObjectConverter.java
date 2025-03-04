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

package com.seibel.distanthorizons.common.wrappers;

import java.nio.FloatBuffer;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.math.Mat4f;

import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Matrix4f;

/**
 * This class converts to and from Minecraft objects (Ex: Matrix4f)
 * and objects we created (Ex: Mat4f).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class McObjectConverter
{
	private static int bufferIndex(int x, int y)
	{
		return y * 4 + x;
	}


	/** 4x4 float matrix converter */
	@Deprecated
	public static Mat4f Convert(
			Matrix4f mcMatrix)
	{
		FloatBuffer buffer = FloatBuffer.allocate(16);
		storeMatrix(mcMatrix, buffer);
		Mat4f matrix = new Mat4f(buffer);
		// TODO ? matrix.transpose();
		return matrix;
	}
	/** Taken from Minecraft's com.mojang.math.Matrix4f class from 1.18.2 */
	private static void storeMatrix(
			Matrix4f matrix,
			FloatBuffer buffer)
	{
		buffer.put(bufferIndex(0, 0), matrix.m00());
		buffer.put(bufferIndex(0, 1), matrix.m01());
		buffer.put(bufferIndex(0, 2), matrix.m02());
		buffer.put(bufferIndex(0, 3), matrix.m03());
		buffer.put(bufferIndex(1, 0), matrix.m10());
		buffer.put(bufferIndex(1, 1), matrix.m11());
		buffer.put(bufferIndex(1, 2), matrix.m12());
		buffer.put(bufferIndex(1, 3), matrix.m13());
		buffer.put(bufferIndex(2, 0), matrix.m20());
		buffer.put(bufferIndex(2, 1), matrix.m21());
		buffer.put(bufferIndex(2, 2), matrix.m22());
		buffer.put(bufferIndex(2, 3), matrix.m23());
		buffer.put(bufferIndex(3, 0), matrix.m30());
		buffer.put(bufferIndex(3, 1), matrix.m31());
		buffer.put(bufferIndex(3, 2), matrix.m32());
		buffer.put(bufferIndex(3, 3), matrix.m33());
	}


	static final ForgeDirection[] directions;
	static final EDhDirection[] lodDirections;
	static
	{
		EDhDirection[] lodDirs = EDhDirection.values();
		directions = new ForgeDirection[lodDirs.length];
		lodDirections = new EDhDirection[lodDirs.length];
		for (EDhDirection lodDir : lodDirs)
		{
            ForgeDirection dir;
			switch (lodDir.name().toUpperCase())
			{
				case "DOWN":
					dir = ForgeDirection.DOWN;
					break;
				case "UP":
					dir = ForgeDirection.UP;
					break;
				case "NORTH":
					dir = ForgeDirection.NORTH;
					break;
				case "SOUTH":
					dir = ForgeDirection.SOUTH;
					break;
				case "WEST":
					dir = ForgeDirection.WEST;
					break;
				case "EAST":
					dir = ForgeDirection.EAST;
					break;
				default:
					dir = null;
					break;
			}

			if (dir == null)
			{
				throw new IllegalArgumentException("Invalid direction on init mapping: " + lodDir);
			}
			directions[lodDir.ordinal()] = dir;
			lodDirections[dir.ordinal()] = lodDir;
		}
	}

	public static BlockPos Convert(DhBlockPos wrappedPos) { return new BlockPos(wrappedPos.getX(), wrappedPos.getY(), wrappedPos.getZ()); }
	public static ChunkPos Convert(DhChunkPos wrappedPos) { return new ChunkPos(wrappedPos.getX(), wrappedPos.getZ()); }

	public static ForgeDirection Convert(EDhDirection lodDirection) { return directions[lodDirection.ordinal()]; }
	public static EDhDirection Convert(ForgeDirection direction) { return lodDirections[direction.ordinal()]; }

}
