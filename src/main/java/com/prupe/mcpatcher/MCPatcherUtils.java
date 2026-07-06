package com.prupe.mcpatcher;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Collection of static methods available to mods at runtime.
 */
public class MCPatcherUtils {

    private static File minecraftDir;
    private static File gameDir;

    public static final String EXTENDED_HD = "Extended HD";
    public static final String HD_FONT = "HD Font";
    public static final String RANDOM_MOBS = "Random Mobs";
    public static final String CUSTOM_COLORS = "Custom Colors";
    public static final String CONNECTED_TEXTURES = "Connected Textures";
    public static final String BETTER_SKIES = "Better Skies";
    public static final String BETTER_GLASS = "Better Glass";
    public static final String CUSTOM_ITEM_TEXTURES = "Custom Item Textures";
    public static final String CUSTOM_ANIMATIONS = "Custom Animations";
    public static final String MIPMAP = "Mipmap";

    public static final String RENDER_PASS_CLASS = "com.prupe.mcpatcher.renderpass.RenderPass";

    public static final String BLANK_PNG_FORMAT = "blank_%08x.png";

    private static final Logger log = LogManager.getLogger();

    private MCPatcherUtils() {}

    /**
     * Get the path to a file/directory within the minecraft folder.
     *
     * @param subdirs zero or more path components
     * @return combined path
     */
    public static File getMinecraftPath(String... subdirs) {
        File f = minecraftDir;
        for (String s : subdirs) {
            f = new File(f, s);
        }
        return f;
    }

    /**
     * Get the path to a file/directory within the game folder. The game folder is usually the same as the minecraft
     * folder, but can be overridden via a launcher profile setting.
     *
     * @param subdirs zero or more path components
     * @return combined path
     */
    public static File getGamePath(String... subdirs) {
        File f = gameDir;
        for (String s : subdirs) {
            f = new File(f, s);
        }
        return f;
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static String getStringProperty(Properties properties, String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        } else {
            return properties.getProperty(key, defaultValue)
                .trim();
        }
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static int getIntProperty(Properties properties, String key, int defaultValue) {
        if (properties != null) {
            String value = properties.getProperty(key, "")
                .trim();
            if (!value.isEmpty()) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {}
            }
        }
        return defaultValue;
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static int getHexProperty(Properties properties, String key, int defaultValue) {
        if (properties != null) {
            String value = properties.getProperty(key, "")
                .trim();
            if (!value.isEmpty()) {
                try {
                    return Integer.parseInt(value, 16);
                } catch (NumberFormatException e) {}
            }
        }
        return defaultValue;
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static boolean getBooleanProperty(Properties properties, String key, boolean defaultValue) {
        if (properties != null) {
            String value = properties.getProperty(key, "")
                .trim()
                .toLowerCase();
            if (!value.isEmpty()) {
                return Boolean.parseBoolean(value);
            }
        }
        return defaultValue;
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static float getFloatProperty(Properties properties, String key, float defaultValue) {
        if (properties != null) {
            String value = properties.getProperty(key, "")
                .trim();
            if (!value.isEmpty()) {
                try {
                    return Float.parseFloat(value);
                } catch (NumberFormatException e) {}
            }
        }
        return defaultValue;
    }

    /**
     * Get a value from a properties file.
     *
     * @param properties   properties file
     * @param key          property name
     * @param defaultValue default value if not found in properties file
     * @return property value
     */
    public static double getDoubleProperty(Properties properties, String key, double defaultValue) {
        if (properties != null) {
            String value = properties.getProperty(key, "")
                .trim();
            if (!value.isEmpty()) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {}
            }
        }
        return defaultValue;
    }

    /**
     * Convenience method to close a stream ignoring exceptions.
     *
     * @param closeable closeable object
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Convenience method to close a zip file ignoring exceptions.
     *
     * @param zip zip file
     */
    public static void close(ZipFile zip) {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns true if string is null or consists only of whitespace.
     *
     * @param s possibly null string
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.trim()
            .isEmpty();
    }

    /**
     * Returns true if collection is null or empty.
     *
     * @param c possibly null collection
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /**
     * Returns true if map is null or empty.
     *
     * @param m possibly null map
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    public static void setMinecraft(File gameDir) {

        minecraftDir = Launch.minecraftHome;
        if (gameDir == null || !gameDir.isDirectory()) {
            MCPatcherUtils.gameDir = minecraftDir;
        } else {
            MCPatcherUtils.gameDir = gameDir.getAbsoluteFile();
        }
        log.info("MCPatcherUtils initialized:");
    }

    /**
     * Get array of rgb values from image.
     *
     * @param image input image
     * @return rgb array
     */
    public static int[] getImageRGB(BufferedImage image) {
        if (image == null) {
            return null;
        } else {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] rgb = new int[width * height];
            image.getRGB(0, 0, width, height, rgb, 0, width);
            return rgb;
        }
    }

    /**
     * Parse a comma-separated list of integers/ranges.
     *
     * @param list     comma- or space-separated list, e.g., 2-4,5,8,12-20
     * @param minValue smallest value allowed in the list
     * @param maxValue largest value allowed in the list
     * @return possibly empty integer array
     */
    public static int[] parseIntegerList(String list, int minValue, int maxValue) {
        ArrayList<Integer> tmpList = new ArrayList<>();
        Pattern p = Pattern.compile("(\\d*)-(\\d*)");
        for (String token : list.replace(',', ' ')
            .split("\\s+")) {
            try {
                if (token.matches("\\d+")) {
                    tmpList.add(Integer.parseInt(token));
                } else {
                    Matcher m = p.matcher(token);
                    if (m.matches()) {
                        String a = m.group(1);
                        String b = m.group(2);
                        int min = a.isEmpty() ? minValue : Integer.parseInt(a);
                        int max = b.isEmpty() ? maxValue : Integer.parseInt(b);
                        for (int i = min; i <= max; i++) {
                            tmpList.add(i);
                        }
                    }
                }
            } catch (NumberFormatException e) {}
        }
        if (minValue <= maxValue) {
            for (int i = 0; i < tmpList.size();) {
                if (tmpList.get(i) < minValue || tmpList.get(i) > maxValue) {
                    tmpList.remove(i);
                } else {
                    i++;
                }
            }
        }
        int[] a = new int[tmpList.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = tmpList.get(i);
        }
        return a;
    }
}
