package net.coderbot.iris.gui.element;

import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.element.shaderselection.BaseEntry;
import net.coderbot.iris.gui.element.shaderselection.DownloadEntry;
import net.coderbot.iris.gui.element.shaderselection.LabelEntry;
import net.coderbot.iris.gui.element.shaderselection.ShaderPackEntry;
import net.coderbot.iris.gui.element.shaderselection.TopButtonRowEntry;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
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

    private final WatchService watcher;
    private final WatchKey key;
    private boolean keyValid;

    public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
        super(client, width, height, top, bottom, 20);

        this.screen = screen;
        this.topButtonRow = new TopButtonRowEntry(this, Iris.getIrisConfig().areShadersEnabled());

        WatchService watcher1;
        WatchKey key1;
        try {
            watcher1 = FileSystems.getDefault().newWatchService();
            key1 = Iris.getShaderpacksDirectory().register(watcher1,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            keyValid = true;
        } catch (IOException e) {
            Iris.logger.error("Couldn't register shaderpacks directory file watcher!", e);
            watcher1 = null;
            key1 = null;
            keyValid = false;
        }
        this.watcher = watcher1;
        this.key = key1;

        refresh();
    }

    // Poll for new shaderpacks
    public void poll() {
        if (!keyValid) {
            return;
        }
        boolean changed = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            changed = true;
        }
        if (changed) {
            refresh();
        }
        keyValid = key.reset();
    }

    public void close() throws IOException {
        if (key != null) {
            key.cancel();
        }
        if (watcher != null) {
            watcher.close();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        poll();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public void refresh() {
        this.entries.clear();

        final Collection<String> names;

        try {
            names = Iris.getShaderpacksDirectoryManager().enumerate();
        } catch (Exception e) {
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

        // Download something man
        if (names.isEmpty()) {
            this.entries.add(new DownloadEntry(this, "Download Shaders", "https://modrinth.com/shaders"));
        }

        // Only allow the enable/disable shaders button if the user has added a shader pack. Otherwise, the button will be disabled.
        topButtonRow.allowEnableShadersButton = !names.isEmpty();

        int index = 0;
        String selectedName = Iris.getIrisConfig().getShaderPackName().orElse(null);
        int targetSelectionIndex = -1;

        for (String name : names) {
            this.addPackEntry(index, name);
            if (name.equals(selectedName)) {
                targetSelectionIndex = index;
            }
            index++;
        }

        // Try not to gaslight users in case they swap back and forth from lwjgl2 and lwjgl3
        final String footerKey = BackendManager.RENDER_BACKEND.supportsFileDrop()
            ? "pack.iris.list.label"
            : "pack.iris.list.label.lwjgl2";
        addLabelEntries(EnumChatFormatting.GRAY.toString() + EnumChatFormatting.ITALIC + I18n.format(footerKey));

        if (targetSelectionIndex != -1) {
            scrollToIndex(targetSelectionIndex);
        }
    }

    /**
     * Scrolls the list to center the entry at {@code targetIndex} in the viewport.
     */
    public void scrollToIndex(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= this.getSize()) {
            return;
        }

        this.elementClicked(targetIndex, false, 0, 0);
        this.selectedElement = targetIndex;

        int viewportCenterY = (this.bottom - this.top) / 2;
        int itemCenterPos = (targetIndex * this.slotHeight) + this.headerPadding + (this.slotHeight / 2);
        float targetScroll = (float) (itemCenterPos - viewportCenterY);
        float maxScroll = (float) this.func_148135_f();

        this.amountScrolled = MathHelper.clamp_float(targetScroll, 0.0F, maxScroll);
    }

    public void addPackEntry(int index, String name) {
        final ShaderPackEntry entry = new ShaderPackEntry(this, name);

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
    protected boolean elementClicked(int index, boolean doubleClick, int mouseX, int mouseY, int mouseButton) {
        // Only do anything on left-click
        if (mouseButton != 0) {
            return false;
        }
        final BaseEntry entry = this.entries.get(index);
        if(entry instanceof ShaderPackEntry shaderPackEntry) {
            this.setSelected(shaderPackEntry);
            if (!topButtonRow.shadersEnabled) {
                topButtonRow.setShadersEnabled(true);
            }
            return true;
        } else if( entry instanceof TopButtonRowEntry topButtonRowEntry) {
            return topButtonRowEntry.mouseClicked(mouseX, mouseY, 0);
        } else if (entry instanceof DownloadEntry downloadEntry) {
            return downloadEntry.click(this.screen);
        }
        return false;
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
        // Do nothing
    }


    @Override
    protected void drawSlot(int index, int x, int y, int i1, Tessellator tessellator, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        final boolean isMouseOver = this.func_148124_c/*getSlotIndexFromScreenCoords*/(mouseX, mouseY) == index;
        entry.drawEntry(screen, index, x, y + 4, this.getListWidth(), tessellator, mouseX, mouseY, isMouseOver);
    }

}
