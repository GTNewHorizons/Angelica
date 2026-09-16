package com.gtnewhorizons.angelica.debug.flyby;

record FlybyOrigin(double x, double z, float yaw) {

    static FlybyOrigin parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        final String[] parts = raw.split(",", -1);
        if (parts.length != 2 && parts.length != 3) throw new IllegalArgumentException("Expected x,z[,yaw] but got '" + raw + "'");
        try {
            return new FlybyOrigin(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), parts.length == 3 ? Float.parseFloat(parts[2].trim()) : 0.0F);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected x,z[,yaw] but got '" + raw + "'", e);
        }
    }
}
