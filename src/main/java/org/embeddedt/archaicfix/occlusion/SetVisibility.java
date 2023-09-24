package org.embeddedt.archaicfix.occlusion;

import net.minecraft.util.EnumFacing;

import java.util.Set;

public class SetVisibility {

	private static final int COUNT_FACES = EnumFacing.values().length;

	public static long setManyVisible(long bitSet, Set<EnumFacing> faces) {

        for (EnumFacing enumfacing : faces) {

            for (EnumFacing enumfacing1 : faces) {
                bitSet = setVisible(bitSet, enumfacing, enumfacing1, true);
            }
        }
		return bitSet;
	}

	public static long setVisible(long bitSet, EnumFacing from, EnumFacing to, boolean visible) {
		bitSet = setBit(bitSet, from.ordinal() + to.ordinal() * COUNT_FACES, visible);
		bitSet = setBit(bitSet, to.ordinal() + from.ordinal() * COUNT_FACES, visible);

		return bitSet;
	}

	private static long setBit(long bitSet, int index, boolean value) {
		if(value) {
			bitSet |= (1L << index);
		} else {
			bitSet &= ~(1L << index);
		}
		return bitSet;
	}

	public static boolean isVisible(long bitSet, EnumFacing from, EnumFacing to) {
		return from == null || to == null || (bitSet & (1L << (from.ordinal() + to.ordinal() * COUNT_FACES))) != 0;
	}

	public static String toString(long bitSet) {

		StringBuilder stringbuilder = new StringBuilder();
		stringbuilder.append(' ');
		EnumFacing[] aenumfacing = EnumFacing.values();
		int i = aenumfacing.length;
		int j;
		EnumFacing enumfacing;

		for (j = 0; j < i; ++j) {
			enumfacing = aenumfacing[j];
			stringbuilder.append(' ').append(enumFacingToStringFixed(enumfacing).toUpperCase().charAt(0));
		}

		stringbuilder.append('\n');
		aenumfacing = EnumFacing.values();
		i = aenumfacing.length;

		for (j = 0; j < i; ++j) {
			enumfacing = aenumfacing[j];
			stringbuilder.append(enumFacingToStringFixed(enumfacing).toUpperCase().charAt(0));
			EnumFacing[] aenumfacing1 = EnumFacing.values();
			int k = aenumfacing1.length;

            for (EnumFacing enumfacing1 : aenumfacing1) {
                if (enumfacing == enumfacing1) {
                    stringbuilder.append("  ");
                } else {
                    boolean flag = isVisible(bitSet, enumfacing, enumfacing1);
                    stringbuilder.append(' ').append(flag ? 'Y' : 'n');
                }
            }

			stringbuilder.append('\n');
		}

		return stringbuilder.toString();
	}

	// Do not trust MCP.
	private static String enumFacingToStringFixed(EnumFacing f) {
		return new String[]{"DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"}[f.ordinal()];
	}

}
