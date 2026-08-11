package net.coderbot.iris.rendertarget;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ParityFlipState {

	private boolean enabled;
	private boolean finalized;
	private boolean odd;
	private ImmutableSet<Integer> parityBuffers = ImmutableSet.of();
	private final Map<ImmutableSet<Integer>, ImmutableSet<Integer>> oddSets = new HashMap<>();
	private final Function<ImmutableSet<Integer>, ImmutableSet<Integer>> xorParityFn = this::xorParity;

	public ParityFlipState(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isFinalized() {
		return finalized;
	}

	public boolean isOdd() {
		return odd;
	}

	public ImmutableSet<Integer> parityBuffers() {
		return parityBuffers;
	}

	public void finalizeParitySet(ImmutableSet<Integer> finalFlipped, IntList buffersToBeCleared) {
		finalized = true;
		if (!enabled) {
			return;
		}
		final ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
		for (int buffer : finalFlipped) {
			if (!buffersToBeCleared.contains(buffer)) {
				builder.add(buffer);
			}
		}
		parityBuffers = builder.build();
		if (parityBuffers.isEmpty()) {
			enabled = false;
		}
	}

	public void onFrameStart() {
		if (enabled && finalized) {
			odd = !odd;
		}
	}

	public void reset() {
		odd = false;
	}

	public ImmutableSet<Integer> resolve(ImmutableSet<Integer> even) {
		if (!odd) {
			return even;
		}
		return oddSets.computeIfAbsent(even, xorParityFn);
	}

	private ImmutableSet<Integer> xorParity(ImmutableSet<Integer> even) {
		final ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
		for (int buffer : even) {
			if (!parityBuffers.contains(buffer)) {
				builder.add(buffer);
			}
		}
		for (int buffer : parityBuffers) {
			if (!even.contains(buffer)) {
				builder.add(buffer);
			}
		}
		return builder.build();
	}

	public boolean affectsAny(int[] drawBuffers) {
		if (!enabled) {
			return false;
		}
		for (int buffer : drawBuffers) {
			if (parityBuffers.contains(buffer)) {
				return true;
			}
		}
		return false;
	}
}
