package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

public record DeferredBlock(int x, int y, int z, Block block, int meta, int pass, Material materialOverride, boolean isShaderPackOverride) {}
