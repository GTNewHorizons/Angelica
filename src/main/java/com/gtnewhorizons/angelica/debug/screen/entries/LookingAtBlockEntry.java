package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

public final class LookingAtBlockEntry implements DebugScreenEntry {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        final MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        final int x = hit.blockX;
        final int y = hit.blockY;
        final int z = hit.blockZ;
        final Block block = context.world().getBlock(x, y, z);
        displayer.fact(DebugGroup.LOOKING_AT_BLOCK, "Coordinates").value(x).text(", ").value(y).text(", ").value(z);
        displayer.fact(DebugGroup.LOOKING_AT_BLOCK, "Type").value(Block.blockRegistry.getNameForObject(block));
        displayer.fact(DebugGroup.LOOKING_AT_BLOCK, "Meta").value(context.world().getBlockMetadata(x, y, z));
    }
}
