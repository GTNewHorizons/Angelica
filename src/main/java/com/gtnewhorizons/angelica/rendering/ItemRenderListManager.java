/*
 * This file is part of FalseTweaks.
 *
 * Copyright (C) 2022-2024 FalsePattern
 * All Rights Reserved
 *
 * Modifications by Angelica in accordance with LGPL v3.0
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * FalseTweaks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FalseTweaks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FalseTweaks. If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.rendering;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glCallList;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemRenderListManager implements IResourceManagerReloadListener {
    public static final ItemRenderListManager INSTANCE = new ItemRenderListManager();

    private final Map<ItemProp, Integer> theMap = new HashMap<>();
    private final List<ItemProp> propList = new ArrayList<>();
    private final ItemProp prop = new ItemProp();
    private int list = 0;

    public boolean pre(float a, float b, float c, float d, int e, int f, float g) {
        prop.set(a, b, c, d, e, f, g);
        if (theMap.containsKey(prop)) {
            val list = theMap.get(prop);
            propList.add(propList.remove(propList.indexOf(prop)));
            glCallList(list);
            return true;
        } else {
            if (propList.size() >= AngelicaConfig.itemRendererDisplayListCacheSize) {
                val oldProp = propList.remove(0);
                GLAllocation.deleteDisplayLists(theMap.remove(oldProp));
            }
            list = GLAllocation.generateDisplayLists(1);
            val newProp = new ItemProp(prop);
            theMap.put(newProp, list);
            propList.add(newProp);
            GL11.glNewList(list, GL11.GL_COMPILE);
            return false;
        }
    }

    public void post() {
        GL11.glEndList();
        GL11.glCallList(list);
    }

    @Override
    public void onResourceManagerReload(IResourceManager p_110549_1_) {
        propList.clear();
        theMap.forEach((key, value) -> GLAllocation.deleteDisplayLists(value));
        theMap.clear();
    }

    @NoArgsConstructor
    @Data
    public class ItemProp {
        private float a;
        private float b;
        private float c;
        private float d;
        private int e;
        private int f;
        private float g;

        public ItemProp(ItemProp old) {
            set(old.a, old.b, old.c, old.d, old.e, old.f, old.g);
        }

        public void set(float a, float b, float c, float d, int e, int f, float g) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
        }
    }
}
