package speiger.src.collections.ints.collections;

import java.util.Spliterator.OfPrimitive;
import java.util.function.Consumer;

import speiger.src.collections.ints.functions.IntConsumer;

/**
 * A Type Specific Split-Iterator that reduces boxing/unboxing
 * It fills the gaps of the java and uses this collection interfaces
 */
public interface IntSplititerator extends OfPrimitive<Integer, IntConsumer, IntSplititerator>, IntIterator
{
	@Override
	default void forEachRemaining(IntConsumer action) { IntIterator.super.forEachRemaining(action); }
	@Override
	@Deprecated
	default void forEachRemaining(Consumer<? super Integer> action) { IntIterator.super.forEachRemaining(action); }
}