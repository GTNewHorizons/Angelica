package com.gtnewhorizons.angelica.common;

import net.minecraft.tileentity.TileEntity;

public abstract class TileTesrTranslucentTest extends TileEntity {

    private static final int SOLID_PASS = 8;

    public int variant() {
        return getBlockMetadata() & ~SOLID_PASS;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == ((getBlockMetadata() & SOLID_PASS) != 0 ? 0 : 1);
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    public static class Provider extends TileTesrTranslucentTest {}

    public static class Model extends TileTesrTranslucentTest {}

    public static class Decal extends TileTesrTranslucentTest {}

    public static class Glow extends TileTesrTranslucentTest {}
}
