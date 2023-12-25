package jss.notfine.gui;

import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.util.HashSet;

public class GuiCustomMenu extends GuiScreen {
    public static GuiCustomMenu instance;
    public static HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
    private final GuiScreen parentGuiScreen;
    private final OptionPage optionPage;
    private final OptionPage[] subPages;
    protected String screenTitle;


    private GuiListExtended optionsRowList;

    public GuiCustomMenu(GuiScreen parentGuiScreen, OptionPage optionPage, OptionPage... subPages) {
        this.parentGuiScreen = parentGuiScreen;
        this.screenTitle = optionPage.getName();
        this.optionPage = optionPage;
        this.subPages = subPages;

    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(200, width / 2 - 110, height - 27, I18n.format("gui.done")));
        optionsRowList = new GuiCustomOptionsRowList(mc, width, height, 32, height - 32, 25, optionPage, subPages);
        instance = this;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if(button.enabled && button.id == 200) {
            if(!(parentGuiScreen instanceof GuiCustomMenu)) {
                saveChanges();
            }
            mc.displayGuiScreen(parentGuiScreen);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int originalScale = mc.gameSettings.guiScale;

        super.mouseClicked(mouseX, mouseY, mouseButton);
        optionsRowList.func_148179_a(mouseX, mouseY, mouseButton);

        if(mc.gameSettings.guiScale != originalScale) {
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            setWorldAndResolution(mc, scaledresolution.getScaledWidth(), scaledresolution.getScaledHeight());
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        int originalScale = mc.gameSettings.guiScale;

        super.mouseMovedOrUp(mouseX, mouseY, state);
        optionsRowList.func_148181_b(mouseX, mouseY, state);

        if(mc.gameSettings.guiScale != originalScale) {
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            setWorldAndResolution(mc, scaledresolution.getScaledWidth(), scaledresolution.getScaledHeight());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if(mc.theWorld != null && Keyboard.isKeyDown(Keyboard.KEY_F1)) {
            return;
        }
        drawDefaultBackground();
        optionsRowList.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, screenTitle, width / 2, 5, 16777215);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if(keyCode == Keyboard.KEY_ESCAPE && !(parentGuiScreen instanceof GuiCustomMenu)) {
            saveChanges();
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void saveChanges() {
        for(OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
        dirtyStorages = new HashSet<>();
    }

}
