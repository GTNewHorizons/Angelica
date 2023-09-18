package jss.notfine.gui;

import jss.notfine.config.NotFineConfig;
import jss.notfine.core.Settings;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

import java.util.ArrayList;

public enum MenuButtonLists {
    //list.add(); list.add();
    VIDEO("options.video") {
        @Override
        public Object[] entries() {
            ArrayList<Object> list = new ArrayList<>();

            list.add(GameSettings.Options.GRAPHICS); list.add(GameSettings.Options.RENDER_DISTANCE);
            list.add(GameSettings.Options.ENABLE_VSYNC); list.add(GameSettings.Options.USE_FULLSCREEN);
            list.add(GameSettings.Options.FRAMERATE_LIMIT); list.add(null);
            list.add(GameSettings.Options.GUI_SCALE); list.add(GameSettings.Options.VIEW_BOBBING);
            list.add(GameSettings.Options.AMBIENT_OCCLUSION); list.add(GameSettings.Options.GAMMA);
            list.add(GameSettings.Options.ANISOTROPIC_FILTERING); list.add(GameSettings.Options.MIPMAP_LEVELS);

            list.add(DETAIL); list.add(SKY);
            list.add(PARTICLE); list.add(OTHER);

            return list.toArray();
        }
    },
    DETAIL("options.button.detail") {
        @Override
        public Object[] entries() {
            ArrayList<Object> list = new ArrayList<>();

            list.add(Settings.MODE_LEAVES); list.add(Settings.MODE_WATER);
            list.add(Settings.MODE_SHADOWS); list.add(Settings.MODE_VIGNETTE);
            list.add(Settings.MODE_DROPPED_ITEMS); list.add(Settings.MODE_GLINT_WORLD);
            list.add(Settings.MODE_GLINT_INV);

            return list.toArray();
        }
    },
    SKY("options.button.sky") {
        @Override
        public Object[] entries() {
            ArrayList<Object> list = new ArrayList<>();

            list.add(Settings.MODE_SKY); list.add(Settings.MODE_CLOUDS);
            list.add(Settings.RENDER_DISTANCE_CLOUDS); list.add(Settings.CLOUD_HEIGHT);
            list.add(Settings.CLOUD_SCALE); list.add(Settings.MODE_CLOUD_TRANSLUCENCY);
            list.add(Settings.TOTAL_STARS);

            return list.toArray();
        }
    },
    PARTICLE("options.button.particle") {
        @Override
        public Object[] entries() {
            ArrayList<Object> list = new ArrayList<>();

            list.add(GameSettings.Options.PARTICLES); list.add(Settings.PARTICLES_VOID);
            list.add(Settings.PARTICLES_ENC_TABLE);

            return list.toArray();
        }
    },
    OTHER("options.button.other") {
        @Override
        public Object[] entries() {
            ArrayList<Object> list = new ArrayList<>();
            if(OpenGlHelper.field_153197_d && NotFineConfig.allowAdvancedOpenGL) {
                list.add(GameSettings.Options.ADVANCED_OPENGL);
            } else {
                list.add(null);
            }
            list.add(GameSettings.Options.ANAGLYPH);
            list.add(Settings.MODE_GUI_BACKGROUND); list.add(GameSettings.Options.SHOW_CAPE);
            list.add(null); list.add(GameSettings.Options.FBO_ENABLE);
            list.add(Settings.GUI_BACKGROUND);

            return list.toArray();
        }
    };

    private final String unlocalizedButton;

    MenuButtonLists(String button) {
        unlocalizedButton = button;
    }

    public final String getButtonLabel() {
        return I18n.format(unlocalizedButton);
    }

    public final String getTitleLabel() {
        return I18n.format("options.title." + name().toLowerCase());
    }

    public abstract Object[] entries();

}
