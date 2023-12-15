package net.coderbot.iris.gui.element;

import lombok.Getter;
import net.coderbot.iris.gui.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Intended to make very simple rows of buttons easier to make
 */
public class IrisElementRow {
	private final Map<Element, Integer> elements = new HashMap<>();
	private final List<Element> orderedElements = new ArrayList<>();
	private final int spacing;
	private int x;
	private int y;
	private int width;
	private int height;

	public IrisElementRow(int spacing) {
		this.spacing = spacing;
	}

	public IrisElementRow() {
		this(1);
	}

	/**
	 * Adds an element to the right of this row.
	 *
	 * @param element The element to add
	 * @param width The width of the element in this row
	 * @return {@code this}, to be used for chaining statements
	 */
	public IrisElementRow add(Element element, int width) {
		if (!this.orderedElements.contains(element)) {
			this.orderedElements.add(element);
		}
		this.elements.put(element, width);

		this.width += width + this.spacing;

		return this;
	}

	/**
	 * Modifies the width of an element.
	 *
	 * @param element The element whose width to modify
	 * @param width The width to be assigned to the specified element
	 */
	public void setWidth(Element element, int width) {
		if (!this.elements.containsKey(element)) {
			return;
		}

		this.width -= this.elements.get(element) + 2;

		add(element, width);
	}

	/**
	 * Renders the row, with the anchor point being the top left.
	 */
	public void render(int x, int y, int height, int mouseX, int mouseY, float tickDelta, boolean rowHovered) {
		this.x = x;
		this.y = y;
		this.height = height;

		int currentX = x;

		for (Element element : this.orderedElements) {
			final int currentWidth = this.elements.get(element);

			element.render(currentX, y, currentWidth, height, mouseX, mouseY, tickDelta,
					rowHovered && sectionHovered(currentX, currentWidth, mouseX, mouseY));

			currentX += currentWidth + this.spacing;
		}
	}

	/**
	 * Renders the row, with the anchor point being the top right.
	 */
	public void renderRightAligned(int x, int y, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		render(x - this.width, y, height, mouseX, mouseY, tickDelta, hovered);
	}

	private boolean sectionHovered(int sectionX, int sectionWidth, double mx, double my) {
		return mx > sectionX && mx < sectionX + sectionWidth &&
				my > this.y && my < this.y + this.height;
	}

	private Optional<Element> getHovered(double mx, double my) {
		int currentX = this.x;

		for (Element element : this.orderedElements) {
			final int currentWidth = this.elements.get(element);

			if (sectionHovered(currentX, currentWidth, mx, my)) {
				return Optional.of(element);
			}

			currentX += currentWidth + this.spacing;
		}

		return Optional.empty();
	}

	public boolean mouseClicked(double mx, double my, int button) {
		return getHovered(mx, my).map(element -> element.mouseClicked(mx, my, button)).orElse(false);
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return getHovered(mx, my).map(element -> element.mouseReleased(mx, my, button)).orElse(false);
	}

    public abstract static class Element {
		public boolean disabled = false;
		@Getter private boolean hovered = false;

		public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
			GuiUtil.bindIrisWidgetsTexture();
			GuiUtil.drawButton(x, y, width, height, hovered, this.disabled);

			this.hovered = hovered;
			this.renderLabel(x, y, width, height, mouseX, mouseY, tickDelta, hovered);
		}

		public abstract void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered);

		public boolean mouseClicked(double mx, double my, int button) {
			return false;
		}

		public boolean mouseReleased(double mx, double my, int button) {
			return false;
		}

    }

    public abstract static class ButtonElement<T extends ButtonElement<T>> extends Element {
		private final Function<T, Boolean> onClick;

		protected ButtonElement(Function<T, Boolean> onClick) {
			this.onClick = onClick;
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (this.disabled) {
				return false;
			}

			if (button == 0) {
				return this.onClick.apply((T) this);
			}

			return super.mouseClicked(mx, my, button);
		}
	}

	/**
	 * A clickable button element that uses a {@link net.coderbot.iris.gui.GuiUtil.Icon} as its label.
	 */
	public static class IconButtonElement extends ButtonElement<IconButtonElement> {
		public GuiUtil.Icon icon;
		public GuiUtil.Icon hoveredIcon;

		public IconButtonElement(GuiUtil.Icon icon, GuiUtil.Icon hoveredIcon, Function<IconButtonElement, Boolean> onClick) {
			super(onClick);
			this.icon = icon;
			this.hoveredIcon = hoveredIcon;
		}

		public IconButtonElement(GuiUtil.Icon icon, Function<IconButtonElement, Boolean> onClick) {
			this(icon, icon, onClick);
		}

		@Override
		public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
			final int iconX = x + (int)((width - this.icon.getWidth()) * 0.5);
			final int iconY = y + (int)((height - this.icon.getHeight()) * 0.5);

			GuiUtil.bindIrisWidgetsTexture();
			if (!this.disabled && hovered) {
				this.hoveredIcon.draw(iconX, iconY);
			} else {
				this.icon.draw(iconX, iconY);
			}
		}
	}

	/**
	 * A clickable button element that uses a text component as its label.
	 */
	public static class TextButtonElement extends ButtonElement<TextButtonElement> {
		protected final FontRenderer font;
		public String text;

		public TextButtonElement(String text, Function<TextButtonElement, Boolean> onClick) {
			super(onClick);

			this.font = Minecraft.getMinecraft().fontRenderer;
			this.text = text;
		}

		@Override
		public void renderLabel(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
			final int textX = x + (int)((width - this.font.getStringWidth(this.text)) * 0.5);
			final int textY = y + (int)((height - 8) * 0.5);

			this.font.drawStringWithShadow(this.text, textX, textY, 0xFFFFFF);
		}
	}
}
