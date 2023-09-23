package speiger.src.collections.ints.sets;

import java.util.Set;

import speiger.src.collections.ints.collections.AbstractIntCollection;
import speiger.src.collections.ints.collections.IntIterator;

/**
 * Abstract Type Specific Set that reduces boxing/unboxing
 */
public abstract class AbstractIntSet extends AbstractIntCollection implements IntSet
{
	@Override
	public abstract IntIterator iterator();
	@Override
	public AbstractIntSet copy() { throw new UnsupportedOperationException(); }
	
	@Override
	public int hashCode() {
		int hashCode = 0;
		IntIterator i = iterator();
		while(i.hasNext())
			hashCode += Integer.hashCode(i.nextInt());
		return hashCode;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof Set))
			return false;
		Set<?> l = (Set<?>)o;
		if(l.size() != size()) return false;
		try {
			return containsAll(l);
		} catch (ClassCastException | NullPointerException unused) {
			return false;
		}
	}
}