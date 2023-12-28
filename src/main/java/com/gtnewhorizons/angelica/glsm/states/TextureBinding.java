package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.stacks.IStackableState;
import lombok.Getter;
import lombok.Setter;

@Getter
public class TextureBinding implements IStackableState<TextureBinding> {
    @Setter protected int binding;

    @Override
    public TextureBinding set(TextureBinding state) {
        this.binding = state.binding;
        return this;
    }
}
