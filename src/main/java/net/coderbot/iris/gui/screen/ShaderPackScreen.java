package net.coderbot.iris.gui.screen;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
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
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ShaderPackScreen extends GuiScreen implements HudHideable {
    /**
     * Queue rendering to happen on top of all elements. Useful for tooltips or dialogs.
     */
    public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

    private static final String SELECT_TITLE = I18n.format("pack.iris.select.title");
    private static final String CONFIGURE_TITLE = I18n.format("pack.iris.configure.title");
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
    private static final String development = "Development Environment";
    private String developmentComponent;
    private String updateComponent;

    private boolean guiHidden = false;
    private boolean dirty = false;
    private float guiButtonHoverTimer = 0.0f;

    public ShaderPackScreen(GuiScreen parent) {
        this.title = I18n.format("options.iris.shaderPackSelection.title");

        this.parent = parent;

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
        if(dirty) {
            dirty = false;
            this.initGui();
        }

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

        final float previousHoverTimer = this.guiButtonHoverTimer;
        super.drawScreen(mouseX, mouseY, delta);
        if (previousHoverTimer == this.guiButtonHoverTimer) {
            this.guiButtonHoverTimer = 0.0f;
        }

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
                // Determine panel height and position
                final int panelHeight = Math.max(50, 18 + (this.hoveredElementCommentBody.size() * 10));
                int x = mouseX + 5;
                if (x + 314 >= (this.width - 4)) x = this.width - (318);
                int y = mouseY + 8;
                if (y + panelHeight >= (this.height - 4)) y = this.height - (panelHeight + 4);
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
    }

    @Override
    public void initGui() {
        super.initGui();
        final int bottomCenter = this.width / 2 - 50;
        final int topCenter = this.width / 2 - 76;
        final boolean inWorld = this.mc.theWorld != null;

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
            final String showOrHide = this.guiHidden
                ? I18n.format("options.iris.gui.show")
                : I18n.format("options.iris.gui.hide");

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

            this.navigation = new NavigationController(currentPack.getMenuContainer());

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
            this.screenSwitchButton.enabled = optionMenuOpen || shaderPackList.getTopButtonRow().shadersEnabled;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
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
        super.keyTyped(typedChar, keyCode);
    }

    public void displayNotification(String String) {
        this.notificationDialog = String;
        this.notificationDialogTimer = 100;
    }

    public void onClose() {
        if (!dropChanges) {
            applyChanges();
        } else {
            discardChanges();
        }

        this.mc.displayGuiScreen(parent);
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

    private void openUri(URI uri) {
        switch (net.minecraft.util.Util.getOSType()) {
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
            final Object getDesktop = aClass.getMethod("getDesktop").invoke((Object) null);
            aClass.getMethod("browse", URI.class).invoke(getDesktop, uri);
        } catch (Exception e) {
            e.printStackTrace();
            openViaSystemClass = true;
        }

        if (openViaSystemClass) {
            AngelicaTweaker.LOGGER.debug("Opening via system class!");
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
                if (!commentBody.isPresent()) {
                    this.hoveredElementCommentBody.clear();
                } else {
                    String rawCommentBody = commentBody.get();

                    // Strip any trailing "."s
                    if (rawCommentBody.endsWith(".")) {
                        rawCommentBody = rawCommentBody.substring(0, rawCommentBody.length() - 1);
                    }
                    // Split comment body into lines by separator ". "
                    List<String> splitByPeriods = Arrays.stream(rawCommentBody.split("\\. [ ]*")).map(String::new).collect(Collectors.toList());
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
        return this.hoveredElementCommentTimer > 20 &&
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
