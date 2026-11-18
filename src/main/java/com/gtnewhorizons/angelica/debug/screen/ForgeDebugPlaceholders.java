package com.gtnewhorizons.angelica.debug.screen;

import cpw.mods.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;

public final class ForgeDebugPlaceholders {

    static final String VERSION = new String("Minecraft 1.7.10 (0 fps)");
    static final String RENDERS = new String("C: 0/0. F: 0, O: 0, E: 0");
    static final String ENTITIES = new String("E: 0/0. B: 0, I: 0");
    static final String PARTICLES = new String("P: 0. T: 0");
    static final String WORLD = new String("Overworld");
    static final String USED_MEMORY = new String("Used memory: 0% (0MB) of 0MB");
    static final String ALLOCATED_MEMORY = new String("Allocated memory: 0% (0MB)");
    static final String X = new String("x: 0.00000 (0) // c: 0 (0)");
    static final String Y = new String("y: 0.000 (feet pos, 0.000 eyes pos)");
    static final String Z = new String("z: 0.00000 (0) // c: 0 (0)");
    static final String FACING = new String("f: 0 (South) / 0.000000");
    static final String CHUNK = new String("lc: 0 b: Plains bl: 0 sl: 0 rl: 0");
    static final String SPEED = new String("ws: 0.000, fs: 0.000, g: false, fl: 0");
    static final String SHADER = new String("shader: none");

    private static final int LEFT_LINES = 13;
    private static final int RIGHT_LINES = 3;

    private static final int[] nullsAdded = new int[2];
    private static List<String> lastBrandings;
    private static List<String> forgeBrandings;

    private ForgeDebugPlaceholders() {}

    public static boolean isPlaceholder(String line) {
        if (line == VERSION || line == RENDERS || line == ENTITIES || line == PARTICLES || line == WORLD
            || line == USED_MEMORY || line == ALLOCATED_MEMORY || line == X || line == Y || line == Z
            || line == FACING || line == CHUNK || line == SPEED || line == SHADER) {
            return true;
        }
        final List<String> brandings = lastBrandings;
        if (line == null || brandings == null) return false;
        for (int i = 0, n = brandings.size(); i < n; i++) {
            if (brandings.get(i) == line) return true;
        }
        return false;
    }

    public static int nullsAdded(int side) {
        return nullsAdded[side];
    }

    public static List<String> brandings() {
        List<String> brandings = forgeBrandings;
        if (brandings == null) {
            brandings = FMLCommonHandler.instance().getBrandings(false);
            forgeBrandings = brandings;
        }
        return brandings;
    }

    public static void add(ArrayList<String> left, ArrayList<String> right, boolean chunkLoaded, boolean shaderActive) {
        add(left, right, chunkLoaded, shaderActive, brandings());
    }

    static void add(ArrayList<String> left, ArrayList<String> right, boolean chunkLoaded, boolean shaderActive, List<String> brandings) {
        lastBrandings = brandings;
        nullsAdded[0] = chunkLoaded ? 1 : 2;
        nullsAdded[1] = 1;
        final int brandingCount = brandings.size();
        left.ensureCapacity(left.size() + LEFT_LINES);
        right.ensureCapacity(right.size() + RIGHT_LINES + brandingCount);
        left.add(VERSION);
        left.add(RENDERS);
        left.add(ENTITIES);
        left.add(PARTICLES);
        left.add(WORLD);
        left.add(null);
        right.add(USED_MEMORY);
        right.add(ALLOCATED_MEMORY);
        left.add(X);
        left.add(Y);
        left.add(Z);
        left.add(FACING);
        left.add(chunkLoaded ? CHUNK : null);
        left.add(SPEED);
        if (shaderActive) left.add(SHADER);
        right.add(null);
        for (int i = 0; i < brandingCount; i++) right.add(brandings.get(i));
    }
}
