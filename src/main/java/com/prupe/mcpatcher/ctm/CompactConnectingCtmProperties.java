package com.prupe.mcpatcher.ctm;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class CompactConnectingCtmProperties {

    private final boolean innerSeams;
    private final String orientation;
    private final Int2IntMap tileReplacementMap = new Int2IntOpenHashMap();

    public CompactConnectingCtmProperties(PropertiesFile properties) {
        this.innerSeams = properties.getBoolean("innerSeams", false);
        this.orientation = properties.getString("orientation", "none").toLowerCase();

        for (int i = 0; i < 47; i++) {
            String val = properties.getString("ctm." + i, "");
            if (!val.isEmpty()) {
                try {
                    int tileIdx = Integer.parseInt(val.trim());
                    tileReplacementMap.put(i, tileIdx);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public boolean getInnerSeams() {
        return innerSeams;
    }

    public String getOrientation() {
        return orientation;
    }

    public Int2IntMap getTileReplacementMap() {
        return tileReplacementMap;
    }
}
