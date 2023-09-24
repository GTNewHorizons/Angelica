package org.embeddedt.archaicfix.helpers;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;

public class WorldRendererDistanceHelper {
    /**
     * Get the squared distance of this world renderer, adjusted to favor the XZ axes over the Y one.
     * @author embeddedt, makamys
     * @param e render view entity
     * @param instance world renderer
     * @return an adjusted squared distance of this renderer from the entity
     */
    public static double betterDistanceSquared(Entity e, WorldRenderer instance) {
        return instance.distanceToEntitySquared(e);
    }
}
