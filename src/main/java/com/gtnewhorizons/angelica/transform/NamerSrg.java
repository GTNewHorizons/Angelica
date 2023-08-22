package com.gtnewhorizons.angelica.transform;

import static com.gtnewhorizons.angelica.transform.Names.*;

public class NamerSrg extends Namer {

    public void setNames() {
        setNamesSrg();
    }

    public void setNamesSrg() {
        entityRenderer_ = c("net/minecraft/client/renderer/EntityRenderer");
        rendererLivingE_ = c("net/minecraft/client/renderer/entity/RendererLivingEntity");
        entityLivingBase_ = c("net/minecraft/entity/EntityLivingBase");

        entityRenderer_renderHand = m(entityRenderer_, "func_78476_b", "(FI)V");
        rendererLivingE_doRender = m(rendererLivingE_, "func_76986_a", "(" + entityLivingBase_.desc + "DDDFF)V");
        rendererLivingE_renderEquippedItems = m(rendererLivingE_, "func_77029_c", "(" + entityLivingBase_.desc + "F)V");
    }
}
