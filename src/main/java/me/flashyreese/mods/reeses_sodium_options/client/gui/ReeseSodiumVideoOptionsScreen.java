package me.flashyreese.mods.reeses_sodium_options.client.gui;

import com.gtnewhorizons.angelica.client.gui.DynamicLightsOptionPages;
import com.gtnewhorizons.angelica.client.gui.FontConfigScreen;
import com.gtnewhorizons.angelica.client.gui.RendererOptionPages;
import com.gtnewhorizons.angelica.client.gui.ScrollableGuiScreen;
import com.gtnewhorizons.angelica.client.gui.TracyOptionPages;
import com.gtnewhorizons.angelica.compat.mojang.Element;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.profiling.TracyOptions;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.AbstractFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.BasicFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.SearchTextFieldComponent;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.Tab;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.TabFrame;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ReeseSodiumVideoOptionsScreen extends ScrollableGuiScreen {
    private static final float ASPECT_RATIO = 5f / 4f;
    private static final int MINIMUM_WIDTH = 550;

    @Nullable
    private Element focused;

    private static final AtomicReference<String> tabFrameSelectedTab = new AtomicReference<>(null);
    private static final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    private static final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    private static final AtomicReference<String> lastSearch = new AtomicReference<>("");

    private final List<Element> children = new CopyOnWriteArrayList<>();
    private final List<OptionPage> pages = new ArrayList<>();

    public final GuiScreen prevScreen;

    private FlatButtonWidget applyButton, closeButton, undoButton;
    private boolean hasPendingChanges;
    private String pendingPage;

    private AbstractFrame frame;
    private SearchTextFieldComponent searchTextField;

    public ReeseSodiumVideoOptionsScreen(GuiScreen prevScreen) {
        this.prevScreen = prevScreen;

        this.pages.add(RendererOptionPages.withLink(SodiumGameOptionPages.general(), this));
        this.pages.add(RendererOptionPages.renderer());
        this.pages.add(SodiumGameOptionPages.quality());
        this.pages.add(SodiumGameOptionPages.advanced());
        this.pages.add(SodiumGameOptionPages.performance());
        this.pages.add(SodiumGameOptionPages.fpsReducer());
        this.pages.add(SodiumGameOptionPages.appearance());
        this.pages.add(SodiumGameOptionPages.text());

        if (DynamicLights.configEnabled) {
            this.pages.add(DynamicLightsOptionPages.dynamicLights());
        }

        if (SystemProperties.debugTooling() || TracyOptions.backendPresent()) {
            this.pages.add(TracyOptionPages.tracy());
        }
    }

    public void showPage(String name) {
        this.pendingPage = name;
    }

    private void applyPendingPage() {
        if (this.pendingPage == null) return;
        lastSearch.set("");
        tabFrameSelectedTab.set(this.pendingPage);
        optionPageScrollBarOffset.set(0);
        this.pendingPage = null;
        this.rebuildGUI();
    }

    @Override
    public List<? extends Element> children() {
        return children;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    // Hackalicious! Rebuild UI
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
        Keyboard.enableRepeatEvents(true);
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

        // Clamp width on wide screens
        int newWidth = this.width;
        if (this.width > MINIMUM_WIDTH && (float) this.width / (float) this.height > ASPECT_RATIO) {
            newWidth = Math.max(MINIMUM_WIDTH, (int) (this.height * ASPECT_RATIO));
        }

        final Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
        final Dim2i tabFrameDim = new Dim2i(basicFrameDim.getOriginX() + basicFrameDim.getWidth() / 20 / 2, basicFrameDim.getOriginY() + basicFrameDim.getHeight() / 4 / 2, basicFrameDim.getWidth() - (basicFrameDim.getWidth() / 20), basicFrameDim.getHeight() / 4 * 3);

        final Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
        final Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
        final Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

        this.undoButton = new FlatButtonWidget(undoButtonDim, I18n.format("sodium.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(applyButtonDim, I18n.format("sodium.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(closeButtonDim, I18n.format("gui.done"), this::onClose);

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
        this.applyPendingPage();
        super.drawDefaultBackground();
        handleMouseScroll(mouseX, mouseY, partialTicks);
        this.updateControls();
        this.frame.render(mouseX, mouseY, partialTicks);
    }

    private void updateControls() {
        final boolean hasChanges = this.getAllOptions().anyMatch(Option::hasChanged);

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


        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            SodiumGameOptions.applyAtlasSettings();
            this.mc.refreshResources();
        } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
            this.mc.renderGlobal.loadRenderers();
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
        if(focused != null) {
            focused.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        final boolean onSearchField = this.searchTextField.isMouseOver(mouseX, mouseY);

        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.children.forEach(element -> element.mouseClicked(mouseX, mouseY, mouseButton));

        if (!onSearchField) {
            this.searchTextField.setFocused(false);
            this.setFocused(this.frame);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceLastClick);

        this.children.forEach(element -> element.mouseDragged(mouseX, mouseY, mouseButton));
    }

    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    // We can't override onGuiClosed due to StackOverflow
    public void onClose() {
        lastSearch.set("");
        this.mc.displayGuiScreen(this.prevScreen);
        super.onGuiClosed();
    }
}
