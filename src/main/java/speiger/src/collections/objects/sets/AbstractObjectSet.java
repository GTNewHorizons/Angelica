package speiger.src.collections.objects.sets;

import java.util.Objects;
import java.util.Set;

import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;

/**
 * Abstract Type Specific Set that reduces boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public abstract class AbstractObjectSet<T> extends AbstractObjectCollection<T> implements ObjectSet<T>
{
	@Override
	public T addOrGet(T o) { throw new UnsupportedOperationException(); }
	
	@Override
	public abstract ObjectIterator<T> iterator();
	@Override
	public AbstractObjectSet<T> copy() { throw new UnsupportedOperationException(); }
	
	@Override
	public int hashCode() {
		int hashCode = 0;
		ObjectIterator<T> i = iterator();
		while(i.hasNext())
			hashCode += Objects.hashCode(i.next());
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