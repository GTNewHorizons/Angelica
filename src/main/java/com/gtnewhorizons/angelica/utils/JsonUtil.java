package com.gtnewhorizons.angelica.utils;

import com.google.gson.JsonObject;

public class JsonUtil {

    public static boolean loadBool(JsonObject in, String name, boolean defaul) {
        if (in.has(name))
            return in.getAsJsonPrimitive(name).getAsBoolean();

        return defaul;
    }

    public static float loadFloat(JsonObject in, String name) {
        if (in.has(name))
            return in.getAsJsonPrimitive(name).getAsFloat();

        throw new RuntimeException("Required field " + name + " not found in JsonObject " + in);
    }

    public static int loadInt(JsonObject in, String name, int defaul) {
        if (in.has(name))
            return in.getAsJsonPrimitive(name).getAsInt();

        return defaul;
    }

    public static String loadStr(JsonObject in, String name) {
        if (in.has(name))
            return in.getAsJsonPrimitive(name).getAsString();

        throw new RuntimeException("Required field " + name + " not found in JsonObject " + in);
    }

    public static String loadStr(JsonObject in, String name, String defaul) {
        if (in.has(name))
            return in.getAsJsonPrimitive(name).getAsString();

        return defaul;
    }
}
