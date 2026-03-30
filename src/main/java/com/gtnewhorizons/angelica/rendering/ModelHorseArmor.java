package com.gtnewhorizons.angelica.rendering;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Horse model with all boxes inflated outward, used as a separate armor layer.
 * Extends ModelHorse to inherit all animation and render logic. The constructor
 * builds the default geometry, then we rebuild every box with inflate applied.
 * This mirrors how modern MC uses a separate ModelLayers.HORSE_ARMOR.
 */
public class ModelHorseArmor extends ModelHorse {

    public ModelHorseArmor(float inflate) {
        super();
        for (ModelRenderer renderer : this.boxList) {
            List<ModelBox> originalBoxes = new ArrayList<>(renderer.cubeList);
            renderer.cubeList.clear();
            for (ModelBox box : originalBoxes) {
                int width = Math.round(box.posX2 - box.posX1);
                int height = Math.round(box.posY2 - box.posY1);
                int depth = Math.round(box.posZ2 - box.posZ1);
                renderer.addBox(box.posX1, box.posY1, box.posZ1, width, height, depth, inflate);
            }
        }
    }
}
