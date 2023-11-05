package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.compat.forge.VertexLighterFlat;
import net.minecraftforge.common.util.ForgeDirection;

public class BakedQuad {

    public void pipe(VertexLighterFlat lighter) {}

    public boolean hasColor() {
        return true;
    }

    public ForgeDirection getFace() {
        return ForgeDirection.UNKNOWN;
    }

    public boolean hasShade() {
        return true;
    }
}
