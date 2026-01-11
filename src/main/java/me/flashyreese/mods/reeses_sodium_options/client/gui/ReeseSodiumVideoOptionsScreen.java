package me.flashyreese.mods.reeses_sodium_options.client.gui;

import com.gtnewhorizons.angelica.client.gui.FontConfigScreen;
import com.gtnewhorizons.angelica.compat.mojang.Element;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import jss.notfine.gui.GuiCustomMenu;
import net.coderbot.iris.Iris;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.AbstractFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.BasicFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.SearchTextFieldComponent;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.Tab;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.TabFrame;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ReeseSodiumVideoOptionsScreen extends SodiumOptionsGUI {
    @Nullable
    private Element focused;

    private static final AtomicReference<String> tabFrameSelectedTab = new AtomicReference<>(null);
    private static final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    private static final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    private static final AtomicReference<String> lastSearch = new AtomicReference<>("");

    private AbstractFrame frame;
    private SearchTextFieldComponent searchTextField;

    public ReeseSodiumVideoOptionsScreen(GuiScreen prevScreen) {
        super(prevScreen);
    }

    // Hackalicious! Rebuild UI
    @Override
    public void rebuildGUI() {
        // Preserve search focus state across rebuilds
        boolean wasSearchFocused = this.searchTextField != null && this.searchTextField.isFocused();
        this.children.clear();
        this.initGui();
        if (wasSearchFocused && this.searchTextField != null) {
            this.searchTextField.setFocused(true);
            this.setFocused(this.searchTextField);
        }
    }


    public void setFocused(@Nullable Element focused) {
        this.focused = focused;
    }

    @Nullable
    public Element getFocused() {
        return this.focused;
    }

    @Override
    public void initGui() {
        this.frame = this.parentFrameBuilder().build();
        this.children.add(this.frame);

        this.searchTextField.setFocused(!lastSearch.get().trim().isEmpty());
        if (this.searchTextField.isFocused()) {
            this.setFocused(this.searchTextField);
        } else {
            this.setFocused(this.frame);
        }
    }

    protected BasicFrame.Builder parentFrameBuilder() {
        final BasicFrame.Builder basicFrameBuilder;

        // Calculates if resolution exceeds 16:9 ratio, force 16:9
        int newWidth = this.width;
        if ((float) this.width / (float) this.height > 1.77777777778) {
            newWidth = (int) (this.height * 1.77777777778);
        }

        final Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
        final Dim2i tabFrameDim = new Dim2i(basicFrameDim.getOriginX() + basicFrameDim.getWidth() / 20 / 2, basicFrameDim.getOriginY() + basicFrameDim.getHeight() / 4 / 2, basicFrameDim.getWidth() - (basicFrameDim.getWidth() / 20), basicFrameDim.getHeight() / 4 * 3);

        final Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
        final Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
        final Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

        this.undoButton = new FlatButtonWidget(undoButtonDim, "Undo", this::undoChanges);
        this.applyButton = new FlatButtonWidget(applyButtonDim, "Apply", this::applyChanges);
        this.closeButton = new FlatButtonWidget(closeButtonDim, "Close", this::onClose);

        // Pre-compute button text and widths to avoid duplicate calculations
        final String irisText = Iris.enabled ? I18n.format(IrisApi.getInstance().getMainScreenLanguageKey()) : null;
        final int irisWidth = irisText != null ? this.mc.fontRenderer.getStringWidth(irisText) : 0;
        final String fontConfigText = AngelicaConfig.enableFontRenderer ? I18n.format("options.angelica.fontconfig") : null;
        final int fontConfigWidth = fontConfigText != null ? this.mc.fontRenderer.getStringWidth(fontConfigText) : 0;

        // Total width reserved for buttons (used for search field sizing)
        int buttonsWidth = 0;
        if (irisText != null) buttonsWidth += irisWidth + 12;
        if (fontConfigText != null) buttonsWidth += fontConfigWidth + 14;

        // Create search field before parentBasicFrameBuilder (which needs the predicate)
        Dim2i searchTextFieldDim = new Dim2i(tabFrameDim.getOriginX(), tabFrameDim.getOriginY() - 26, tabFrameDim.getWidth() - buttonsWidth, 20);
        this.searchTextField = new SearchTextFieldComponent(searchTextFieldDim, this.pages, tabFrameSelectedTab,
                tabFrameScrollBarOffset, optionPageScrollBarOffset, tabFrameDim.getHeight(), this, lastSearch);

        basicFrameBuilder = this.parentBasicFrameBuilder(basicFrameDim, tabFrameDim);

        // Add buttons after basicFrameBuilder is created
        int buttonX = tabFrameDim.getLimitX();
        if (irisText != null) {
            buttonX -= irisWidth + 10;
            final Dim2i shaderPackButtonDim = new Dim2i(buttonX, tabFrameDim.getOriginY() - 26, irisWidth + 10, 20);
            final FlatButtonWidget shaderPackButton = new FlatButtonWidget(shaderPackButtonDim, irisText, () -> mc.displayGuiScreen(new ShaderPackScreen(this)));
            basicFrameBuilder.addChild(dim -> shaderPackButton);
            buttonX -= 2; // gap between buttons
        }

        if (fontConfigText != null) {
            buttonX -= fontConfigWidth + 12;
            final Dim2i fontConfigButtonDim = new Dim2i(buttonX, tabFrameDim.getOriginY() - 26, fontConfigWidth + 12, 20);
            final FlatButtonWidget fontConfigButton = new FlatButtonWidget(fontConfigButtonDim, fontConfigText, () -> mc.displayGuiScreen(new FontConfigScreen(this)));
            basicFrameBuilder.addChild(dim -> fontConfigButton);
        }

        basicFrameBuilder.addChild(dim -> this.searchTextField);

        return basicFrameBuilder;
    }

    public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
        final Predicate<Option<?>> optionPredicate = searchTextField.getOptionPredicate();
        final boolean noResults = searchTextField.hasNoResults();

        BasicFrame.Builder builder = BasicFrame.createBuilder()
                .setDimension(parentBasicFrameDim)
                .shouldRenderOutline(false);

        if (noResults) {
            // Show "No matching options" message centered in the tab area
            final String noResultsText = I18n.format("options.angelica.search.no_results");
            builder.addChild(dim -> new me.jellysquid.mods.sodium.client.gui.widgets.AbstractWidget() {
                @Override
                public void render(int mouseX, int mouseY, float delta) {
                    int textWidth = mc.fontRenderer.getStringWidth(noResultsText);
                    int x = tabFrameDim.getCenterX() - textWidth / 2;
                    int y = tabFrameDim.getCenterY();
                    drawString(noResultsText, x, y, 0x808080);
                }
            });
        } else {
            builder.addChild(parentDim -> TabFrame.createBuilder()
                    .setDimension(tabFrameDim)
                    .shouldRenderOutline(false)
                    .setTabSectionScrollBarOffset(tabFrameScrollBarOffset)
                    .setTabSectionSelectedTab(tabFrameSelectedTab)
                    .addTabs(tabs -> this.pages
                            .stream()
                            .filter(this::canShowPage)
                            .forEach(page -> tabs.add(Tab.createBuilder().from(page, optionPredicate, optionPageScrollBarOffset)))
                    )
                    .onSetTab(() -> {
                        optionPageScrollBarOffset.set(0);
                    })
                    .build()
            );
        }

        return builder
                .addChild(dim -> this.undoButton)
                .addChild(dim -> this.applyButton)
                .addChild(dim -> this.closeButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawDefaultBackground();
        handleMouseScroll(mouseX, mouseY, partialTicks);
        this.updateControls();
        this.frame.render(mouseX, mouseY, partialTicks);
    }

    private void updateControls() {
        boolean hasChanges = this.getAllOptions()
                .anyMatch(Option::hasChanged);

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
    }

    private Stream<Option<?>> getAllOptions() {
        return this.pages.stream().flatMap(s -> s.getOptions().stream());
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

    /**
     * Check if a page has any options that match the current search filter.
     */
    private boolean canShowPage(OptionPage page) {
        if (page.getGroups().isEmpty()) {
            return false;
        }

        Predicate<Option<?>> predicate = searchTextField.getOptionPredicate();
        for (OptionGroup group : page.getGroups()) {
            for (Option<?> option : group.getOptions()) {
                if (predicate.test(option)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if(keyCode == Keyboard.KEY_ESCAPE && !shouldCloseOnEsc()) {
            return;
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            onClose();
            return;
        }
        if (keyCode == Keyboard.KEY_P) {
            if(isShiftKeyDown()){
                this.mc.displayGuiScreen(new GuiCustomMenu(this.prevScreen, SodiumGameOptionPages.general(), SodiumGameOptionPages.quality(), SodiumGameOptionPages.advanced(), SodiumGameOptionPages.performance()));
            } else if (isCtrlKeyDown()) {
                this.mc.displayGuiScreen(new SodiumOptionsGUI(this.prevScreen));
            }
        }

        if(focused != null) {
            focused.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    @Override
    public void onClose() {
        lastSearch.set("");
        super.onClose();
    }
}
