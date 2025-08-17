package com.gtnewhorizons.angelica.loading.fml.compat.handlers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.loading.fml.compat.ICompatHandler;

import java.util.List;
import java.util.Map;

public class ImmersiveEngineeringCompatHandler implements ICompatHandler {

    @Override
    public Map<String, List<String>> getTileEntityNullGuard() {
        final List<String> renderWorldBlockExclusive = ImmutableList.of("renderWorldBlock");
        return ImmutableMap.<String, List<String>>builder()
            .put("blusunrize.immersiveengineering.client.render.BlockRenderClothDevices", renderWorldBlockExclusive)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDecoration", renderWorldBlockExclusive)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDevices", renderWorldBlockExclusive)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDevices2", renderWorldBlockExclusive)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderWoodenDecoration", renderWorldBlockExclusive)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderWoodenDevices", renderWorldBlockExclusive)
            .build();
    }

    @Override
    public Map<String, Boolean> getThreadSafeISBRHAnnotations() {
        return ImmutableMap.<String, Boolean>builder()
            .put("blusunrize.immersiveengineering.client.render.BlockRenderClothDevices", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDecoration", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDevices", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalDevices2", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderMetalMultiblocks", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderStoneDevices", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderWoodenDecoration", false)
            .put("blusunrize.immersiveengineering.client.render.BlockRenderWoodenDevices", false)
            .build();
    }

    @Override
    public List<String> extraTransformers() {
        return ImmutableList.of("com.gtnewhorizons.angelica.loading.fml.compat.transformers.specific.ImmersiveEngineeringTransformer");
    }

}
