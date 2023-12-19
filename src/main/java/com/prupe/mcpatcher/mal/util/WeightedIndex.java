package com.prupe.mcpatcher.mal.util;

abstract public class WeightedIndex {

    private static final long K1 = 0xb492b66fbe98f273L;
    private static final long KMUL = 0x9ddfea08eb382d69L;

    final int size;

    public static WeightedIndex create(int size) {
        if (size <= 0) {
            return null;
        }

        return new WeightedIndex(size) {

            @Override
            public int choose(long key) {
                return mod(key, size);
            }

            @Override
            public String toString() {
                return "unweighted";
            }
        };
    }

    public static WeightedIndex create(int size, final String weightList) {
        if (size <= 0 || weightList == null) {
            return create(size);
        }

        final int[] weights = new int[size];
        int sum1 = 0;
        boolean useWeight = false;
        String[] list = weightList.trim()
            .split("\\s+");
        for (int i = 0; i < size; i++) {
            if (i < list.length && list[i].matches("^\\d+$")) {
                weights[i] = Math.max(Integer.parseInt(list[i]), 0);
            } else {
                weights[i] = 1;
            }
            if (i > 0 && weights[i] != weights[0]) {
                useWeight = true;
            }
            sum1 += weights[i];
        }
        if (!useWeight || sum1 <= 0) {
            return create(size);
        }
        final int sum = sum1;

        return new WeightedIndex(size) {

            @Override
            public int choose(long key) {
                int index;
                int m = mod(key, sum);
                for (index = 0; index < size - 1 && m >= weights[index]; index++) {
                    m -= weights[index];
                }
                return index;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("%(");
                for (int i = 0; i < weights.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(String.format("%.1f", 100.0 * weights[i] / sum));
                }
                sb.append(")");
                return sb.toString();
            }
        };
    }

    protected WeightedIndex(int size) {
        this.size = size;
    }

    protected final int mod(long n, int modulus) {
        return (int) (((n >> 32) ^ n) & 0x7fffffff) % modulus;
    }

    abstract public int choose(long key);

    // adapted from CityHash http://code.google.com/p/cityhash/source/browse/trunk/ (MIT license)
    public static long hash128To64(int i, int j, int k, int l) {
        return hash128To64(((long) i << 32) | ((long) j & 0xffffffffL), ((long) k << 32) | ((long) l & 0xffffffffL));
    }

    public static long hash128To64(long a, long b) {
        a = shiftMix(a * K1) * K1;
        long c = b * K1 + mix128to64(a, b);
        long d = shiftMix(a + b);
        a = mix128to64(a, c);
        b = mix128to64(d, b);
        return a ^ b ^ mix128to64(b, a);
    }

    private static long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

    private static long mix128to64(long u, long v) {
        long a = shiftMix((u ^ v) * KMUL);
        long b = shiftMix((u ^ a) * KMUL);
        b *= KMUL;
        return b;
    }
}
