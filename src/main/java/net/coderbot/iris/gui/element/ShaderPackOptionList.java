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
import net.minecraft.client.gui.GuiTextField;
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

	@Getter
    private boolean searchModeActive = false;
	private String typedSearchQuery = "";
	private int reservedLeftWidth = 48; // Pixels reserved on the left of the header row for the search/clear button + gap.
	private GuiTextField searchBox = null;

	public ShaderPackOptionList(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
		super(client, width, height, top, bottom, 24);
		this.navigation = navigation;
		this.screen = screen;

		applyShaderPack(pack);
	}

	public void applyShaderPack(ShaderPack pack) {
		this.container = pack.getMenuContainer();
	}


    /** Applies {@code query} to the option container and rebuilds the list rows. */
	public void updateSearchQuery(String query) {
		typedSearchQuery = query != null ? query : "";
		if (this.container != null) {
			this.container.setSearchQuery(query);
		}
		this.rebuild();
	}

	/** Resets all search state and clears the active filter without rebuilding entries. */
	public void disableSearchMode() {
		searchModeActive = false;
		typedSearchQuery = "";
		searchBox = null;
		if (this.container != null) {
			this.container.setSearchQuery(null);
		}
	}

	public void disableSearchModeAndRebuild() {
		disableSearchMode();
		this.rebuild();
	}

	public void enableSearchModeAndRebuild() {
		searchModeActive = true;
		this.rebuild(); // First rebuild so that the button width is the current one
		int listWidth = getListWidth();
		int entryX = this.width / 2 - listWidth / 2;
		int boxX = entryX + reservedLeftWidth + 3;
		int boxY = this.top + 5; // got figured out by trial and error and works, don't question
		int boxW = Math.max(40, listWidth - reservedLeftWidth - 5);
		this.searchBox = new GuiTextField(Minecraft.getMinecraft().fontRenderer, boxX, boxY, boxW, 14);
		this.searchBox.setMaxStringLength(64);
		this.searchBox.setFocused(true);
	}

	/** True when the navigation stack is non-empty (a sub-screen is open). */
	public boolean isOnSubScreen() {
		return navigation != null && navigation.getCurrentScreen() != null;
	}

    public void setReservedLeftWidth(int w)  {
        reservedLeftWidth = Math.max(0, w);
    }

	/**
	 * Routes a keystroke into the live search text field, then propagates any text change
	 * to the option container and rebuilds the filtered list.
	 */
	public void handleSearchKeyTyped(char typedChar, int keyCode) {
		if (searchBox == null || !searchModeActive) return;
		searchBox.textboxKeyTyped(typedChar, keyCode);
		String newText = searchBox.getText();
		if (!newText.equals(typedSearchQuery)) {
			updateSearchQuery(newText);
		}
	}

	/** Draws the search text box overlay above the header row */
	public void drawSearchBox() {
		if (searchBox == null || !searchModeActive) return;
		// Hide when the header has scrolled out of view
		if (this.amountScrolled > 0.5f) return;
		searchBox.drawTextBox();
	}

	public void rebuild() {
        this.entries.clear();
        this.amountScrolled = 0;
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
		this.addEntry(new HeaderEntry(this.screen, this.navigation, text, backButton, this));
	}

	public void addWidgets(int columns, List<AbstractElementWidget<?>> elements) {
		this.elementWidgets.addAll(elements);

		List<AbstractElementWidget<?>> row = new ArrayList<>();
		for (AbstractElementWidget<?> element : elements) {
			row.add(element);

			if (row.size() >= columns) {
				this.addEntry(new ElementRowEntry(this.navigation, row));
				row = new ArrayList<>(); // Clearing the list would affect the row entry created above
			}
		}

		if (!row.isEmpty()) {
			while (row.size() < columns) {
				row.add(AbstractElementWidget.EMPTY);
			}

			this.addEntry(new ElementRowEntry(this.navigation, row));
		}
	}

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Pass the click to the search box first so it can update its focus state
        if (searchModeActive && searchBox != null && amountScrolled <= 0.5f) {
            searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected boolean elementClicked(int index, boolean doubleClick, int mouseX, int mouseY, int mouseButton) {
        final BaseEntry entry = this.entries.get(index);
        return entry.mouseClicked(mouseX, mouseY, mouseButton);
    }
    @Override
    public boolean mouseReleased( int mouseX, int mouseY, int button) {
        this.scrolling = false;
        if (mouseY <= this.top || mouseY >= this.bottom) {
            return false;
        }

        final int relativeY = mouseY - this.top + (int) this.amountScrolled - hitYOffset();
        final int index = relativeY / this.slotHeight;

        if (index < 0 || index >= this.entries.size())
            return false;

        final BaseEntry entry = this.entries.get(index);
        return entry.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean isSelected(int idx) {
        return false;//return this.entries.get(idx).equals(this.selected);
    }

    @Override
    protected void drawBackground() {
        // noop
    }

    @Override
    protected int hitYOffset() {
        return 3;
    }

    @Override
    protected int hitRightInset() {
        return 4;
    }

    @Override
    protected int hitLeftInset() {
        return -1;
    }

    @Override
    protected int hitXShift() {
        return 2;
    }

    @Override
    protected void drawSlot(int index, int x, int y, int i1, Tessellator tessellator, int mouseX, int mouseY) {
        final BaseEntry entry = this.entries.get(index);
        final boolean isMouseOver = this.func_148124_c/*getSlotIndexFromScreenCoords*/(mouseX, mouseY) == index;
        entry.drawEntry(screen, index, x, y - 2, this.getListWidth(), this.slotHeight, tessellator, mouseX, mouseY, isMouseOver);
    }



}
