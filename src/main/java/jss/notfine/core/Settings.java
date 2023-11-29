package jss.notfine.core;

import jss.notfine.gui.options.control.NotFineControlValueFormatter;
import jss.notfine.gui.options.named.AlwaysNever;
import jss.notfine.gui.options.named.BackgroundSelect;
import jss.notfine.gui.options.named.DownfallQuality;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.gui.options.named.GraphicsToggle;
import jss.notfine.render.RenderStars;
import jss.notfine.gui.options.storage.NotFineMinecraftOptionsStorage;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.named.GraphicsQuality;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public enum Settings {
    CLOUD_HEIGHT(new NotFineOptionSlider(128, 96, 384, 8,  null)) {
        @Override
        public void applyChanges() {
            SettingsManager.cloudsUpdated();
        }
    },
    CLOUD_SCALE(new NotFineOptionSlider(4, 2, 20, 1, null)),
    DOWNFALL_DISTANCE(new NotFineOptionCycling<>(DownfallQuality.DEFAULT, OptionImpact.MEDIUM)) {
        @Override
        public void applyChanges() {
            SettingsManager.downfallDistanceUpdated();
        }
    },
    GUI_BACKGROUND(new NotFineOptionCycling<>(BackgroundSelect.DEFAULT, null)) {
        @Override
        public void applyChanges() {
            SettingsManager.backgroundUpdated();
        }
    },
    MODE_CLOUD_TRANSLUCENCY(new NotFineOptionCycling<>(AlwaysNever.DEFAULT, null)) {
        @Override
        public void applyChanges() {
            SettingsManager.cloudsUpdated();
        }
    },
    MODE_CLOUDS(new NotFineOptionCycling<>(GraphicsQualityOff.DEFAULT, OptionImpact.MEDIUM)) {
        @Override
        public void applyChanges() {
            SettingsManager.cloudsUpdated();
        }
    },
    MODE_DROPPED_ITEMS(new NotFineOptionCycling<>(GraphicsQuality.DEFAULT, OptionImpact.LOW)) {
        @Override
        public void applyChanges() {
            SettingsManager.droppedItemDetailUpdated();
        }
    },
    MODE_GLINT_INV(new NotFineOptionTickBox(true, OptionImpact.VARIES)),
    MODE_GLINT_WORLD(new NotFineOptionTickBox(true, OptionImpact.VARIES)),
    MODE_GUI_BACKGROUND(new NotFineOptionTickBox(true, null)),
    MODE_LEAVES(new NotFineOptionCycling<>(LeavesQuality.DEFAULT, OptionImpact.VARIES, OptionFlag.REQUIRES_RENDERER_RELOAD)) {
        @Override
        public void applyChanges() {
            SettingsManager.leavesUpdated();
        }
    },
    MODE_SHADOWS(new NotFineOptionCycling<>(GraphicsToggle.DEFAULT, OptionImpact.LOW)) {
        @Override
        public void applyChanges() {
            SettingsManager.shadowsUpdated();
        }
    },
    MODE_SKY(new NotFineOptionTickBox(true, OptionImpact.MEDIUM)),
    MODE_WATER(new NotFineOptionCycling<>(GraphicsQuality.DEFAULT, OptionImpact.LOW)) {
        @Override
        public void applyChanges() {
            SettingsManager.waterDetailUpdated();
        }
    },
    MODE_VIGNETTE(new NotFineOptionCycling<>(GraphicsToggle.DEFAULT, OptionImpact.LOW)) {
        @Override
        public void applyChanges() {
            SettingsManager.vignetteUpdated();
        }
    },
    PARTICLES_ENC_TABLE(new NotFineOptionSlider(1, 0, 16, 1, OptionImpact.LOW)),
    PARTICLES_VOID(new NotFineOptionTickBox(true, OptionImpact.LOW)),
    RENDER_DISTANCE_CLOUDS(new NotFineOptionSlider(4, 4, 64, 2, OptionImpact.VARIES)) {
        @Override
        public void applyChanges() {
            SettingsManager.cloudsUpdated();
        }
    },
    TOTAL_STARS(new NotFineOptionSlider(1500, 0, 32000, 500, OptionImpact.LOW)) {
        @Override
        public void applyChanges() {
            RenderStars.reloadStarRenderList(Minecraft.getMinecraft().renderGlobal);
        }
    };

    public final NotFineOption option;

    Settings(NotFineOption option) {
        this.option = option;
    }

    public void ready() {
        option.setting = this;
    }

    public void applyChanges() {

    }

    public static class NotFineOptionCycling<T extends Enum<T>> extends NotFineOption<T> {

        protected NotFineOptionCycling(T base, OptionImpact impact, OptionFlag... optionFlags) {
            super(base, impact, optionFlags);
        }

        @Override
        public void deserialize(String fragment) {
            store = T.valueOf(value.getDeclaringClass(), fragment);
            value = store;
            modifiedValue = store;
        }

        @Override
        public Control<T> getControl() {
            return new CyclingControl<>(this, value.getDeclaringClass());
        }

    }

    public static class NotFineOptionSlider extends NotFineOption<Integer> {
        public final int min, max, step;

        protected NotFineOptionSlider(int base, int min, int max, int step,  OptionImpact impact, OptionFlag... optionFlags) {
            super(base, impact, optionFlags);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public Control<Integer> getControl() {
            //TODO: Don't hardcode CLOUD_SCALE check.
            return new SliderControl(this, min, max, step, setting != Settings.CLOUD_SCALE ? ControlValueFormatter.number() : NotFineControlValueFormatter.multiplied(0.25f));
        }

        @Override
        public void deserialize(String fragment) {
            int deserialized = Integer.parseInt(fragment);
            deserialized = MathHelper.clamp_int(deserialized, min, max);
            if(step > 1) {
                deserialized = step * Math.round((float)deserialized / (float)step);
            }
            store = deserialized;
            value = store;
            modifiedValue = store;
        }
    }

    public static class NotFineOptionTickBox extends NotFineOption<Boolean> {

        protected NotFineOptionTickBox(boolean base, OptionImpact impact, OptionFlag... optionFlags) {
            super(base, impact, optionFlags);
        }

        @Override
        public Control<Boolean> getControl() {
            return new TickBoxControl(this);
        }

        @Override
        public void deserialize(String fragment) {
            store = Boolean.parseBoolean(fragment);
            value = store;
            modifiedValue = store;
        }

    }


    public static abstract class NotFineOption<T> implements Option<T> {
        private static final NotFineMinecraftOptionsStorage optionStorage = new NotFineMinecraftOptionsStorage();
        private final OptionImpact impact;
        private final EnumSet<OptionFlag> optionFlags = EnumSet.noneOf(OptionFlag.class);
        protected final T base;

        protected T value, modifiedValue, store;
        protected Settings setting;

        protected NotFineOption(T base, OptionImpact impact, OptionFlag... optionFlags) {
            value = base;
            modifiedValue = base;
            store = base;
            this.base = base;
            this.impact = impact;
            Collections.addAll(this.optionFlags, optionFlags);
        }

        public abstract void deserialize(String fragment);

        public T getStore() {
            return store;
        }

        @Override
        public String getName() {
            return I18n.format("options." + setting.name().toLowerCase());
        }

        @Override
        public String getTooltip() {
            return I18n.format("options." + setting.name().toLowerCase() + ".tooltip");
        }

        @Override
        public OptionImpact getImpact() {
            return impact;
        }

        @Override
        public T getValue() {
            return modifiedValue;
        }

        @Override
        public void setValue(T value) {
            modifiedValue = value;
        }

        @Override
        public void reset() {
            value = store;
            modifiedValue = store;
        }

        @Override
        public OptionStorage<?> getStorage() {
            return optionStorage;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean hasChanged() {
            return !this.value.equals(this.modifiedValue);
        }

        @Override
        public void applyChanges() {
            store = modifiedValue;
            value = modifiedValue;
            setting.applyChanges();
        }

        @Override
        public Collection<OptionFlag> getFlags() {
            return optionFlags;
        }

    }

}


