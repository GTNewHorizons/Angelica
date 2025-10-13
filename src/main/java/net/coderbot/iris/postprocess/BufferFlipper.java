package net.coderbot.iris.postprocess;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class BufferFlipper {
	private final IntSet flippedBuffers;

	public BufferFlipper() {
		this.flippedBuffers = new IntOpenHashSet();
	}

	public void flip(int target) {
		// colortex6 carries material/mask data that the gbuffer populates every frame.
		// If we allow it to flip, the alternate texture remains empty and later copy-back
		// wipes the fresh mask, breaking TAA mask lookups (notably on foliage/transparents).
		if (target == 6) {
			return;
		}

		if (!flippedBuffers.remove(target)) {
			// If the target wasn't in the set, add it to the set.
			flippedBuffers.add(target);
		}
	}

	/**
	 * Returns true if this buffer is flipped.
	 *
	 * If this buffer is not flipped, then users should write to the alternate variant and read from the main variant.
	 *
	 * If this buffer is flipped, then users should write to the main variant and read from the alternate variant.
	 */
	public boolean isFlipped(int target) {
		return flippedBuffers.contains(target);
	}

	public IntIterator getFlippedBuffers() {
		return flippedBuffers.iterator();
	}

	public ImmutableSet<Integer> snapshot() {
		return ImmutableSet.copyOf(flippedBuffers);
	}
}
