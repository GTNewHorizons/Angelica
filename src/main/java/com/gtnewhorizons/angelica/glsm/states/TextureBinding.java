package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

@Getter
public class TextureBinding implements ISettableState<TextureBinding> {
    @Setter protected int binding;

    @Override
    public TextureBinding set(TextureBinding state) {
        this.binding = state.binding;
        return this;
    }
}
