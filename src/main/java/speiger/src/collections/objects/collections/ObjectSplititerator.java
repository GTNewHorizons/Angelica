package speiger.src.collections.objects.collections;

import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A Type Specific Split-Iterator that reduces boxing/unboxing
 * It fills the gaps of the java and uses this collection interfaces
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectSplititerator<T> extends Spliterator<T>, ObjectIterator<T>
{
	@Override
	default void forEachRemaining(Consumer<? super T> action) { ObjectIterator.super.forEachRemaining(action); }
}