package jss.notfine.gui;

import net.minecraft.client.gui.GuiButton;

import java.util.Collections;
import java.util.List;

public interface ISettingsEnum {
    String getButtonLabel();
    String getTitleLabel();

    // If this button has it's own page - Entries that go on this page - or Empty
    default List<Enum<?>> entries() { return Collections.emptyList(); }

    // If this button does something custom - create the button
    default GuiButton createButton(int xPosition, int yPosition, Object setting) { return null; }
}
