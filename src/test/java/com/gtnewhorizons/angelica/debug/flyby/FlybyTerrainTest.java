package com.gtnewhorizons.angelica.debug.flyby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlybyTerrainTest {

    private static final double[] LINE_X = { 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5 };
    private static final double[] LINE_Z = { 0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.5 };

    @Test
    void pillarAtCorridorEdgeRaisesFloor() {
        assertEquals(9, FlybyTerrain.routeFloor(LINE_X, LINE_Z, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == 2 && z == 5 ? 9 : 4));
        assertEquals(9, FlybyTerrain.routeFloor(LINE_X, LINE_Z, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == -2 && z == 12 ? 9 : 4));
        assertEquals(4, FlybyTerrain.routeFloor(LINE_X, LINE_Z, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == 3 && z == 5 ? 9 : 4));
        assertEquals(4, FlybyTerrain.routeFloor(LINE_X, LINE_Z, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == 0 && z == 13 ? 9 : 4));
    }

    @Test
    void negativeCoordinatesFloorToLowerCell() {
        final double[] pathX = { -0.5 };
        final double[] pathZ = { -0.5 };
        assertEquals(9, FlybyTerrain.routeFloor(pathX, pathZ, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == -3 && z == -3 ? 9 : 4));
        assertEquals(9, FlybyTerrain.routeFloor(pathX, pathZ, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == 1 && z == 1 ? 9 : 4));
        assertEquals(4, FlybyTerrain.routeFloor(pathX, pathZ, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> x == 2 ? 9 : 4));
        assertEquals(4, FlybyTerrain.routeFloor(pathX, pathZ, FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> z == -4 ? 9 : 4));
    }

    @Test
    void originParseWithYaw() {
        assertEquals(new FlybyOrigin(1509.5, -924.5, 0.0F), FlybyOrigin.parse("1509.5,-924.5,0"));
        assertEquals(new FlybyOrigin(10.0, 20.0, 90.0F), FlybyOrigin.parse("10, 20, 90"));
        assertEquals(new FlybyOrigin(1509.5, -924.5, 0.0F), FlybyOrigin.parse("1509.5,-924.5"));
    }

    @Test
    void originParseRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> FlybyOrigin.parse("1,2,3,4"));
    }
}
