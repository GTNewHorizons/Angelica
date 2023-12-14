package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.shaderpack.option.menu.OptionMenuElement;

import java.util.Optional;

public abstract class CommentedElementWidget<T extends OptionMenuElement> extends AbstractElementWidget<T> {
	public CommentedElementWidget(T element) {
		super(element);
	}

	public abstract Optional<String> getCommentTitle();

	public abstract Optional<String> getCommentBody();
}
