package jss.notfine.asm.mappings;

public class NamerSrg extends Namer {

    public void setNames() {
        setNamesSrg();
    }

    public void setNamesSrg() {
        Names.renderBlocks_ = c("net/minecraft/client/renderer/RenderBlocks");
        Names.block_ = c("net/minecraft/block/Block");
        Names.iBlockAccess_ = c("net/minecraft/world/IBlockAccess");
        Names.worldRenderer_ = c("net/minecraft/client/renderer/WorldRenderer");
        Names.entityLivingBase_ = c("net/minecraft/entity/EntityLivingBase");

        Names.renderBlocks_colorBlueTopRight = f(Names.renderBlocks_, "field_147833_aA", "F");
        Names.renderBlocks_blockAccess = f(Names.renderBlocks_, "field_147845_a", Names.iBlockAccess_.desc);

        Names.renderBlocks_renderStandardBlockWithAmbientOcclusion = m(
            Names.renderBlocks_,
            "func_147751_a",
            "(" + Names.block_.desc + "IIIFFF)Z");

        Names.renderBlocks_renderStandardBlockWithAmbientOcclusionPartial = m(
            Names.renderBlocks_,
            "func_147808_b",
            "(" + Names.block_.desc + "IIIFFF)Z");

        Names.worldRenderer_updateRenderer = m(
            Names.worldRenderer_,
            "func_147892_a",
            "(" + Names.entityLivingBase_.desc + ")V");

        Names.block_getRenderBlockPass = m(Names.block_, "func_149701_w", "()I");
    }
}
