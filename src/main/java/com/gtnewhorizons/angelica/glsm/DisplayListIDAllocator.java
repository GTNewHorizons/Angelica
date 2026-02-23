package com.gtnewhorizons.angelica.glsm;

/*
 * Ported from Mesa's util_idalloc (src/util/u_idalloc.c).
 *
 * Original copyright and license:
 *
 * Copyright 2017 Valve Corporation
 * All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Original author: Samuel Pitoiset <samuel.pitoiset@gmail.com>
 */

/**
 * Bitmap ID allocator for display list IDs.
 *
 * <p>Bitmap-based: each bit in the {@code data} array represents one ID.
 * Bit set = allocated, bit clear = free. ID 0 is reserved (never returned).
 */
public class DisplayListIDAllocator {

    private int[] data;
    private int numElements;       // number of uint32 words allocated
    private int numSetElements;    // highest word index with any bit set + 1
    private int lowestFreeIdx;     // optimization: skip fully-allocated words

    public DisplayListIDAllocator() {
        this(32); // 1024 IDs initially
    }

    public DisplayListIDAllocator(int initialWords) {
        if (initialWords < 1) initialWords = 1;
        this.data = new int[initialWords];
        this.numElements = initialWords;
        this.numSetElements = 0;
        this.lowestFreeIdx = 0;
        // Reserve ID 0 (never returned), matching Mesa's skip_zero pattern
        data[0] = 1;
        numSetElements = 1;
    }

    public int allocRange(int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        if (count == 1) return allocSingle();
        return allocMultiple(count);
    }

    private int allocSingle() {
        for (int i = lowestFreeIdx; i < numElements; i++) {
            if (data[i] == 0xFFFFFFFF) continue;

            int bit = Integer.numberOfTrailingZeros(~data[i]);
            data[i] |= 1 << bit;
            lowestFreeIdx = i;
            numSetElements = Math.max(numSetElements, i + 1);
            return i * 32 + bit;
        }

        // No slots available, grow. Record old end, then mark first bit there.
        int oldNumElements = numElements;
        resize(Math.max(numElements, 1) * 2);
        lowestFreeIdx = oldNumElements;
        data[oldNumElements] |= 1;
        numSetElements = Math.max(numSetElements, oldNumElements + 1);
        return oldNumElements * 32;
    }

    private int allocMultiple(int num) {
        int numWords = divRoundUp(num, 32);
        int base = findFreeBlock(lowestFreeIdx);
        boolean found = false;

        while (!found) {
            int i;
            for (i = base; i < numElements && i - base < numWords && data[i] == 0; i++)
                ;

            if (i - base == numWords) {
                found = true;
            } else if (i == numElements) {
                // Hit end — base is valid: data[base..end] are zero, resize extends the run into newly zeroed words.
                resize(numElements * 2 + numWords);
                found = true;
            } else {
                // Non-zero word at i, skip past it
                base = i + 1;
            }
        }

        // Mark bits as used
        int fullWords = num / 32;
        int remainder = num % 32;
        for (int i = base; i < base + fullWords; i++) {
            data[i] = 0xFFFFFFFF;
        }
        if (remainder != 0) {
            data[base + fullWords] |= (1 << remainder) - 1;
        }

        if (lowestFreeIdx == base) {
            lowestFreeIdx = base + num / 32;
        }
        numSetElements = Math.max(numSetElements, base + numWords);

        return base * 32;
    }

    private int findFreeBlock(int start) {
        for (int i = start; i < numElements; i++) {
            if (data[i] == 0) return i;
        }
        return numElements;
    }

    public void free(int id) {
        if (id <= 0) return; // ID 0 is reserved
        int idx = id / 32;
        if (idx >= numElements) return;

        lowestFreeIdx = Math.min(idx, lowestFreeIdx);
        data[idx] &= ~(1 << (id % 32));

        if (numSetElements == idx + 1) {
            while (numSetElements > 0 && data[numSetElements - 1] == 0) {
                numSetElements--;
            }
        }
    }

    public void freeRange(int base, int count) {
        if (count <= 0 || base <= 0) return;

        int startIdx = base / 32;
        if (startIdx >= numElements) return;

        int end = base + count;
        for (int id = base; id < end; id++) {
            int idx = id / 32;
            if (idx >= numElements) break;
            data[idx] &= ~(1 << (id % 32));
        }

        lowestFreeIdx = Math.min(startIdx, lowestFreeIdx);
        while (numSetElements > 0 && data[numSetElements - 1] == 0) {
            numSetElements--;
        }
    }

    public boolean isAllocated(int id) {
        if (id <= 0) return false;
        int idx = id / 32;
        if (idx >= numSetElements) return false;
        return (data[idx] & (1 << (id % 32))) != 0;
    }

    private void resize(int newNumElements) {
        if (newNumElements <= numElements) return;
        int[] newData = new int[newNumElements];
        System.arraycopy(data, 0, newData, 0, numElements);
        data = newData;
        numElements = newNumElements;
    }

    private static int divRoundUp(int a, int b) {
        return (a + b - 1) / b;
    }
}
