package net.coderbot.iris.gui.element;

import lombok.Getter;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.shaderoptions.BaseEntry;
import net.coderbot.iris.gui.element.shaderoptions.ElementRowEntry;
import net.coderbot.iris.gui.element.shaderoptions.HeaderEntry;
import net.coderbot.iris.gui.element.widget.AbstractElementWidget;
import net.coderbot.iris.gui.element.widget.OptionMenuConstructor;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import java.util.ArrayList;
import java.util.List;

public class ShaderPackOptionList extends IrisGuiSlot {
	private final List<AbstractElementWidget<?>> elementWidgets = new ArrayList<>();
	private final ShaderPackScreen screen;
	@Getter
    private final NavigationController navigation;
	private OptionMenuContainer container;
    private final List<BaseEntry> entries = new ArrayList<>();

	public ShaderPackOptionList(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
		super(client, width, height, top, bottom, 24);
		this.navigation = navigation;
		this.screen = screen;

		applyShaderPack(pack);
	}

	public void applyShaderPack(ShaderPack pack) {
		this.container = pack.getMenuContainer();
	}

	public void rebuild() {
        this.entries.clear();
//		this.clearEntries();
//		this.setScrollAmount(0);
		OptionMenuConstructor.constructAndApplyToScreen(this.container, this.screen, this, navigation);
	}

	public void refresh() {
		this.elementWidgets.forEach(widget -> widget.init(this.screen, this.navigation));
	}

	@Override
    public int getListWidth() {
		return Math.min(400, width - 12);
	}

    protected void addEntry(BaseEntry entry) {
        this.entries.add(entry);
    }

	public void addHeader(String text, boolean backButton) {
		this.addEntry(new HeaderEntry(this.screen, this.navigation, text, backButton));
	}

	public void addWidgets(int columns, List<AbstractElementWidget<?>> elements) {
		this.elementWidgets.addAll(elements);

		List<AbstractElementWidget<?>> row = new ArrayList<>();
		for (AbstractElementWidget<?> element : elements) {
			row.add(element);

			if (row.size() >= columns) {
				this.addEntry(new ElementRowEntry(screen, this.navigation, row));
				row = new ArrayList<>(); // Clearing the list would affect the row entry created above
			}
		}

		if (row.size() > 0) {
			while (row.size() < columns) {
				row.add(AbstractElementWidget.EMPTY);
			}

			this.addEntry(new ElementRowEntry(screen, this.navigation, row));
		}
	}

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        entry.mouseClicked(mouseX, mouseY, 0);

    }

    @Override
    protected boolean isSelected(int idx) {
        return false;//return this.entries.get(idx).equals(this.selected);
    }

    @Override
    protected void drawBackground() {

    }

    @Override
    protected void drawSlot(int index, int x, int y, int i1, Tessellator tessellator, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        final boolean isMouseOver = this.func_148124_c/*getSlotIndexFromScreenCoords*/(mouseX, mouseY) == index;
        entry.drawEntry(screen, index, x - 2, y + 4, this.getListWidth(), this.slotHeight, tessellator, mouseX, mouseY, isMouseOver);
    }

//


}
