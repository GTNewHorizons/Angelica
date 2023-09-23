package speiger.src.collections.longs.collections;

import java.util.Spliterator.OfPrimitive;
import java.util.function.Consumer;

import speiger.src.collections.longs.functions.LongConsumer;

/**
 * A Type Specific Split-Iterator that reduces boxing/unboxing
 * It fills the gaps of the java and uses this collection interfaces
 */
public interface LongSplititerator extends OfPrimitive<Long, LongConsumer, LongSplititerator>, LongIterator
{
	@Override
	default void forEachRemaining(LongConsumer action) { LongIterator.super.forEachRemaining(action); }
	@Override
	@Deprecated
	default void forEachRemaining(Consumer<? super Long> action) { LongIterator.super.forEachRemaining(action); }
}