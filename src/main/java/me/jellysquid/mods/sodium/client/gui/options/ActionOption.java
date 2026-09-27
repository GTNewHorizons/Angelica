package me.jellysquid.mods.sodium.client.gui.options;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ActionOption extends ButtonOption {

    private final Supplier<String> label;
    private final Runnable action;
    private final BooleanSupplier available;

    public ActionOption(String name, String tooltip, Supplier<String> label, Runnable action, BooleanSupplier available) {
        super(name, tooltip, 100);
        this.label = label;
        this.action = action;
        this.available = available;
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
