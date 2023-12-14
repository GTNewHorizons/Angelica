package net.coderbot.iris.gui.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

public class ShaderPackSelectionList extends IrisGuiSlot {
    private static final String PACK_LIST_LABEL = I18n.format("pack.iris.list.label");

    private final ShaderPackScreen screen;
//    @Getter
//    private final TopButtonRowEntry topButtonRow;

    @Setter
    @Getter
    private ShaderPackEntry applied = null;

    @Setter
    @Getter
    private ShaderPackEntry selected = null;

    private final List<BaseEntry> entries = new ArrayList<>();

    public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
        super(client, width, height, top, bottom, 20);

        this.screen = screen;
//        this.topButtonRow = new TopButtonRowEntry(this, Iris.getIrisConfig().areShadersEnabled());

        refresh();
    }

    public void refresh() {
//        this.clearEntries();

        Collection<String> names;

        try {
            names = Iris.getShaderpacksDirectoryManager().enumerate();
        } catch (Throwable e) {
            Iris.logger.error("Error reading files while constructing selection UI", e);

            // Not translating this since it's going to be seen very rarely,
            // We're just trying to get more information on a seemingly untraceable bug:
            // - https://github.com/IrisShaders/Iris/issues/785
            this.addLabelEntries(
                "",
                "There was an error reading your shaderpacks directory",
                "",
                "Check your logs for more information.",
                "Please file an issue report including a log file.",
                "If you are able to identify the file causing this, please include it in your report as well.",
                "Note that this might be an issue with folder permissions; ensure those are correct first."
            );

            return;
        }

//        this.addEntry(topButtonRow);

        // Only allow the enable/disable shaders button if the user has added a shader pack. Otherwise, the button will be disabled.
//        topButtonRow.allowEnableShadersButton = names.size() > 0;

        int index = 0;

        for (String name : names) {
            index++;
            addPackEntry(index, name);
        }

        this.addLabelEntries(PACK_LIST_LABEL);
    }

    public void addPackEntry(int index, String name) {
        final ShaderPackEntry entry = new ShaderPackEntry(index, this, name);

        Iris.getIrisConfig().getShaderPackName().ifPresent(currentPackName -> {
            if (name.equals(currentPackName)) {
                setSelected(entry);
                setApplied(entry);
            }
        });

        this.entries.add(entry);
    }

    public void addLabelEntries(String ... lines) {
        for (String text : lines) {
            this.entries.add(new LabelEntry(text));
        }
    }

    public void select(String name) {
        for (BaseEntry entry : this.entries) {
            if (entry instanceof ShaderPackEntry shaderPackEntry && shaderPackEntry.packName.equals(name)) {
                setSelected(shaderPackEntry);
                return;
            }
        }
    }

    @Override
    protected int getSize() {
        return this.entries.size();
    }

    @Override
    protected void elementClicked(int index, boolean b, int mouseX, int mouseY) {
        if(this.entries.get(index) instanceof ShaderPackEntry shaderPackEntry) {
            this.setSelected(shaderPackEntry);
        }
   }

    @Override
    protected boolean isSelected(int idx) {
        return this.entries.get(idx).equals(this.selected);
    }

    @Override
    protected void drawBackground() {

    }



    @Override
    protected void drawSlot(int index, int x, int y, int i3, Tessellator tessellator, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        entry.drawEntry(screen, index, (this.screen.width / 2), y + 1);

    }


    public static abstract class BaseEntry extends GuiLabel {
        protected BaseEntry() {}

        public abstract void drawEntry(ShaderPackScreen screen, int index, int x, int y);
    }

    public static class ShaderPackEntry extends BaseEntry {
        @Getter
        private final String packName;
        private final ShaderPackSelectionList list;
        private final int index;

        public ShaderPackEntry(int index, ShaderPackSelectionList list, String packName) {
            this.packName = packName;
            this.list = list;
            this.index = index;
        }

        public boolean isApplied() {
            return list.getApplied() == this;
        }

        public boolean isSelected() {
            return list.getSelected() == this;
        }

        @Override
        public void drawEntry(ShaderPackScreen screen, int index, int x, int y) {
            final FontRenderer font = screen.getFontRenderer();

            int color = 0xFFFFFF;
            String name = packName;
            if (font.getStringWidth(name) > this.list.getListWidth() - 3) {
                name = font.trimStringToWidth(name, this.list.getListWidth() - 8) + "...";
            }
            if(this.isSelected()) {
                name = "§l" + name;
            }
            if(this.isApplied()) {
                color = 0xFFF263;
            }



            screen.drawCenteredString(name, x, y, color);
        }

//
//        @Override
//        public boolean mouseClicked(double mouseX, double mouseY, int button) {
//            // Only do anything on left-click
//            if (button != 0) {
//                return false;
//            }
//
//            boolean didAnything = false;
//
//            // UX: If shaders are disabled, then clicking a shader in the list will also
//            //     enable shaders on apply. Previously, it was not possible to select
//            //     a pack when shaders were disabled, but this was a source of confusion
//            //     - people did not realize that they needed to enable shaders before
//            //     selecting a shader pack.
//            if (!list.getTopButtonRow().shadersEnabled) {
//                list.getTopButtonRow().setShadersEnabled(true);
//                didAnything = true;
//            }
//
//            if (!this.isSelected()) {
//                this.list.select(this.index);
//                didAnything = true;
//            }
//
//            return didAnything;
//        }
    }

    public static class LabelEntry extends BaseEntry {
        private final String label;

        public LabelEntry(String label) {
            this.label = label;
        }

        @Override
        public void drawEntry(ShaderPackScreen screen, int index, int x, int y) {
            screen.drawCenteredString(label, x, y, 0xFFFFFF);
        }
    }

