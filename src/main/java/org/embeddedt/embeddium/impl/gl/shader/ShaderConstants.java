package org.embeddedt.embeddium.impl.gl.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShaderConstants {
    public static final ShaderConstants EMPTY = ShaderConstants.builder().build();

    private final List<String> defines;

    private ShaderConstants(List<String> defines) {
        this.defines = defines;
    }

    public List<String> getDefineStrings() {
        return this.defines;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final String EMPTY_VALUE = "";

        private final HashMap<String, String> constants = new HashMap<>();

        private Builder() {

        }

        public Builder add(String name) {
            return this.add(name, EMPTY_VALUE);
        }

        public Builder add(String name, String value) {
            String prev = this.constants.get(name);

            if (prev != null) {
                throw new IllegalArgumentException("Constant " + name + " is already defined with value " + prev);
            }

            this.constants.put(name, value);
            return this;
        }

        public ShaderConstants build() {
            List<String> defines = new ArrayList<>(this.constants.size());

            for (Map.Entry<String, String> entry : this.constants.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value.isEmpty()) {
                    defines.add("#define " + key);
                } else {
                    defines.add("#define " + key + " " + value);
                }
            }

            return new ShaderConstants(Collections.unmodifiableList(defines));
        }

        public Builder addAll(List<String> defines) {
            for (String value : defines) {
                this.add(value);
            }
            return this;
        }
    }
}
