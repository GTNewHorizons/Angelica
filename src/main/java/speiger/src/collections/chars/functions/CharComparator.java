package speiger.src.collections.chars.functions;

import java.util.Comparator;
import java.util.Objects;

/**
 * Type-Specific Class for Comparator to reduce (un)boxing
 */
public interface CharComparator extends Comparator<Character> 
{
	/**
	 * Type-Specific compare function to reduce (un)boxing
	 * @param o1 the first object to be compared.
	 * @param o2 the second object to be compared.
	 * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
	 * @see Comparator#compare(Object, Object)
	 */
	int compare(char o1, char o2);
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default int compare(Character o1, Character o2) {
		return compare(o1.charValue(), o2.charValue());
	}
	
	/**
	 * A Wrapper function to convert a Non-Type-Specific Comparator to a Type-Specific-Comparator
	 * @param c comparator to convert
	 * @return the wrapper of the comparator
	 * @throws NullPointerException if the comparator is null
	 */
	public static CharComparator of(Comparator<Character> c) {
		Objects.requireNonNull(c);
		return (K, V) -> c.compare(Character.valueOf(K), Character.valueOf(V));
	}
	
	@Override
	public default CharComparator reversed() {
		return new Reversed(this);
	}
	
	/**
	 * A Type Specific Reversed Comparator to reduce boxing/unboxing
	 */
	static class Reversed implements CharComparator
	{
		CharComparator original;
		
		/**
		 * default constructor
		 * @param original that is going to be reversed
		 */
		public Reversed(CharComparator original) {
			this.original = original;
		}
		
		public int compare(char o1, char o2) {
			return original.compare(o2, o1);
		}
		
		@Override
		public CharComparator reversed() {
			return original;
		}
	}
}