package net.coderbot.batchedentityrendering.impl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import com.gtnewhorizons.angelica.compat.mojang.BufferBuilder;
import com.gtnewhorizons.angelica.compat.mojang.BufferSource;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.compat.mojang.VertexConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OldFullyBufferedMultiBufferSource extends BufferSource {
	private final Map<RenderLayer, BufferBuilder> bufferBuilders;
	private final Object2IntMap<RenderLayer> unused;
	private final Set<BufferBuilder> activeBuffers;
	private boolean flushed;

	private final Set<RenderLayer> typesThisFrame;
	private final List<RenderLayer> typesInOrder;

	public OldFullyBufferedMultiBufferSource() {
		super(new BufferBuilder(0), Collections.emptyMap());

		this.bufferBuilders = new HashMap<>();
		this.unused = new Object2IntOpenHashMap<>();
		this.activeBuffers = new HashSet<>();
		this.flushed = false;

		this.typesThisFrame = new HashSet<>();
		this.typesInOrder = new ArrayList<>();
	}

	private TransparencyType getTransparencyType(RenderLayer type) {
		while (type instanceof WrappableRenderType) {
			type = ((WrappableRenderType) type).unwrap();
		}

		if (type instanceof BlendingStateHolder) {
			return ((BlendingStateHolder) type).getTransparencyType();
		}

		// Default to "generally transparent" if we can't figure it out.
		return TransparencyType.GENERAL_TRANSPARENT;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer renderType) {
		flushed = false;

		BufferBuilder buffer = bufferBuilders.computeIfAbsent(renderType, type -> new BufferBuilder(type.bufferSize()));

		if (activeBuffers.add(buffer)) {
			buffer.begin(renderType.mode(), renderType.format());
		}

		if (this.typesThisFrame.add(renderType)) {
			// If we haven't seen this type yet, add it to the list of types to render.
			//
			// We keep track of the order that types were added, in order to ensure that if layers are not
			// sorted relative each other due to translucency, that they are sorted in the order that they were
			// drawn in.
			//
			// This is important for things like villager rendering, where the clothes and skin of villagers overlap
			// each other, so if the clothes are drawn before the skin, they appear to be poorly-clothed.
			this.typesInOrder.add(renderType);
		}

		// If this buffer is scheduled to be removed, unschedule it since it's now being used.
		unused.removeInt(renderType);

		return buffer;
	}

	@Override
	public void endBatch() {
		if (flushed) {
			return;
		}

		List<RenderLayer> removedTypes = new ArrayList<>();

		unused.forEach((unusedType, unusedCount) -> {
			if (unusedCount < 10) {
				// Removed after 10 frames of not being used
				return;
			}

			BufferBuilder buffer = bufferBuilders.remove(unusedType);
			removedTypes.add(unusedType);

			if (activeBuffers.contains(buffer)) {
				throw new IllegalStateException(
						"A buffer was simultaneously marked as inactive and as active, something is very wrong...");
			}
		});

		for (RenderLayer removed : removedTypes) {
			unused.removeInt(removed);
		}

		// Make sure translucent types are rendered after non-translucent ones.
		typesInOrder.sort(Comparator.comparing(this::getTransparencyType));

		for (RenderLayer type : typesInOrder) {
			drawInternal(type);
		}

		typesInOrder.clear();
		typesThisFrame.clear();

		flushed = true;
	}

	@Override
	public void endBatch(RenderLayer type) {
		// Disable explicit flushing
	}

	private void drawInternal(RenderLayer type) {
		BufferBuilder buffer = bufferBuilders.get(type);

		if (buffer == null) {
			return;
		}

		if (activeBuffers.remove(buffer)) {
			type.end(buffer, 0, 0, 0);
			buffer.clear();
		} else {
			// Schedule the buffer for removal next frame if it isn't used this frame.
			int unusedCount = unused.getOrDefault(type, 0);

			unusedCount += 1;

			unused.put(type, unusedCount);
		}
	}
}