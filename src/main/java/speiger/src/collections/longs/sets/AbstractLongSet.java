package speiger.src.collections.longs.sets;

import java.util.Set;

import speiger.src.collections.longs.collections.AbstractLongCollection;
import speiger.src.collections.longs.collections.LongIterator;

/**
 * Abstract Type Specific Set that reduces boxing/unboxing
 */
public abstract class AbstractLongSet extends AbstractLongCollection implements LongSet
{
	@Override
	public abstract LongIterator iterator();
	@Override
	public AbstractLongSet copy() { throw new UnsupportedOperationException(); }
	
	@Override
	public int hashCode() {
		int hashCode = 0;
		LongIterator i = iterator();
		while(i.hasNext())
			hashCode += Long.hashCode(i.nextLong());
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