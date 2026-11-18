package me.jellysquid.mods.sodium.client.gui.options;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** "Option" that opens a sub screen */
public class SubScreenOption extends ButtonOption {

    private static final String ARROW = ">";

    private final Function<GuiScreen, GuiScreen> destination;

    public SubScreenOption(String name, String tooltip, Function<GuiScreen, GuiScreen> destination) {
        super(name, tooltip, 30);
        this.destination = destination;
    }

    @Override
    protected String label() {
        return ARROW;
    }

    @Override
    protected void invoke() {
        final var mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(this.destination.apply(mc.currentScreen));
    }
}
