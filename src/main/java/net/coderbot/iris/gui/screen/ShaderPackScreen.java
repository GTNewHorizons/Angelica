package net.coderbot.iris.gui.screen;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.ShaderPackOptionList;
import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.element.shaderselection.ShaderPackEntry;
import net.coderbot.iris.gui.element.widget.AbstractElementWidget;
import net.coderbot.iris.gui.element.widget.CommentedElementWidget;
import net.coderbot.iris.gui.element.widget.IrisButton;
import net.coderbot.iris.gui.element.widget.IrisImageButton;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ShaderPackScreen extends GuiScreen implements HudHideable {
    /**
     * Queue rendering to happen on top of all elements. Useful for tooltips or dialogs.
     */
    public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

    private static final String SELECT_TITLE = EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC + I18n.format("pack.iris.select.title");
    private static final String CONFIGURE_TITLE = EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC + I18n.format("pack.iris.configure.title");
    private static final int COMMENT_PANEL_WIDTH = 314;

    private final GuiScreen parent;
    private final String title;

    private final String irisTextComponent;

    private ShaderPackSelectionList shaderPackList;

    private @Nullable ShaderPackOptionList shaderOptionList = null;
    private @Nullable NavigationController navigation = null;
    private GuiButton screenSwitchButton;

    private String notificationDialog = null;
    private int notificationDialogTimer = 0;

    private @Nullable AbstractElementWidget<?> hoveredElement = null;
    private Optional<String> hoveredElementCommentTitle = Optional.empty();
    private List<String> hoveredElementCommentBody = new ArrayList<>();
    private int hoveredElementCommentTimer = 0;

    private boolean optionMenuOpen = false;

    private boolean dropChanges = false;
    private String developmentComponent;
    private String updateComponent;

    private boolean guiHidden = false;
    private boolean dirty = false;

    public ShaderPackScreen(GuiScreen parent) {
        this.title = I18n.format("options.iris.shaderPackSelection.title");

        this.parent = parent;

        // Prime the shader transform executor so reloads from this screen don't pay thread spin-up costs.
        Iris.ShaderTransformExecutor.prepare();

        String irisName = Iris.MODNAME; // + " " + Iris.getVersion(); // TEMP

        if (Iris.INSTANCE.isDevelopmentEnvironment) {
            this.developmentComponent = "Development Environment";
            irisName = irisName.replace("-development-environment", "");
        }

        this.irisTextComponent = irisName;

        refreshForChangedPack();
    }
    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);

        if(dirty) {
            dirty = false;
            this.initGui();
        }

        handleDroppedFiles();

        if (this.mc.theWorld == null) {
            super.drawDefaultBackground();
        } else if (!this.guiHidden) {
            this.drawGradientRect(0, 0, width, height, 0x4F232323, 0x4F232323);
        }

        if (!this.guiHidden) {
            if (optionMenuOpen && this.shaderOptionList != null) {
                this.shaderOptionList.drawScreen(mouseX, mouseY, delta);
            } else {
                this.shaderPackList.drawScreen(mouseX, mouseY, delta);
            }
        }

        if (hoveredElement != null) {
            hoveredElementCommentTimer++;
        } else {
            hoveredElementCommentTimer = 0;
        }

        super.drawScreen(mouseX, mouseY, delta);

        if (!this.guiHidden) {
            drawCenteredString(this.fontRendererObj, this.title, (int) (this.width * 0.5), 8, 0xFFFFFF);

            if (notificationDialog != null && notificationDialogTimer > 0) {
                drawCenteredString(this.fontRendererObj, notificationDialog, (int) (this.width * 0.5), 21, 0xFFFFFF);
            } else {
                if (optionMenuOpen) {
                    drawCenteredString(this.fontRendererObj, CONFIGURE_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFF);
                } else {
                    drawCenteredString(this.fontRendererObj, SELECT_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFF);
                }
            }

            // Draw the comment panel
            if (this.isDisplayingComment()) {
                final int panelHeight = Math.max(50, 18 + (this.hoveredElementCommentBody.size() * 10));
                final int x = (int) (0.5 * this.width) - 157;
                final int y = this.height - (panelHeight + 4);
                // Draw panel
                GuiUtil.drawPanel(x, y, COMMENT_PANEL_WIDTH, panelHeight);
                // Draw text
                this.fontRendererObj.drawStringWithShadow(this.hoveredElementCommentTitle.orElse(""), x + 4, y + 4, 0xFFFFFF);
                for (int i = 0; i < this.hoveredElementCommentBody.size(); i++) {
                    this.fontRendererObj.drawStringWithShadow(this.hoveredElementCommentBody.get(i), x + 4, (y + 16) + (i * 10), 0xFFFFFF);
                }
            }
        }

        // Render everything queued to drawScreen last
        for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
            render.run();
        }
        TOP_LAYER_RENDER_QUEUE.clear();

        if (this.developmentComponent != null) {
            this.fontRendererObj.drawStringWithShadow(developmentComponent, 2, this.height - 10, 0xFFFFFF);
            this.fontRendererObj.drawStringWithShadow(irisTextComponent, 2, this.height - 20, 0xFFFFFF);
        } else if (this.updateComponent != null) {
            this.fontRendererObj.drawStringWithShadow(updateComponent, 2, this.height - 10, 0xFFFFFF);
            this.fontRendererObj.drawStringWithShadow(irisTextComponent, 2, this.height - 20, 0xFFFFFF);
        } else {
            this.fontRendererObj.drawStringWithShadow(irisTextComponent, 2, this.height - 10, 0xFFFFFF);
        }

        GLStateManager.glPopAttrib();
    }

    @Override
    public void initGui() {
        super.initGui();

        BackendManager.RENDER_BACKEND.startFileDrop();

        final int bottomCenter = this.width / 2 - 50;
        final int topCenter = this.width / 2 - 76;
        final boolean inWorld = this.mc.theWorld != null;

        if (this.shaderPackList != null) {
            try {
                this.shaderPackList.close();
            } catch (IOException e) {
                Iris.logger.error("Failed to close previous shaderpack selection watcher!", e);
            }
        }

        this.shaderPackList = new ShaderPackSelectionList(this, this.mc, this.width, this.height, 32, this.height - 58, 0, this.width);

        if (Iris.getCurrentPack().isPresent() && this.navigation != null) {
            final ShaderPack currentPack = Iris.getCurrentPack().get();

            this.shaderOptionList = new ShaderPackOptionList(this, this.navigation, currentPack, this.mc, this.width, this.height, 32, this.height - 58, 0, this.width);
            this.navigation.setActiveOptionList(this.shaderOptionList);

            this.shaderOptionList.rebuild();
        } else {
            optionMenuOpen = false;
            this.shaderOptionList = null;
        }

        if (inWorld) {
            this.shaderPackList.setRenderBackground(false);
            if (shaderOptionList != null) {
                this.shaderOptionList.setRenderBackground(false);
            }
        }

        this.buttonList.clear();

        if (!this.guiHidden) {

            this.buttonList.add(new IrisButton(bottomCenter + 104, this.height - 27, 100, 20,
                I18n.format("gui.done"), button -> this.onClose()));

            this.buttonList.add(new IrisButton(bottomCenter, this.height - 27, 100, 20,
                I18n.format("options.iris.apply"), button -> this.applyChanges()));

            this.buttonList.add(new IrisButton(bottomCenter - 104, this.height - 27, 100, 20,
                I18n.format("gui.cancel"), button -> this.dropChangesAndClose()));

            this.buttonList.add(new IrisButton(topCenter - 78, this.height - 51, 152, 20,
                I18n.format("options.iris.openShaderPackFolder"), button -> this.openShaderPackFolder()));

            this.screenSwitchButton = new IrisButton(topCenter + 78, this.height - 51, 152, 20,
                I18n.format("options.iris.shaderPackList"), button -> {
                    this.optionMenuOpen = !this.optionMenuOpen;

                    // UX: Apply changes before switching screens to avoid unintuitive behavior
                    //
                    // Not doing this leads to unintuitive behavior, since selecting a pack in the
                    // list (but not applying) would open the settings for the previous pack, rather
                    // than opening the settings for the selected (but not applied) pack.
                    this.applyChanges();

                    initGui();
                }
            );
            this.buttonList.add(this.screenSwitchButton);

            refreshScreenSwitchButton();
        }

        if (inWorld) {
            final float endOfLastButton = this.width / 2.0f + 154.0f;
            final float freeSpace = this.width - endOfLastButton;
            final int x;
            if (freeSpace > 100.0f) {
                x = this.width - 50;
            } else if (freeSpace < 20.0f) {
                x = this.width - 20;
            } else {
                x = (int) (endOfLastButton + (freeSpace / 2.0f)) - 10;
            }

            this.buttonList.add(new IrisImageButton(
                x, this.height - 39,
                20, 20,
                this.guiHidden ? 20 : 0, 146, 20,
                GuiUtil.IRIS_WIDGETS_TEX,
                button -> {
                    this.guiHidden = !this.guiHidden;
                    this.dirty = true;
                }
            ));
        }

        // NB: Don't let comment remain when exiting options screen
        // https://github.com/IrisShaders/Iris/issues/1494
        this.hoveredElement = null;
        this.hoveredElementCommentTimer = 0;
    }


    /**
     * Called when the mouse is clicked.
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        boolean handled = false;
        if (!this.guiHidden) {
            if (optionMenuOpen && this.shaderOptionList != null ) {
                handled = this.shaderOptionList.mouseClicked(mouseX, mouseY, mouseButton);
            } else {
                handled = this.shaderPackList.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
        if(!handled) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    /**
     * Called when the mouse is moved or a mouse button is released.  Signature: (mouseX, mouseY, which) which==-1 is
     * mouseMove, which==0 or which==1 is mouseUp
     */
    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        boolean handled = false;
        if (!this.guiHidden && state != -1) {
            if (optionMenuOpen && this.shaderOptionList != null) {
                handled = this.shaderOptionList.mouseReleased(mouseX, mouseY, Mouse.getEventButton());
            } else {
                handled = this.shaderPackList.mouseReleased(mouseX, mouseY, Mouse.getEventButton());
            }
        }
        if(!handled) {
            super.mouseMovedOrUp(mouseX, mouseY, state);
        }
    }


    @Override
    protected void actionPerformed(GuiButton guiButton) {
        if(guiButton.enabled) {
            if(guiButton instanceof IrisButton irisButton) {
                irisButton.onPress();
            }
        }
    }

    public void refreshForChangedPack() {
        if (Iris.getCurrentPack().isPresent()) {
            final ShaderPack currentPack = Iris.getCurrentPack().get();

            this.navigation = new NavigationController();

            if (this.shaderOptionList != null) {
                this.shaderOptionList.applyShaderPack(currentPack);
                this.shaderOptionList.rebuild();
            }
        } else {
            this.navigation = null;
        }

        refreshScreenSwitchButton();
    }

    public void refreshScreenSwitchButton() {
        if (this.screenSwitchButton != null) {
            this.screenSwitchButton.displayString = optionMenuOpen ? I18n.format("options.iris.shaderPackList") : I18n.format("options.iris.shaderPackSettings");
            // Disable the settings switch when shaders are off or the selected pack exposes no options
            this.screenSwitchButton.enabled = optionMenuOpen || (shaderPackList.getTopButtonRow().shadersEnabled
                && Iris.getCurrentPack().map(p -> !p.getMenuContainer().mainScreen.elements.isEmpty()).orElse(true));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_D) {
            this.mc.displayGuiScreen(new GuiYesNo((result, id) -> {
                Iris.setDebug(result);
                this.mc.displayGuiScreen(this);
            }, "Shader debug mode toggle",
                "Debug mode helps investigate problems and shows shader errors. Would you like to enable it?", 0));
            return;
        }
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_G) {
            this.mc.displayGuiScreen(new GuiYesNo((result, id) -> {
                Iris.setAllowUnknownShaders(result);
                this.mc.displayGuiScreen(this);
            }, "Unknown shader toggle",
                "This allows unknown shaders to load in.", 0));
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (this.guiHidden) {
                this.guiHidden = false;
                this.initGui();
                return;
            } else if (this.navigation != null && this.navigation.hasHistory()) {
                this.navigation.back();
                return;
            } else if (this.optionMenuOpen) {
                this.optionMenuOpen = false;
                this.initGui();
                return;
            }
        }
        if (keyCode == Keyboard.KEY_F1 && this.mc.theWorld != null) {
            this.guiHidden = !this.guiHidden;
            this.initGui();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            final boolean canOpenOptions = shaderPackList.getTopButtonRow().shadersEnabled
                && Iris.getCurrentPack().map(p -> !p.getMenuContainer().mainScreen.elements.isEmpty()).orElse(false);
            if (this.optionMenuOpen || canOpenOptions) {
                this.optionMenuOpen = !this.optionMenuOpen;
                this.applyChanges();
                this.initGui();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    public void displayNotification(String String) {
        this.notificationDialog = String;
        this.notificationDialogTimer = 100;
    }

    private void handleDroppedFiles() {
        final List<String> dropped = BackendManager.RENDER_BACKEND.pollDroppedFiles();
        if (dropped.isEmpty()) {
            return;
        }
        final List<Path> paths = dropped.stream().map(Paths::get).collect(Collectors.toList());
        if (this.optionMenuOpen) {
            onOptionMenuFilesDrop(paths);
        } else {
            onPackListFilesDrop(paths);
        }
    }

    public void onPackListFilesDrop(List<Path> paths) {
        final List<Path> packs = paths.stream().filter(Iris::isValidShaderpack).toList();

        for (Path pack : packs) {
            final String fileName = pack.getFileName().toString();
            try {
                Iris.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
            } catch (FileAlreadyExistsException e) {
                displayNotification(I18n.format("options.iris.shaderPackSelection.copyErrorAlreadyExists", fileName));
                this.shaderPackList.refresh();
                return;
            } catch (IOException e) {
                Iris.logger.warn("Error copying dragged shader pack", e);
                displayNotification(I18n.format("options.iris.shaderPackSelection.copyError", fileName));
                this.shaderPackList.refresh();
                return;
            }
        }

        // After copying, refresh the list so the new packs show up
        this.shaderPackList.refresh();

        if (packs.isEmpty()) {
            if (paths.size() == 1) {
                displayNotification(I18n.format("options.iris.shaderPackSelection.failedAddSingle", paths.getFirst().getFileName().toString()));
            } else {
                displayNotification(I18n.format("options.iris.shaderPackSelection.failedAdd"));
            }
        } else if (packs.size() == 1) {
            final String packName = packs.getFirst().getFileName().toString();
            displayNotification(I18n.format("options.iris.shaderPackSelection.addedPack", packName));
            // Select the freshly-added pack, since the user probably wants to use it
            this.shaderPackList.select(packName);
        } else {
            displayNotification(I18n.format("options.iris.shaderPackSelection.addedPacks", packs.size()));
        }
    }

    public void onOptionMenuFilesDrop(List<Path> paths) {
        // Only one settings file should be imported at a time
        if (paths.size() != 1) {
            displayNotification(I18n.format("options.iris.shaderPackOptions.tooManyFiles"));
            return;
        }
        importPackOptions(paths.getFirst());
    }

    public void importPackOptions(Path settingFile) {
        try (InputStream in = Files.newInputStream(settingFile)) {
            final Properties properties = new Properties();
            properties.load(in);

            Iris.queueShaderPackOptionsFromProperties(properties);

            displayNotification(I18n.format("options.iris.shaderPackOptions.importedSettings", settingFile.getFileName().toString()));

            if (this.navigation != null) {
                this.navigation.refresh();
            }
        } catch (Exception e) {
            Iris.logger.error("Error importing shader settings file \"" + settingFile + "\"", e);
            displayNotification(I18n.format("options.iris.shaderPackOptions.failedImport", settingFile.getFileName().toString()));
        }
    }

    public void onClose() {
        if (!dropChanges) {
            applyChanges();
        } else {
            discardChanges();
        }

        this.mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        BackendManager.RENDER_BACKEND.stopFileDrop();

        if (this.shaderPackList != null) {
            try {
                this.shaderPackList.close();
            } catch (IOException e) {
                Iris.logger.error("Failed to safely close shaderpack selection!", e);
            }
        }
    }

    private void dropChangesAndClose() {
        dropChanges = true;
        onClose();
    }

    public void applyChanges() {
        final ShaderPackEntry entry = this.shaderPackList.getSelected();

        if (entry == null) return;

        this.shaderPackList.setApplied(entry);

        final String name = entry.getPackName();

        // If the pack is being changed, clear pending options from the previous pack to
        // avoid possible undefined behavior from applying one pack's options to another pack
        if (!name.equals(Iris.getCurrentPackName())) {
            Iris.clearShaderPackOptionQueue();
        }

        final boolean enabled = this.shaderPackList.getTopButtonRow().shadersEnabled;

        final String previousPackName = Iris.getIrisConfig().getShaderPackName().orElse(null);
        final boolean previousShadersEnabled = Iris.getIrisConfig().areShadersEnabled();

        // Only reload if the pack would be different from before, or shaders were toggled, or options were changed, or if we're about to reset options.
        if (!name.equals(previousPackName) || enabled != previousShadersEnabled || !Iris.getShaderPackOptionQueue().isEmpty() || Iris.shouldResetShaderPackOptionsOnNextReload()) {
            Iris.getIrisConfig().setShaderPackName(name);
            IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
        }

        refreshForChangedPack();
    }

    private void discardChanges() {
        Iris.clearShaderPackOptionQueue();
    }

    private void openShaderPackFolder() {
        CompletableFuture.runAsync(() -> openUri(Iris.getShaderpacksDirectoryManager().getDirectoryUri()));
    }

    public void openLinkConfirm(String url) {
        this.mc.displayGuiScreen(new GuiConfirmOpenLink((result, id) -> {
            if (result) {
                try {
                    openUri(new URI(url));
                } catch (URISyntaxException e) {
                    Iris.logger.error("Invalid shader download URL: " + url, e);
                }
            }
            this.mc.displayGuiScreen(this);
        }, url, 0, true) {
            @Override
            public void initGui() {
                super.initGui();
                for (int i = 0; i < this.buttonList.size(); i++) {
                    this.buttonList.get(i).xPosition = this.width / 2 - 155 + i * 105;
                }
            }
        });
    }

    private void openUri(URI uri) {
        switch (Util.getOSType()) {
            case OSX -> {
                try {
                    Runtime.getRuntime().exec(new String[] { "/usr/bin/open", uri.toString() });
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            case WINDOWS -> {
                try {
                    Runtime.getRuntime().exec(new String[] { "rundll32", "url.dll,FileProtocolHandler", uri.toString() });
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            case LINUX -> {
                try {
                    Runtime.getRuntime().exec(new String[] { "xdg-open", uri.toString() });
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            default -> {
            }
        }
        boolean openViaSystemClass = false;

        try {
            final Class<?> aClass = Class.forName("java.awt.Desktop");
            final Object getDesktop = aClass.getMethod("getDesktop").invoke(null);
            aClass.getMethod("browse", URI.class).invoke(getDesktop, uri);
        } catch (Exception e) {
            e.printStackTrace();
            openViaSystemClass = true;
        }

        if (openViaSystemClass) {
            AngelicaMod.LOGGER.debug("Opening via system class!");
            Sys.openURL("file://" + uri);
        }
    }

    // Let the screen know if an element is hovered or not, allowing for accurately updating which element is hovered
    public void setElementHoveredStatus(AbstractElementWidget<?> widget, boolean hovered) {
        // TODO: Unused, but might be useful for shader options
        if (hovered && widget != this.hoveredElement) {
            this.hoveredElement = widget;

            if (widget instanceof CommentedElementWidget) {
                this.hoveredElementCommentTitle = ((CommentedElementWidget<?>) widget).getCommentTitle();

                Optional<String> commentBody = ((CommentedElementWidget<?>) widget).getCommentBody();
                if (commentBody.isEmpty()) {
                    this.hoveredElementCommentBody.clear();
                } else {
                    String rawCommentBody = commentBody.get();

                    // Strip any trailing periods
                    if (rawCommentBody.endsWith(".")) {
                        rawCommentBody = rawCommentBody.substring(0, rawCommentBody.length() - 1);
                    }
                    // Split comment body into lines by separator ". "
                    List<String> splitByPeriods = Arrays.stream(rawCommentBody.split("\\. +")).toList();
                    // Line wrap
                    this.hoveredElementCommentBody = new ArrayList<>();
                    for (String text : splitByPeriods) {
                        this.hoveredElementCommentBody.addAll(this.fontRendererObj.listFormattedStringToWidth(text, COMMENT_PANEL_WIDTH - 8));
                    }
                }
            } else {
                this.hoveredElementCommentTitle = Optional.empty();
                this.hoveredElementCommentBody.clear();
            }

            this.hoveredElementCommentTimer = 0;
        } else if (!hovered && widget == this.hoveredElement) {
            this.hoveredElement = null;
            this.hoveredElementCommentTitle = Optional.empty();
            this.hoveredElementCommentBody.clear();
            this.hoveredElementCommentTimer = 0;
        }
    }

    public boolean isDisplayingComment() {
        return this.hoveredElementCommentTimer > 10 &&
            this.hoveredElementCommentTitle.isPresent() &&
            !this.hoveredElementCommentBody.isEmpty();
    }
    public void drawCenteredString(String text, int x, int y, int color) {
        this.drawCenteredString(this.fontRendererObj, text, x, y, color);
    }
    public void drawString(String text, int x, int y, int color) {
        this.drawString(this.fontRendererObj, text, x, y, color);
    }


    public FontRenderer getFontRenderer() {
        return this.fontRendererObj;
    }
}
