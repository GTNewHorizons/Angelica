package me.jellysquid.mods.sodium.client.gui;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.client.gui.ScrollableGuiScreen;
import com.gtnewhorizons.angelica.compat.mojang.Drawable;
import com.gtnewhorizons.angelica.compat.mojang.Element;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import jss.notfine.gui.GuiCustomMenu;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.gui.options.control.element.SodiumControlElementFactory;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.utils.URLUtils;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class SodiumOptionsGUI extends ScrollableGuiScreen {

    protected static final SodiumControlElementFactory elementFactory = new SodiumControlElementFactory();

    protected final List<Element> children = new CopyOnWriteArrayList<>();

    protected final List<OptionPage> pages = new ArrayList<>();

    protected final List<ControlElement<?>> controls = new ArrayList<>();
    protected final List<Drawable> drawable = new ArrayList<>();

    public final GuiScreen prevScreen;

    protected OptionPage currentPage;

    protected FlatButtonWidget applyButton, closeButton, undoButton;
    protected FlatButtonWidget donateButton, hideDonateButton;

    protected boolean hasPendingChanges;
    protected ControlElement<?> hoveredElement;

    protected OptionPage shaderPacks;


    public SodiumOptionsGUI(GuiScreen prevScreen) {
        this.prevScreen = prevScreen;

        this.pages.add(SodiumGameOptionPages.general());
        this.pages.add(SodiumGameOptionPages.quality());
        this.pages.add(SodiumGameOptionPages.advanced());
        this.pages.add(SodiumGameOptionPages.performance());

        if (AngelicaConfig.enableIris) {
            shaderPacks = new OptionPage(I18n.format("options.iris.shaderPackSelection"), ImmutableList.of());
            this.pages.add(shaderPacks);
        }
    }

    public void setPage(OptionPage page) {
        if (AngelicaConfig.enableIris && page == shaderPacks) {
            mc.displayGuiScreen(new ShaderPackScreen(this));
            return;
        }

        this.currentPage = page;

        this.rebuildGUI();
    }

    @Override
    public void initGui() {
        this.rebuildGUI();
    }

    protected void rebuildGUI() {
        this.controls.clear();
        this.children.clear();
        this.drawable.clear();

        if (this.currentPage == null) {
            if (this.pages.isEmpty()) {
                throw new IllegalStateException("No pages are available?!");
            }

            // Just use the first page for now
            this.currentPage = this.pages.get(0);
        }

        this.rebuildGUIPages();
        this.rebuildGUIOptions();

        this.undoButton = new FlatButtonWidget(new Dim2i(this.width - 211, this.height - 26, 65, 20), I18n.format("sodium.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(new Dim2i(this.width - 142, this.height - 26, 65, 20), I18n.format("sodium.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(new Dim2i(this.width - 73, this.height - 26, 65, 20), I18n.format("gui.done"), this::onClose);
        final String donateToJelly = I18n.format("sodium.options.buttons.donate");
        final int width = 12 + this.fontRendererObj.getStringWidth(donateToJelly);
        this.donateButton = new FlatButtonWidget(new Dim2i(this.width - width - 32, 6, width, 20), donateToJelly, () -> openDonationPage("https://caffeinemc.net/donate"));
        this.hideDonateButton = new FlatButtonWidget(new Dim2i(this.width - 26, 6, 20, 20), "x", this::hideDonationButton);

        if (SodiumClientMod.options().notifications.hideDonationButton) {
            this.setDonationButtonVisibility(false);
        }

        this.children.add(this.undoButton);
        this.children.add(this.applyButton);
        this.children.add(this.closeButton);
        this.children.add(this.donateButton);
        this.children.add(this.hideDonateButton);

        for (Element element : this.children) {
            if (element instanceof Drawable) {
                this.drawable.add((Drawable) element);
            }
        }
    }

    private void setDonationButtonVisibility(boolean value) {
        this.donateButton.setVisible(value);
        this.hideDonateButton.setVisible(value);
    }

    private void hideDonationButton() {
        final SodiumGameOptions options = SodiumClientMod.options();
        options.notifications.hideDonationButton = true;

        try {
            options.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }

        this.setDonationButtonVisibility(false);
    }

    private void rebuildGUIPages() {
        int x = 6;
        final int y = 6;

        for (OptionPage page : this.pages) {
            final int width = 12 + this.fontRendererObj.getStringWidth(page.getName());

            final FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, width, 18), page.getName(), () -> this.setPage(page));
            button.setSelected(this.currentPage == page);

            x += width + 6;

            this.children.add(button);
        }
    }

    private void rebuildGUIOptions() {
        final int x = 6;
        int y = 28;

        for (OptionGroup group : this.currentPage.getGroups()) {
            // Add each option's control element
            for (Option<?> option : group.getOptions()) {
                final Control<?> control = option.getControl();
                final ControlElement<?> element = control.createElement(new Dim2i(x, y, 200, 18), elementFactory);

                this.controls.add(element);
                //Safe if SodiumControlElementFactory or a compatible ControlElementFactory is used to create element.
                this.children.add((Element)element);

                // Move down to the next option
                y += 18;
            }

            // Add padding beneath each option group
            y += 4;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        super.drawScreen(mouseX, mouseY, delta);
        super.drawDefaultBackground();

        this.updateControls();

        for (Drawable drawable : this.drawable) {
            drawable.render(mouseX, mouseY, delta);
        }

        if (this.hoveredElement != null) {
            this.renderOptionTooltip(this.hoveredElement);
        }
    }

    @Override
    public List<? extends Element> children() {
        return children;
    }

    private void updateControls() {
        ControlElement<?> hovered = this.getActiveControls()
                .filter(ControlElement::isHovered)
                .findFirst()
                .orElse(null);

        boolean hasChanges = this.getAllOptions().anyMatch(Option::hasChanged);

        for (OptionPage page : this.pages) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    hasChanges = true;
                }
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
        this.hoveredElement = hovered;
    }

    private Stream<Option<?>> getAllOptions() {
        return this.pages.stream()
                .flatMap(s -> s.getOptions().stream());
    }

    private Stream<ControlElement<?>> getActiveControls() {
        return this.controls.stream();
    }

    private void renderOptionTooltip(ControlElement<?> element) {
        final Dim2i dim = element.getDimensions();

        final int textPadding = 3;
        final int boxPadding = 3;

        final int boxWidth = 200;

        int boxY = dim.getOriginY();
        final int boxX = dim.getLimitX() + boxPadding;

        final Option<?> option = element.getOption();
        final List<String> tooltip = new ArrayList<>(this.fontRendererObj.listFormattedStringToWidth(option.getTooltip(), boxWidth - (textPadding * 2)));

        final OptionImpact impact = option.getImpact();

        if (impact != null) {
        	tooltip.add(EnumChatFormatting.GRAY + I18n.format("sodium.options.performance_impact_string", impact.toDisplayString()));
        }

        final int boxHeight = (tooltip.size() * 12) + boxPadding;
        final int boxYLimit = boxY + boxHeight;
        final int boxYCutoff = this.height - 40;

        // If the box is going to be cutoff on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        this.drawGradientRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000, 0xE0000000);

        for (int i = 0; i < tooltip.size(); i++) {
            this.fontRendererObj.drawString(tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    private void applyChanges() {
        final HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        this.getAllOptions().forEach((option -> {
            if (!option.hasChanged()) {
                return;
            }

            option.applyChanges();

            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }));

        if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            this.mc.renderGlobal.loadRenderers();
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            this.mc.getTextureMapBlocks().setMipmapLevels(this.mc.gameSettings.mipmapLevels);
            this.mc.refreshResources();
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
    }

    private void undoChanges() {
        this.getAllOptions().forEach(Option::reset);
    }

    private void openDonationPage(String url) {
        URLUtils.open(url);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if(keyCode == Keyboard.KEY_ESCAPE && !shouldCloseOnEsc()) {
            return;
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            onClose();
            return;
        }

        if (keyCode == Keyboard.KEY_P && isShiftKeyDown()) {
            this.mc.displayGuiScreen(new GuiCustomMenu(this.prevScreen, SodiumGameOptionPages.general(),
                SodiumGameOptionPages.quality(), SodiumGameOptionPages.advanced(), SodiumGameOptionPages.performance()));
        }
    }

    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    // We can't override onGuiClosed due to StackOverflow
    public void onClose() {
        this.mc.displayGuiScreen(this.prevScreen);
        super.onGuiClosed();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        this.children.forEach(element -> element.mouseClicked(mouseX, mouseY, mouseButton));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceLastClick);

        this.children.forEach(element -> element.mouseDragged(mouseX, mouseY, mouseButton));
    }

}
