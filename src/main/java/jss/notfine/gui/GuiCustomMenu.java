package jss.notfine.gui;

import jss.notfine.NotFine;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;

public class GuiCustomMenu extends GuiScreen {
    private static final HashMap<Class<?>, ISettingHandler> buttonHandlers = new HashMap<>();
    private final GuiScreen parentGuiScreen;
    private final MenuButtonLists buttonEnum;
    protected String screenTitle;

    private GuiListExtended optionsRowList;

    static {
        addButtonHandler(Settings.class, (xPosition, yPosition, setting) -> {
            Settings customSetting = (Settings)setting;
            if(customSetting.slider)
                return (new GuiCustomSettingSlider(xPosition, yPosition, customSetting));
            else
                return (new GuiCustomSettingButton(xPosition, yPosition, customSetting));
        });
        addButtonHandler(GameSettings.Options.class, (xPosition, yPosition, setting) -> {
            GameSettings.Options vanillaSetting = (GameSettings.Options)setting;
            if (vanillaSetting.getEnumFloat())
                return (new GuiVanillaSettingSlider(xPosition, yPosition, vanillaSetting));
            else
                return (new GuiVanillaSettingButton(xPosition, yPosition, vanillaSetting));
        });
        addButtonHandler(MenuButtonLists.class, (xPosition, yPosition, setting) -> {
            MenuButtonLists menuType = (MenuButtonLists)setting;
            return new GuiCustomMenuButton(xPosition, yPosition, menuType);
        });
    }

    public GuiCustomMenu(GuiScreen parentGuiScreen, MenuButtonLists buttonEnum) {
        this.parentGuiScreen = parentGuiScreen;
        this.screenTitle = buttonEnum.getTitleLabel();
        this.buttonEnum = buttonEnum;
    }

    public static void addButtonHandler(Class<?> cls, ISettingHandler handler) {
        buttonHandlers.put(cls, handler);
    }

    public static GuiButton createButton(int xPosition, int yPosition, Enum<?> setting) {
        if(setting == null) return null;

        final ISettingHandler buttonHandler = buttonHandlers.get((setting).getDeclaringClass());
        if (buttonHandler == null) {
            NotFine.logger.debug("No handler for setting: " + setting.getClass().getName());
            return null;
        }
        return buttonHandler.createButton(xPosition, yPosition, setting);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(200, width / 2 - 100, height - 27, I18n.format("gui.done")));

        optionsRowList = new GuiCustomSettingsRowList(mc, width, height, 32, height - 32, 25, buttonEnum);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if(button.enabled && button.id == 200) {
            if(!(parentGuiScreen instanceof GuiCustomMenu)) {
                saveSettings();
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
        if(keyCode == Keyboard.KEY_ESCAPE) {
            saveSettings();
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void saveSettings() {
        mc.gameSettings.saveOptions();
        SettingsManager.settingsFile.saveSettings();
    }

}
