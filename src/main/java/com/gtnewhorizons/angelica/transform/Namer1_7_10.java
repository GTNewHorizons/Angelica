package com.gtnewhorizons.angelica.transform;

import static com.gtnewhorizons.angelica.transform.Names.entityLivingBase_;
import static com.gtnewhorizons.angelica.transform.Names.entityRenderer_;
import static com.gtnewhorizons.angelica.transform.Names.entityRenderer_renderHand;
import static com.gtnewhorizons.angelica.transform.Names.rendererLivingE_;
import static com.gtnewhorizons.angelica.transform.Names.rendererLivingE_doRender;
import static com.gtnewhorizons.angelica.transform.Names.rendererLivingE_renderEquippedItems;

public class Namer1_7_10 extends Namer {

    public void setNames() {
        setNames1_7_10();
    }

    public void setNames1_7_10() {
        entityRenderer_ = c("blt");
        rendererLivingE_ = c("boh");
        entityLivingBase_ = c("sv");

        entityRenderer_renderHand = m(entityRenderer_, "b", "(FI)V");
        rendererLivingE_doRender = m(rendererLivingE_, "a", "(" + entityLivingBase_.desc + "DDDFF)V");
        rendererLivingE_renderEquippedItems = m(rendererLivingE_, "c", "(" + entityLivingBase_.desc + "F)V");
    }
}
