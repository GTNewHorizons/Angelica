package net.coderbot.iris.layer;

import net.coderbot.batchedentityrendering.impl.WrappableRenderType;
import com.gtnewhorizons.angelica.compat.mojang.RenderPhase;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class OuterWrappedRenderType extends RenderLayer implements WrappableRenderType {
	private final RenderPhase extra;
	private final RenderLayer wrapped;

	private OuterWrappedRenderType(String name, RenderLayer wrapped, RenderPhase extra) {
		super(name, wrapped.format(), wrapped.mode(), wrapped.bufferSize(),
			wrapped.hasCrumbling(), shouldSortOnUpload(wrapped), wrapped::startDrawing, wrapped::endDrawing);

		this.extra = extra;
		this.wrapped = wrapped;
	}

	public static OuterWrappedRenderType wrapExactlyOnce(String name, RenderLayer wrapped, RenderPhase extra) {
		if (wrapped instanceof OuterWrappedRenderType outerWrappedRenderType) {
			wrapped = outerWrappedRenderType.unwrap();
		}

		return new OuterWrappedRenderType(name, wrapped, extra);
	}

	@Override
	public void startDrawing() {
		extra.startDrawing();

		super.startDrawing();
	}

	@Override
	public void endDrawing() {
		super.endDrawing();

		extra.endDrawing();
	}

	@Override
	public RenderLayer unwrap() {
		return this.wrapped;
	}

	@Override
	public Optional<RenderLayer> outline() {
		return this.wrapped.outline();
	}

	@Override
	public boolean isOutline() {
		return this.wrapped.isOutline();
	}

	@Override
	public boolean equals(@Nullable Object object) {
		if (object == null) {
			return false;
		}

		if (object.getClass() != this.getClass()) {
			return false;
		}

		OuterWrappedRenderType other = (OuterWrappedRenderType) object;

		return Objects.equals(this.wrapped, other.wrapped) && Objects.equals(this.extra, other.extra);
	}

	@Override
	public int hashCode() {
		// Add one so that we don't have the exact same hash as the wrapped object.
		// This means that we won't have a guaranteed collision if we're inserted to a map alongside the unwrapped object.
		return this.wrapped.hashCode() + 1;
	}

	@Override
	public String toString() {
		return "iris_wrapped:" + this.wrapped.toString();
	}

	private static boolean shouldSortOnUpload(RenderLayer type) {
        return true;
        // TODO: Iris
//		return ((RenderTypeAccessor) type).shouldSortOnUpload();
	}
}
