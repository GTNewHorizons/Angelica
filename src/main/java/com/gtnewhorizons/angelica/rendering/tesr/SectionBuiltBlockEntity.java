package com.gtnewhorizons.angelica.rendering.tesr;

import net.minecraft.world.IBlockAccess;

public interface SectionBuiltBlockEntity {

    int angelica$prepareFromSection(IBlockAccess world);

    void angelica$applySectionResult(int prepared);

    boolean angelica$drawBuilt(double x, double y, double z);
}
