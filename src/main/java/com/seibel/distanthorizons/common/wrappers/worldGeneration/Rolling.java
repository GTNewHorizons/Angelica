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


package com.seibel.distanthorizons.common.wrappers.worldGeneration;

//FIXME: Move this outside the WorldGenerationStep thingy
public class Rolling
{

    private final int size;
    private double total = 0d;
    private int index = 0;
    private final double[] samples;

    public Rolling(int size)
    {
        this.size = size;
        samples = new double[size];
        for (int i = 0; i < size; i++)
        {
            samples[i] = 0d;
        }
    }

    public void add(double x)
    {
        total -= samples[index];
        samples[index] = x;
        total += x;
        if (++index == size)
            index = 0; // cheaper than modulus
    }

    public double getAverage()
    {
        return size == 0 ? 0 : total / size;
    }

}
