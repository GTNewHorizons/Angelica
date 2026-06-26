package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

@Getter
public class TextureBinding implements ISettableState<TextureBinding> {
    @Setter protected int texture2D;
    @Setter protected int texture2DArray;

    @Override
    public TextureBinding set(TextureBinding state) {
        this.texture2D = state.texture2D;
        this.texture2DArray = state.texture2DArray;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof TextureBinding textureBinding)) return false;
        return texture2D == textureBinding.texture2D
            && texture2DArray == textureBinding.texture2DArray;
    }
    @Override
    public TextureBinding copy() {
        return new TextureBinding().set(this);
    }

    public final void unbindIfEquals(int id) {
        if (getTexture2D() == id) {
            this.setTexture2D(0);
        }
        if (getTexture2DArray() == id) {
            setTexture2DArray(0);
        }
    }
}
