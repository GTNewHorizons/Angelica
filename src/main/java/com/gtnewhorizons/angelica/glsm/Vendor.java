package com.gtnewhorizons.angelica.glsm;

/// Matches
public enum Vendor {
    AMD("amd"),
    INTEL("intel"),
    MESA("mesa"),
    NVIDIA("nvidia"),
    UNKNOWN("");

    final String[] names;

    Vendor(String... names) {
        this.names = names;
    }

    public static Vendor getVendor(String vendorString) {
        for (var v : values()) {
            for (var name : v.names) {
                if (vendorString.contains(name)) return v;
            }
        }

        // Shouldn't be needed as "" matches everything, but sanity checks good.
        return UNKNOWN;
    }
}