//    public static class TopButtonRowEntry extends BaseEntry {
//        private static final Component REFRESH_SHADER_PACKS_LABEL = new TranslatableComponent("options.iris.refreshShaderPacks").withStyle(style -> style.withColor(TextColor.fromRgb(0x99ceff)));
//        private static final Component NONE_PRESENT_LABEL = new TranslatableComponent("options.iris.shaders.nonePresent").withStyle(ChatFormatting.GRAY);
//        private static final Component SHADERS_DISABLED_LABEL = new TranslatableComponent("options.iris.shaders.disabled");
//        private static final Component SHADERS_ENABLED_LABEL = new TranslatableComponent("options.iris.shaders.enabled");
//        private static final int REFRESH_BUTTON_WIDTH = 18;
//
//        private final ShaderPackSelectionList list;
//        private final IrisElementRow buttons = new IrisElementRow();
//        private final EnableShadersButtonElement enableDisableButton;
//        private final IrisElementRow.Element refreshPacksButton;
//
//        public boolean allowEnableShadersButton = true;
//        public boolean shadersEnabled;
//
//        public TopButtonRowEntry(ShaderPackSelectionList list, boolean shadersEnabled) {
//            this.list = list;
//            this.shadersEnabled = shadersEnabled;
//            this.enableDisableButton = new EnableShadersButtonElement(
//                getEnableDisableLabel(),
//                button -> {
//                    if (this.allowEnableShadersButton) {
//                        setShadersEnabled(!this.shadersEnabled);
//                        GuiUtil.playButtonClickSound();
//                        return true;
//                    }
//
//                    return false;
//                });
//            this.refreshPacksButton = new IrisElementRow.IconButtonElement(
//                GuiUtil.Icon.REFRESH,
//                button -> {
//                    this.list.refresh();
//
//                    GuiUtil.playButtonClickSound();
//                    return true;
//                });
//            this.buttons.add(this.enableDisableButton, 0).add(this.refreshPacksButton, REFRESH_BUTTON_WIDTH);
//        }
//
//        public void setShadersEnabled(boolean shadersEnabled) {
//            this.shadersEnabled = shadersEnabled;
//            this.enableDisableButton.text = getEnableDisableLabel();
//            this.list.screen.refreshScreenSwitchButton();
//        }
//
//        @Override
//        public void render(int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
//            this.buttons.setWidth(this.enableDisableButton, (entryWidth - 1) - REFRESH_BUTTON_WIDTH);
//            this.enableDisableButton.centerX = x + (int)(entryWidth * 0.5);
//
//            this.buttons.render(x - 2, y - 3, 18, mouseX, mouseY, tickDelta, hovered);
//
//            if (this.refreshPacksButton.isHovered()) {
//                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() ->
//                    GuiUtil.drawTextPanel(Minecraft.getInstance().font, REFRESH_SHADER_PACKS_LABEL,
//                        (mouseX - 8) - Minecraft.getInstance().font.width(REFRESH_SHADER_PACKS_LABEL), mouseY - 16));
//            }
//        }
//
//        private Component getEnableDisableLabel() {
//            return this.allowEnableShadersButton ? this.shadersEnabled ? SHADERS_ENABLED_LABEL : SHADERS_DISABLED_LABEL : NONE_PRESENT_LABEL;
//        }
//
//        @Override
//        public boolean mouseClicked(double mouseX, double mouseY, int button) {
//            return this.buttons.mouseClicked(mouseX, mouseY, button);
//        }
//
//        // Renders the label at an offset as to not look misaligned with the rest of the menu
//        public static class EnableShadersButtonElement extends IrisElementRow.TextButtonElement {
//            private int centerX;
//
//            public EnableShadersButtonElement(Component text, Function<IrisElementRow.TextButtonElement, Boolean> onClick) {
//                super(text, onClick);
//            }
//
//            @Override
//            public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
//                int textX = this.centerX - (int)(this.font.width(this.text) * 0.5);
//                int textY = y + (int)((height - 8) * 0.5);
//
//                this.font.drawShadow(this.text, textX, textY, 0xFFFFFF);
//            }
//        }
//    }
}
