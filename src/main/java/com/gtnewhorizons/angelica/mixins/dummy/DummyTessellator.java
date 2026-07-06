package com.gtnewhorizons.angelica.mixins.dummy;

import net.minecraft.client.renderer.Tessellator;

/**
 * No-op tessellator to replace the inconvienent one
 */
public class DummyTessellator extends Tessellator {

    public static Tessellator instance = new DummyTessellator();

    @Override
    public void startDrawingQuads() {}

    @Override
    public void  setColorOpaque_I(int whocares) {}

    @Override
    public void addVertex(double thing1, double thing2, double thereaintnothing3) {}

    @Override
    public int draw() {
        return 0;
    }
}
