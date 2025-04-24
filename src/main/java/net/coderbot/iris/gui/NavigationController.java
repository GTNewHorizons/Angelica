package net.coderbot.iris.gui;

import lombok.Getter;
import net.coderbot.iris.gui.element.ShaderPackOptionList;

import java.util.ArrayDeque;
import java.util.Deque;

public class NavigationController {

	private ShaderPackOptionList optionList;

	@Getter
    private String currentScreen = null;
	private final Deque<String> history = new ArrayDeque<>();

	public void back() {
		if (history.size() > 0) {
			history.removeLast();

			if (history.size() > 0) {
				currentScreen = history.getLast();
			} else {
				currentScreen = null;
			}
		} else {
			currentScreen = null;
		}

		this.rebuild();
	}

	public void open(String screen) {
		currentScreen = screen;
		history.addLast(screen);

		this.rebuild();
	}

	public void rebuild() {
		if (optionList != null) {
			optionList.rebuild();
		}
	}

	public void refresh() {
		if (optionList != null) {
			optionList.refresh();
		}
	}

	public boolean hasHistory() {
		return this.history.size() > 0;
	}

	public void setActiveOptionList(ShaderPackOptionList optionList) {
		this.optionList = optionList;
	}

}
