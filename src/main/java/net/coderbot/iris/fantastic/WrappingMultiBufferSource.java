package net.coderbot.iris.fantastic;

import com.gtnewhorizons.angelica.compat.mojang.RenderType;
import java.util.function.Function;

public interface WrappingMultiBufferSource {
	void pushWrappingFunction(Function<RenderType, RenderType> wrappingFunction);
	void popWrappingFunction();
	void assertWrapStackEmpty();
}
