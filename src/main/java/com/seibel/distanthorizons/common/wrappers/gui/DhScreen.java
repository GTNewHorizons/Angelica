package com.seibel.distanthorizons.common.wrappers.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.List;

public class DhScreen extends GuiScreen {
    protected String title;

    public DhScreen(String text) {
        title = text;
    }

    protected GuiButton addBtn(GuiButton button) {
        this.buttonList.add(button);
        return button;
    }

    protected void DhDrawCenteredString(String text, int x, int y, int color) {
        if (StatCollector.canTranslate(text)) {
            text = StatCollector.translateToLocal(text);
        }
        drawCenteredString(fontRendererObj, text, x, y, color);
    }

    protected void DhDrawString(String text, int x, int y, int color) {
        if (StatCollector.canTranslate(text)) {
            text = StatCollector.translateToLocal(text);
        }
        drawString(fontRendererObj, text, x, y, color);
    }

    protected void DhRenderTooltip(List<String> list, int x, int y) {
        for (int i = 0; i < list.size(); i++) {
            String text = list.get(i);
            if (StatCollector.canTranslate(text)) {
                text = StatCollector.translateToLocal(text);
            }
            list.set(i, text);
        }
        drawHoveringText(list, x, y, fontRendererObj);
    }

    protected void DhRenderTooltip(String text, int x, int y) {
        if (StatCollector.canTranslate(text)) {
            text = StatCollector.translateToLocal(text);
        }
        ArrayList<String> list = new ArrayList<>();
        list.add(text);
        drawHoveringText(list, x, y, fontRendererObj);
    }
}
