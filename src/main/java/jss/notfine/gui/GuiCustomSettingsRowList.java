package jss.notfine.gui;

import com.google.common.collect.Lists;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.Tessellator;

import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiCustomSettingsRowList extends GuiListExtended {
    private final List<Row> settingsList = Lists.newArrayList();

    public GuiCustomSettingsRowList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, MenuButtonLists buttonEnum)  {
        super(mc, width, height, top, bottom, slotHeight);
        field_148163_i = false;

        Enum<?>[] settings = MenuButtonLists.getEntries(buttonEnum);

        for(int i = 0; i < settings.length; i += 2) {
            Enum<?> settingOne = settings[i];
            Enum<?> settingTwo = i < settings.length - 1 ? settings[i + 1] : null;
            GuiButton buttonOne = GuiCustomMenu.createButton(width / 2 - 155, 0, settingOne);
            GuiButton buttonTwo = GuiCustomMenu.createButton(width / 2 - 155 + 160, 0, settingTwo);
            settingsList.add(new Row(buttonOne, buttonTwo));
        }
    }

    @Override
    public Row getListEntry(int index) {
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
    public static class Row implements IGuiListEntry {
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
            if(buttonOne != null) {
                buttonOne.yPosition = y;
                buttonOne.drawButton(mc, mouseX, mouseY);
            }

            if(buttonTwo != null) {
                buttonTwo.yPosition = y;
                buttonTwo.drawButton(mc, mouseX, mouseY);
            }
        }

        @Override
        public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            if(buttonOne != null && buttonOne.mousePressed(mc, x, y)) {
                return true;
            } else if(buttonTwo != null && buttonTwo.mousePressed(mc, x, y)) {
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
