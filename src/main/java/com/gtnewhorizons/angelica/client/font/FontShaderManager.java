package com.gtnewhorizons.angelica.client.font;

import java.util.ArrayList;
import java.util.List;

public final class FontShaderManager {

    public static final List<FontOverlayShader> fontShaders = new ArrayList<>();

    public static void registerShader(FontOverlayShader shader) {
        fontShaders.add(shader);
    }

    static boolean isOverlayShader(char ch) {
        return ((int) ch) < fontShaders.size();
    }
}
