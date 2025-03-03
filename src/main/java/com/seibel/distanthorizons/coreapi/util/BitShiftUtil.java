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

package com.seibel.distanthorizons.coreapi.util;

/**
 * A list of helper methods to make code easier to read. <br>
 * Specifically written because bit shifts short circuit James' brain.
 *
 * @author James Seibel
 * @version 2022-11-6
 */
public class BitShiftUtil
{
	/**
	 * Equivalent to: <br>
	 * {@literal 1 << value, } <br>
	 * 2^value, <br>
	 * Math.pow(2, value) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same. <br><br>
	 *
	 * Can also be used to replace bit shifts in the format: <br>
	 * {@literal multiplier << value; } <br>
	 * multiplier * powerOfTwo(value);
	 */
	public static int powerOfTwo(int value) { return 1 << value; }
	/** see {@link BitShiftUtil#powerOfTwo(int)} for documentation */
	public static long powerOfTwo(long value) { return 1L << value; }
	
	
	/**
	 * Equivalent to: <br>
	 * value >> 1, <br>
	 * value / 2 <br><br>
	 *
	 * Note: value / 2 isn't identical for negative values
	 */
	public static int half(int value) { return value >> 1; }
	/** see {@link BitShiftUtil#half(int)} for documentation */
	public static long half(long value) { return value >> 1; }
	
	
	/**
	 * Equivalent to: <br>
	 * value >> power, <br>
	 * value / 2^power <br><br>
	 *
	 * Note: value / 2^power isn't identical for negative values
	 */
	public static int divideByPowerOfTwo(int value, int power) { return value >> power; }
	/** see {@link BitShiftUtil#divideByPowerOfTwo(int, int)} for documentation */
	public static long divideByPowerOfTwo(long value, long power) { return value >> power; }
	
	
	/**
	 * Equivalent to: <br>
	 * {@literal value << 1, } <br>
	 * value^2, <br>
	 * Math.pow(value, 2) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same.
	 */
	public static int square(int value) { return value << 1; }
	/** see {@link BitShiftUtil#square(int)} for documentation */
	public static long square(long value) { return value << 1; }
	
	
	/**
	 * Equivalent to: <br>
	 * {@literal value << power, } <br>
	 * value^power, <br>
	 * Math.pow(value, power) <br><br>
	 *
	 * Note: Math.pow() isn't identical for large values where bits would be lost in the shift, however for medium to small values they function the same.
	 */
	public static int pow(int value, int power) { return value << power; }
	/** see {@link BitShiftUtil#pow(int, int)} for documentation */
	public static long pow(long value, long power) { return value << power; }
	
}
