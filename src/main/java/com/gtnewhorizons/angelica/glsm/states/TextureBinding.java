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

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof TextureBinding textureBinding)) return false;
        return binding == textureBinding.binding;
    }
    @Override
    public TextureBinding copy() {
        return new TextureBinding().set(this);
    }
}
