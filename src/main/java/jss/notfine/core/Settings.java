package jss.notfine.core;

import jss.notfine.render.RenderStars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;

public enum Settings {
    CLOUD_HEIGHT(true, 128f, 96f, 384f, 8f) {
        @Override
        public void updateSetting() {
            SettingsManager.cloudsUpdated();
        }
    },
    CLOUD_SCALE(true, 1f, 0.5f, 5f, 0.25f),
    FOG_DEPTH(false,0f, 0f, 1f, 1f, "0:On, 1:Off"),
    GUI_BACKGROUND(false, -1f, -1f, 5f, 1f, "-1:Default, 0:Sand, 1:Mycelium, 2:Stonebrick, 3:Mossy Stonebrick, 4:Oak Planks, 5: Birch Planks") {
        @Override
        public void updateSetting() {
            SettingsManager.backgroundUpdated();
        }
    },
    MODE_CLOUD_TRANSLUCENCY(false, -1f,-1f,1f, 1f, "-1:Default, 0:Always, 1:Never") {
        @Override
        public void updateSetting() {
            SettingsManager.cloudsUpdated();
        }
    },
    MODE_CLOUDS(false,-1f, -1f, 2f, 1f, "-1:Default, 0:Fancy, 1:Fast, 2:Off") {
        @Override
        public void updateSetting() {
            SettingsManager.cloudsUpdated();
        }
    },
    MODE_DROPPED_ITEMS(false, -1f,-1f,1f, 1f, "-1:Default, 0:On, 1:Off") {
        @Override
        public void updateSetting() {
            SettingsManager.droppedItemDetailUpdated();
        }
    },
    MODE_GLINT_INV(false,0f, 0f, 1f, 1f, "0:On, 1:Off"),
    MODE_GLINT_WORLD(false,0f, 0f, 1f, 1f, "0:On, 1:Off"),
    MODE_GUI_BACKGROUND(false, 0f, 0f, 1f, 1f, "0:On, 1:Off"),
    MODE_LEAVES(false,-1f, -1f, 4f,1f,"-1:Default, 0:Fancy, 1:Fast, 2: Smart, 3:Hybrid Fancy, 3:Hybrid Fast") {
        @Override
        public void updateSetting() {
            SettingsManager.leavesUpdated();
        }
    },
    MODE_SHADOWS(false, -1f,-1f,1f, 1f, "-1:Default, 0:On, 1:Off") {
        @Override
        public void updateSetting() {
            SettingsManager.shadowsUpdated();
        }
    },
    MODE_SKY(false,0f, 0f, 1f, 1f, "0:On, 1:Off"),
    MODE_WATER(false, -1f,-1f,1f, 1f, "-1:Default, 0:Fancy, 1:Fast") {
        @Override
        public void updateSetting() {
            SettingsManager.waterDetailUpdated();
        }
    },
    MODE_VIGNETTE(false, -1f,-1f,1f, 1f, "-1:Default, 0:On, 1:Off") {
        @Override
        public void updateSetting() {
            SettingsManager.vignetteUpdated();
        }
    },
    PARTICLES_ENC_TABLE(true,1f, 0f, 16f, 1f),
    PARTICLES_VOID(false,0f, 0f, 1f, 1f, "0:On, 1:Off"),
    RENDER_DISTANCE_CLOUDS(true, 4f, 4f, 64f, 2f) {
        @Override
        public void updateSetting() {
            SettingsManager.cloudsUpdated();
        }
    },
    TOTAL_STARS(true, 1500f, 0f, 32000f, 500f) {
        @Override
        public void updateSetting() {
            RenderStars.reloadStarRenderList(Minecraft.getMinecraft().renderGlobal);
        }
    },
    RENDER_DISTANCE_ENTITIES(true, 100f, 50f, 500f, 25f) {
        @Override
        public void updateSetting() {
            SettingsManager.entityRenderDistanceUpdated();
        }
    };
    public final boolean slider;
    public final float base;
    public final float minimum;
    public final float maximum;
    public final float step;
    public final String configComment;
    private float value;

    Settings(boolean slider, float base, float minimum, float maximum, float step, String configComment) {
        this.slider = slider;
        this.base = base;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.configComment = configComment;
        value = base;
    }

    Settings(boolean slider, float base, float minimum, float maximum, float step) {
        this(slider, base, minimum, maximum, step, "Increments in steps of " + step);
    }

    public void setValue(float value) {
        value = MathHelper.clamp_float(value, minimum, maximum);
        if(step > 0f) {
            value = step * (float)Math.round(value / step);
        }
        if(this.value != value) {
            this.value = value;
            updateSetting();
        }
    }

    public void setValueNormalized(float value) {
        setValue(minimum + (maximum - minimum) * MathHelper.clamp_float(value, 0f, 1f));
    }

    public void incrementValue() {
        value += step;
        if(value > maximum) {
            value = minimum;
        }
        updateSetting();
    }

    public float getValue() {
        return value;
    }

    public float getValueNormalized() {
        return MathHelper.clamp_float((value - minimum) / (maximum - minimum), 0f, 1f);
    }

    public boolean isValueBase() {
        return value == base;
    }

    public String getLocalization() {
        String localized = I18n.format("options." + name().toLowerCase()) + ": ";
        if(slider) {
            if(step % 1f == 0f) {
                localized += (int)value;
            } else {
                localized += value;
            }
        } else if(step == 1f && minimum == 0f && maximum == 1f) {
            if(value == 0f) {
                localized += I18n.format("options.on");
            } else {
                localized += I18n.format("options.off");
            }
        } else {
            localized += I18n.format("options." + name().toLowerCase() + '.' + (int)value);
        }
        return localized;
    }

    public void updateSetting() {

    }

}
