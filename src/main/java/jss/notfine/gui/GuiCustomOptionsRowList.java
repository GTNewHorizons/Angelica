package jss.notfine.gui;

import com.google.common.collect.Lists;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jss.notfine.gui.options.control.element.NotFineControlElementFactory;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.Tessellator;

import java.util.Iterator;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiCustomOptionsRowList extends GuiListExtended {
    private static final NotFineControlElementFactory factory = new NotFineControlElementFactory();
    private final List<Row> settingsList = Lists.newArrayList();

    public GuiCustomOptionsRowList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, OptionPage optionPage, OptionPage... subPages)  {
        super(mc, width, height, top, bottom, slotHeight);
        field_148163_i = false;

        for(OptionGroup optionGroup : optionPage.getGroups()) {
            Iterator settings = optionGroup.getOptions().stream().iterator();
            while(settings.hasNext()) {
                Option optionOne = (Option)settings.next();
                Option optionTwo = settings.hasNext() ? (Option)settings.next() : null;
                GuiButton buttonOne = (GuiButton)optionOne.getControl().createElement(new Dim2i(width / 2 - 155,0,150,20), factory);
                GuiButton buttonTwo = optionTwo == null ? null : (GuiButton)optionTwo.getControl().createElement(new Dim2i(width / 2 - 155 + 160,0,150,20), factory);
                settingsList.add(new GuiCustomOptionsRowList.Row(buttonOne, buttonTwo));
            }
        }
        for(int i = 0; i < subPages.length; i += 2) {
            OptionPage pageOne = subPages[i];
            OptionPage pageTwo = i < subPages.length - 1 ? subPages[i + 1] : null;
            GuiButton buttonOne = new GuiCustomMenuButton(width / 2 - 155, 0, pageOne);
            GuiButton buttonTwo = pageTwo == null ? null : new GuiCustomMenuButton(width / 2 - 155  + 160, 0, pageTwo);
            settingsList.add(new Row(buttonOne, buttonTwo));
        }
    }

    @Override
    public GuiCustomOptionsRowList.Row getListEntry(int index) {
        return settingsList.get(index);
    }

    @Override
    protected int getSize() {
        return settingsList.size();
    }

    @Override
    public int getListWidth() {
        return 400;
    }

    @Override
    protected int getScrollBarX() {
        return super.getScrollBarX() + 32;
    }

    @SideOnly(Side.CLIENT)
    public static class Row implements GuiListExtended.IGuiListEntry {
        private final Minecraft mc = Minecraft.getMinecraft();
        private final GuiButton buttonOne, buttonTwo;

        public Row(GuiButton one, GuiButton two) {
            buttonOne = one;
            buttonTwo = two;

            if(one != null && two == null) {
                one.width += 160;
            }
        }

        @Override
        public void drawEntry(int varU1, int x, int y, int varU2, int varU3, Tessellator tessellator, int mouseX, int mouseY, boolean varU4)  {
            buttonOne.yPosition = y;
            buttonOne.drawButton(mc, mouseX, mouseY);


            if(buttonTwo != null) {
                buttonTwo.yPosition = y;
                buttonTwo.drawButton(mc, mouseX, mouseY);
            }
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if(buttonOne.mousePressed(mc, x, y)) {
                return true;
            }
            if(buttonTwo != null && buttonTwo.mousePressed(mc, x, y)) {
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if(buttonOne != null) {
                buttonOne.mouseReleased(x, y);
            }

            if(buttonTwo != null) {
                buttonTwo.mouseReleased(x, y);
            }
        }
    }

}
