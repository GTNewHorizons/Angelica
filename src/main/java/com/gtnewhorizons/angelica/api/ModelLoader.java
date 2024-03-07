package com.gtnewhorizons.angelica.api;

import net.minecraft.block.Block;

/**
 * Model loading is as follows: <ul>
 * <li>Models to load are registered in PREINIT or INIT. See {@link Loader#reg}.</li>
 * <li>A resource rebuild is triggered, then registered models and their parents are recursively loaded in POSTINIT.
 * </li>
 * <li>Models registered for baking are baked on the first client tick.</li>
 *</ul>
 * <p>As for icons, register them in {@link Block#registerBlockIcons}. Whatever gets them in the block texture atlas.
 */
public interface ModelLoader {
}
