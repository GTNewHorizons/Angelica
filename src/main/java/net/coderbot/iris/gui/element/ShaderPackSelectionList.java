package net.coderbot.iris.gui.element;

import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.entry.BaseEntry;
import net.coderbot.iris.gui.entry.LabelEntry;
import net.coderbot.iris.gui.entry.ShaderPackEntry;
import net.coderbot.iris.gui.entry.TopButtonRowEntry;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ShaderPackSelectionList extends IrisGuiSlot {
    @Getter
    private final ShaderPackScreen screen;
    @Getter
    private final TopButtonRowEntry topButtonRow;

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
        this.topButtonRow = new TopButtonRowEntry(this, Iris.getIrisConfig().areShadersEnabled());

        refresh();
    }

    public void refresh() {
        this.entries.clear();

        final Collection<String> names;

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

        this.entries.add(topButtonRow);

        // Only allow the enable/disable shaders button if the user has added a shader pack. Otherwise, the button will be disabled.
        topButtonRow.allowEnableShadersButton = names.size() > 0;

        int index = 0;

        for (String name : names) {
            index++;
            addPackEntry(index, name);
        }

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
            this.entries.add(new LabelEntry(this, text));
        }
    }

    public void select(String name) {
        for (BaseEntry entry : this.entries) {
            if (entry instanceof ShaderPackEntry shaderPackEntry && name.equals(shaderPackEntry.getPackName())) {
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
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        if(entry instanceof ShaderPackEntry shaderPackEntry) {
            this.setSelected(shaderPackEntry);
            if (!topButtonRow.shadersEnabled) {
                topButtonRow.setShadersEnabled(true);
            }
        } else if( entry instanceof TopButtonRowEntry topButtonRowEntry) {
            topButtonRowEntry.mouseClicked(mouseX, mouseY, 0);
        }
   }

    @Override
    protected boolean isSelected(int idx) {
        return this.entries.get(idx).equals(this.selected);
    }

    @Override
    public int getListWidth() {
        return Math.min(308, width - 50);
    }

    @Override
    protected void drawBackground() {

    }


    @Override
    protected void drawSlot(int index, int x, int y, int i1, Tessellator tessellator, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        final boolean isMouseOver = this.func_148124_c/*getSlotIndexFromScreenCoords*/(mouseX, mouseY) == index;
        entry.drawEntry(screen, index, x - 2, y + 4, this.getListWidth(), tessellator, mouseX, mouseY, isMouseOver);
    }

}
