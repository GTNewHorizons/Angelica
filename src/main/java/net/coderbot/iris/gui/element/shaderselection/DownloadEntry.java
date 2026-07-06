package net.coderbot.iris.gui.element.shaderselection;

import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

/**
 * Show a download button when no shaderpacks exist in the shaderpacks folder.
 */
public class DownloadEntry extends BaseEntry {
    private final String label;
    private final String url;

    public DownloadEntry(ShaderPackSelectionList list, String label, String url) {
        super(list);
        this.label = label;
        this.url = url;
    }

    public boolean click(ShaderPackScreen screen) {
        GuiUtil.playButtonClickSound();
        screen.openLinkConfirm(url);
        return true;
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int listWidth, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        GuiUtil.bindIrisWidgetsTexture();
        GuiUtil.drawButton(x - 2, y - 5, listWidth, 18, isMouseOver, false);
        screen.drawCenteredString(label, (x + listWidth / 2) - 2, y, 0xFFFFFF);
    }
}
