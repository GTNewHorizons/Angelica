package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.BooleanOption;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.minecraft.client.resources.I18n;

public class BooleanElementWidget extends BaseOptionElementWidget<OptionMenuBooleanOptionElement> {
	private static final String TEXT_TRUE = I18n.format("label.iris.true");
	private static final String TEXT_FALSE = I18n.format("label.iris.false");
	private static final String TEXT_TRUE_DEFAULT = I18n.format("label.iris.true");
	private static final String TEXT_FALSE_DEFAULT = I18n.format("label.iris.false");

	private final BooleanOption option;

	private boolean appliedValue;
	private boolean value;
	private boolean defaultValue;

	public BooleanElementWidget(OptionMenuBooleanOptionElement element) {
		super(element);

		this.option = element.option;
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		super.init(screen, navigation);

		// The value currently in use by the shader pack
		this.appliedValue = this.element.getAppliedOptionValues().getBooleanValueOrDefault(this.option.getName());

		// The yet-to-be-applied value that has been queued (if that is the case)
		// Might be equal to the applied value
		this.value = this.element.getPendingOptionValues().getBooleanValueOrDefault(this.option.getName());

		this.defaultValue = this.element.getAppliedOptionValues().getOptionSet().getBooleanOptions()
			.get(this.option.getName()).getOption().getDefaultValue();

		this.setLabel(GuiUtil.translateOrDefault(this.option.getName(), "option." + this.option.getName()));
	}

	@Override
	public void drawScreen(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		this.updateRenderParams(width, 28);

		this.renderOptionWithValue(x, y, width, height, hovered);
		this.tryRenderTooltip(mouseX, mouseY, hovered);
	}

	@Override
	protected String createValueLabel() {
		// UX: Do not use color if the value is set to default.
		//
		// This is because the red color for "Off" and green color of "On"
		// was causing people to want to change options to On when that was
		// unnecessary due to red having a bad association.
		//
		// This was changed on request of Emin, since people kept on changing
		// Compatibility Mode to "On" when not needed. Now we use white for
		// default to avoid giving a positive or negative connotation to a
		// default value.
		if (this.value == this.defaultValue) {
			return this.value ? TEXT_TRUE_DEFAULT : TEXT_FALSE_DEFAULT;
		}

		return this.value ? TEXT_TRUE : TEXT_FALSE;
	}

	@Override
	public String getCommentKey() {
		return "option." + this.option.getName() + ".comment";
	}

	public String getValue() {
		return Boolean.toString(this.value);
	}

	private void queue() {
		Iris.getShaderPackOptionQueue().put(this.option.getName(), this.getValue());
	}

	@Override
	public boolean applyNextValue() {
		this.value = !this.value;
		this.queue();

		return true;
	}

	@Override
	public boolean applyPreviousValue() {
		return this.applyNextValue();
	}

	@Override
	public boolean applyOriginalValue() {
		this.value = this.option.getDefaultValue();
		this.queue();

		return true;
	}

	@Override
	public boolean isValueModified() {
		return this.value != this.appliedValue;
	}
}
