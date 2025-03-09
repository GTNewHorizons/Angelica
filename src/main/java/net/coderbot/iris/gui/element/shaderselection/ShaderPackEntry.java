package net.coderbot.iris.gui.element.shaderselection;

import lombok.Getter;
import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;

public class ShaderPackEntry extends BaseEntry {
    @Getter private final String packName;
    private final ShaderPackSelectionList shaderPackSelectionList;

    public ShaderPackEntry(ShaderPackSelectionList list, String packName) {
        super(list);
        this.packName = packName;
        this.shaderPackSelectionList = list;
    }

    public boolean isApplied() {
        return shaderPackSelectionList.getApplied() == this;
    }

    public boolean isSelected() {
        return shaderPackSelectionList.getSelected() == this;
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int listWidth, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        final FontRenderer font = screen.getFontRenderer();
        final boolean shadersEnabled = shaderPackSelectionList.getTopButtonRow().shadersEnabled;

        int color = 0xFFFFFF;
        String name = packName;
        if (font.getStringWidth(name) > this.list.getListWidth() - 3) {
            name = font.trimStringToWidth(name, this.list.getListWidth() - 8) + "...";
        }

        if(isMouseOver) {
            name = EnumChatFormatting.BOLD + name;
        }

        if(this.isApplied()) {
            color = 0xFFF263;
        }

        if(!shadersEnabled && !isMouseOver) {
            color = 0xA2A2A2;
        }



        screen.drawCenteredString(name, (x + listWidth / 2) - 2, y, color);
    }
}
