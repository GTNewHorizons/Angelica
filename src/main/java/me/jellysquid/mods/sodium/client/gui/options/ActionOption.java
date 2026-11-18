package me.jellysquid.mods.sodium.client.gui.options;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ActionOption extends ButtonOption {

    private final Supplier<String> label;
    private final Runnable action;
    private final BooleanSupplier available;
    private final boolean clickable;

    public ActionOption(String name, String tooltip, Supplier<String> label, Runnable action, BooleanSupplier available) {
        this(name, tooltip, label, action, available, true);
    }

    private ActionOption(String name, String tooltip, Supplier<String> label, Runnable action, BooleanSupplier available, boolean clickable) {
        super(name, tooltip, 100);
        this.label = label;
        this.action = action;
        this.available = available;
        this.clickable = clickable;
    }

    public static ActionOption label(String name, String tooltip, Supplier<String> label) {
        return new ActionOption(name, tooltip, label, () -> {}, () -> true, false);
    }

    @Override
    protected boolean clickable() {
        return this.clickable;
    }

    @Override
    protected String label() {
        return this.label.get();
    }

    @Override
    protected void invoke() {
        this.action.run();
    }

    @Override
    public boolean isAvailable() {
        return this.available.getAsBoolean();
    }
}
