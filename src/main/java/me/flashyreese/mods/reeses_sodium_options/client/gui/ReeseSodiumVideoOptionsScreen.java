package me.flashyreese.mods.reeses_sodium_options.client.gui;

import com.gtnewhorizons.angelica.compat.mojang.Element;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import jss.notfine.gui.GuiCustomMenu;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.AbstractFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.BasicFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.components.SearchTextFieldComponent;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.Tab;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.TabFrame;
import com.gtnewhorizons.angelica.client.gui.SodiumGameOptionPages;
import com.gtnewhorizons.angelica.client.gui.SodiumOptionsGUI;
import com.gtnewhorizons.angelica.client.gui.options.Option;
import com.gtnewhorizons.angelica.client.gui.options.OptionFlag;
import com.gtnewhorizons.angelica.client.gui.options.OptionPage;
import com.gtnewhorizons.angelica.client.gui.options.storage.OptionStorage;
import com.gtnewhorizons.angelica.client.gui.widgets.FlatButtonWidget;
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
import java.util.stream.Stream;

public class ReeseSodiumVideoOptionsScreen extends SodiumOptionsGUI {
    @Nullable
    private Element focused;

    private static final AtomicReference<String> tabFrameSelectedTab = new AtomicReference<>(null);
    private static final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
    private static final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

    private static final AtomicReference<String> lastSearch = new AtomicReference<>("");
    private static final AtomicReference<Integer> lastSearchIndex = new AtomicReference<>(0);

    private AbstractFrame frame;
    private SearchTextFieldComponent searchTextField;

    public ReeseSodiumVideoOptionsScreen(GuiScreen prevScreen) {
        super(prevScreen);
    }

    // Hackalicious! Rebuild UI
    @Override
    public void rebuildGUI() {
        this.children.clear();
        this.initGui();
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

        Dim2i searchTextFieldDim = new Dim2i(tabFrameDim.getOriginX(), tabFrameDim.getOriginY() - 26, tabFrameDim.getWidth(), 20);



        basicFrameBuilder = this.parentBasicFrameBuilder(basicFrameDim, tabFrameDim);

        if(AngelicaConfig.enableIris) {
            //int size = this.client.textRenderer.getWidth(new TranslatableText(IrisApi.getInstance().getMainScreenLanguageKey()));
            final String irisText = I18n.format(IrisApi.getInstance().getMainScreenLanguageKey());
            final int size = this.mc.fontRenderer.getStringWidth(irisText);
            final Dim2i shaderPackButtonDim = new Dim2i(tabFrameDim.getLimitX() - size - 10, tabFrameDim.getOriginY() - 26, 10 + size, 20);

            searchTextFieldDim = new Dim2i(tabFrameDim.getOriginX(), tabFrameDim.getOriginY() - 26, tabFrameDim.getWidth() - (tabFrameDim.getLimitX() - shaderPackButtonDim.getOriginX()) - 2, 20);

            //FlatButtonWidget shaderPackButton = new FlatButtonWidget(shaderPackButtonDim, new TranslatableText(IrisApi.getInstance().getMainScreenLanguageKey()), () -> this.client.setScreen((Screen) IrisApi.getInstance().openMainIrisScreenObj(this)));
            final FlatButtonWidget shaderPackButton = new FlatButtonWidget(shaderPackButtonDim, irisText, () -> mc.displayGuiScreen(new ShaderPackScreen(this)));
            basicFrameBuilder.addChild(dim -> shaderPackButton);
        }

        this.searchTextField = new SearchTextFieldComponent(searchTextFieldDim, this.pages, tabFrameSelectedTab,
                tabFrameScrollBarOffset, optionPageScrollBarOffset, tabFrameDim.getHeight(), this, lastSearch, lastSearchIndex);

        basicFrameBuilder.addChild(dim -> this.searchTextField);

        return basicFrameBuilder;
    }

    public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
        return BasicFrame.createBuilder()
                .setDimension(parentBasicFrameDim)
                .shouldRenderOutline(false)
                .addChild(parentDim -> TabFrame.createBuilder()
                        .setDimension(tabFrameDim)
                        .shouldRenderOutline(false)
                        .setTabSectionScrollBarOffset(tabFrameScrollBarOffset)
                        .setTabSectionSelectedTab(tabFrameSelectedTab)
                        .addTabs(tabs -> this.pages
                                .stream()
                                .filter(page -> !page.getGroups().isEmpty())
                                .forEach(page -> tabs.add(Tab.createBuilder().from(page, optionPageScrollBarOffset)))
                        )
                        .onSetTab(() -> {
                            optionPageScrollBarOffset.set(0);
                        })
                        .build()
                )
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
        lastSearchIndex.set(0);
        super.onClose();
    }
}
