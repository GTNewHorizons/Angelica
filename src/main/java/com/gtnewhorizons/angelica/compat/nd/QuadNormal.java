package com.gtnewhorizons.angelica.compat.nd;

import org.joml.Vector3f;

public enum QuadNormal {
    // Temporarily borrowed from Neodymium
    NONE, POSITIVE_X, NEGATIVE_X, POSITIVE_Y, NEGATIVE_Y, POSITIVE_Z, NEGATIVE_Z;

    public static QuadNormal fromVector(Vector3f normal) {
        if(normal.x == 0f) {
            if(normal.y == 0f) {
                if(normal.z > 0) {
                    return POSITIVE_Z;
                } else if(normal.z < 0) {
                    return NEGATIVE_Z;
                }
            } else if(normal.z == 0f) {
                if(normal.y > 0) {
                    return POSITIVE_Y;
                } else if(normal.y < 0) {
                    return NEGATIVE_Y;
                }
            }
        } else if(normal.y == 0f) {
            if(normal.z == 0f) {
                if(normal.x > 0) {
                    return POSITIVE_X;
                } else if(normal.x < 0) {
                    return NEGATIVE_X;
                }
            }
        }
        return NONE;
    }
}
