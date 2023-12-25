package com.prupe.mcpatcher.mal.util;

import java.util.BitSet;

import org.lwjgl.input.Keyboard;

public class InputHandler {

    private final BitSet keysDown = new BitSet();
    private final String name;
    private final boolean enabled;

    public InputHandler(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isKeyPressed(int key) {
        if (!enabled) {
            // nothing
        } else if (Keyboard.isKeyDown(key)) {
            if (!keysDown.get(key)) {
                keysDown.set(key);
                return true;
            }
        } else {
            keysDown.clear(key);
        }
        return false;
    }

    public boolean isKeyDown(int key) {
        return enabled && Keyboard.isKeyDown(key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("InputUtils{")
            .append(name)
            .append(':');
        for (int i = keysDown.nextSetBit(0); i >= 0; i = keysDown.nextSetBit(i + 1)) {
            sb.append(' ')
                .append(Keyboard.getKeyName(i));
        }
        sb.append('}');
        return sb.toString();
    }
}
