package speiger.src.collections.utils;

/**
 * Helper class that is used for HashMap functions reduce duplicated code
 */
public class HashUtil
{
	/** Minimum HashMap Capacity */
	public static final int DEFAULT_MIN_CAPACITY = 16;
	/** Minimum ConcurrentHashMap Concurrency */
	public static final int DEFAULT_MIN_CONCURRENCY = 4;
	/** Default HashMap Load Factor */
	public static final float DEFAULT_LOAD_FACTOR = 0.75F;
	/** HashMap Load Factor with reduced hash collisions but more memory usage */
	public static final float FAST_LOAD_FACTOR = 0.5F;
	/** HashMap Load Factor with minimal hash collisions but more memory usage */
	public static final float FASTER_LOAD_FACTOR = 0.25F;
	
	/** 2<sup>32</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
	private static final int INT_PHI = 0x9E3779B9;
	/** The reciprocal of {@link #INT_PHI} modulo 2<sup>32</sup>. */
	private static final int INV_INT_PHI = 0x144cbc89;
	
	/** Quickly mixes the bits of an integer.
	 *
	 * <p>This method mixes the bits of the argument by multiplying by the golden ratio and
	 * xorshifting the result. It is borrowed from <a href="https://github.com/leventov/Koloboke">Koloboke</a>, 
	 *
	 * @param x an integer.
	 * @return a hash value obtained by mixing the bits of {@code x}.
	 * @see #invMix(int)
	 */
	public static int mix(final int x) {
		final int h = x * INT_PHI;
		return h ^ (h >>> 16);
	}
	
	/** The inverse of {@link #mix(int)}. This method is mainly useful to create unit tests.
	 *
	 * @param x an integer.
	 * @return a value that passed through {@link #mix(int)} would give {@code x}.
	 */
	public static int invMix(final int x) {
		return (x ^ x >>> 16) * INV_INT_PHI;
	}
	
	/**
	 * Function that rounds up to the closest power of 2
	 * A modified version of https://stackoverflow.com/a/466242
	 * @param x that should be converted to the next power of two
	 * @return the input number rounded up to the next power of two
	 */
	public static int nextPowerOfTwo(int x) {
		return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
	}
	
	/**
	 * Function that rounds up to the closest power of 2
	 * A modified version of https://stackoverflow.com/a/466242
	 * @param x that should be converted to the next power of two
	 * @return the input number rounded up to the next power of two
	 */
	public static long nextPowerOfTwo(long x) {
		return 1L << (64 - Long.numberOfLeadingZeros(x - 1));
	}
	
	/**
	 * Function that finds out how many bits are required to store the number Provided
	 * @param value to get the required bits to store.
	 * @return the required bits to store that number
	 * @note Really useful for compression. Reducing data that is inserted into a GZIP algorithm.
	 */
	public static int getRequiredBits(int value) {
		return Integer.bitCount(nextPowerOfTwo(value+1)-1);
	}
	
	/**
	 * Function that finds out how many bits are required to store the number Provided
	 * @param value to get the required bits to store.
	 * @return the required bits to store that number
	 * @note Really useful for compression. Reducing data that is inserted into a GZIP algorithm.
	 */
	public static int getRequiredBits(long value) {
		return Long.bitCount(nextPowerOfTwo(value+1L)-1L);
	}
	
	/**
	 * Helper function that creates the ideal array size for HashMap
	 * @param size the original array size
	 * @param loadFactor the load factor
	 * @return the new array size
	 */
	public static int arraySize(int size, float loadFactor) {
		return (int)Math.min(1 << 30, Math.max(2, nextPowerOfTwo((long)Math.ceil(size / loadFactor))));
	}
}
