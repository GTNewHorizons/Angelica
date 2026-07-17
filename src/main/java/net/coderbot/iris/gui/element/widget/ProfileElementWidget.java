package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.OptionSet;
import net.coderbot.iris.shaderpack.option.Profile;
import net.coderbot.iris.shaderpack.option.ProfileSet;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.coderbot.iris.shaderpack.option.menu.ProfileElementTracker;
import net.coderbot.iris.shaderpack.option.values.OptionValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import java.util.Optional;

public class ProfileElementWidget extends BaseOptionElementWidget<OptionMenuProfileElement> {

	private static String profileLabel()  { return I18n.format("options.iris.profile"); }
	private static String profileCustom() { return I18n.format("options.iris.profile.custom"); }

	private Profile next;
	private Profile previous;
	private String profileLabel;

	public ProfileElementWidget(OptionMenuProfileElement element) {
		super(element);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		super.init(screen, navigation);
		this.setLabel(profileLabel());
        boolean secondProfileSet = ProfileElementTracker.isSecondProfileSet(this.element);

		final ProfileSet profiles = this.element.profiles;
        final OptionSet options = this.element.options;
        final OptionValues pendingValues = this.element.getPendingOptionValues();

        final ProfileSet.ProfileResult result = profiles.scan(options, pendingValues);

		this.next = result.next;
		this.previous = result.previous;
        final Optional<String> profileName = result.current.map(p -> p.name);

        String translationKey = "profile" + (secondProfileSet ? "2." : ".") + profileName.orElse("custom");
        this.profileLabel = profileName.map(name -> GuiUtil.translateOrDefault(name, translationKey)).orElse(profileCustom());
	}

	@Override
	public void drawScreen(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		this.updateRenderParams(width, width - (Minecraft.getMinecraft().fontRenderer.getStringWidth(profileLabel()) + 16));

		this.renderOptionWithValue(x, y, width, height, hovered);
	}

	@Override
	protected String createValueLabel() {
		return this.profileLabel;
	}

	@Override
	protected int getValueColor() {
		return profileCustom().equals(this.profileLabel) ? 0xFFFF55 : 0xFFFFFF;
	}

	@Override
	public Optional<String> getCommentTitle() {
		return Optional.of(profileLabel());
	}

	@Override
	public String getCommentKey() {
        return ProfileElementTracker.isSecondProfileSet(this.element) ? "profile2.comment" : "profile.comment";
	}

	@Override
	public boolean applyNextValue() {
		if (this.next == null) {
			return false;
		}

		Iris.queueShaderPackOptionsFromProfile(this.next);

		return true;
	}

	@Override
	public boolean applyPreviousValue() {
		if (this.previous == null) {
			return false;
		}

		Iris.queueShaderPackOptionsFromProfile(this.previous);

		return true;
	}

	@Override
	public boolean applyOriginalValue() {
		return false; // Resetting options is the way to return to the "default profile"
	}

	@Override
	public boolean isValueModified() {
		return false;
	}
}
