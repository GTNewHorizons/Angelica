package net.coderbot.iris.fantastic;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import java.util.function.Function;

public interface WrappingMultiBufferSource {
	void pushWrappingFunction(Function<RenderLayer, RenderLayer> wrappingFunction);
	void popWrappingFunction();
	void assertWrapStackEmpty();
}
