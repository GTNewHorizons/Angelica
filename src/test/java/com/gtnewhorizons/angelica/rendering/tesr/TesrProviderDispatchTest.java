package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TesrProviderDispatchTest {

    private static final class FakeProvider implements TesrMeshProvider {
        private final Object key;
        FakeProvider(Object key) { this.key = key; }
        @Override public Object angelica$meshKey(TileEntity te) { return key; }
        @Override public void angelica$build(TesrMeshSink sink, TileEntity te) {}
    }

    @Test
    void nonProviderIsNotHandled() {
        assertFalse(TesrProviderDispatch.tryRender(new Object(), null, 0, 0, 0));
    }

    @Test
    void nullMeshKeyFallsBackToNormalRender() {
        assertFalse(TesrProviderDispatch.tryRender(new FakeProvider(null), null, 0, 0, 0));
    }

    @Test
    void nullTileEntityResolvesToZeroBlockEntityId() {
        org.junit.jupiter.api.Assertions.assertEquals(0, TesrProviderDispatch.resolveBlockEntityId(null));
    }
}
