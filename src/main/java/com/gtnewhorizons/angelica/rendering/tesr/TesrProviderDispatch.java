package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

public final class TesrProviderDispatch {

    private TesrProviderDispatch() {}

    public static int resolveBlockEntityId(TileEntity te) {
        if (te == null) return 0;
        final Block block = te.getBlockType();
        if (block == null) return 0;
        final Reference2ObjectMap<Block, Int2IntMap> matches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if (matches == null) return 0;
        final Int2IntMap metaMap = matches.get(block);
        if (metaMap == null) return 0;
        return Math.max(0, metaMap.get(te.getBlockMetadata()));
    }

    public static boolean tryRender(Object renderer, TileEntity te, double x, double y, double z) {
        if (!(renderer instanceof TesrMeshProvider provider)) return false;
        final Object key = provider.angelica$meshKey(te);
        if (key == null) return false;

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(resolveBlockEntityId(te));
        GLStateManager.glPushMatrix();
        provider.angelica$transform(te, x, y, z);
        AngelicaTesrMeshCache.INSTANCE.renderCached(key, provider.angelica$meshDirty(te), provider, te);
        GLStateManager.glPopMatrix();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        return true;
    }
}
