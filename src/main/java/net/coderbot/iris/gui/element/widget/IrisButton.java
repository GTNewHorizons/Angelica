package net.coderbot.iris.gui.element.widget;

import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;
import java.util.function.Function;

public class IrisButton extends GuiButton {
    protected final Consumer<IrisButton> onPress;

    public IrisButton(int x, int y, int width, int height, String displayString, Consumer<IrisButton> onPress) {
        super(999, x, y, width, height, displayString);
        this.onPress = onPress;

    }

    public void onPress() {
        this.onPress.accept(this);
    }


}
