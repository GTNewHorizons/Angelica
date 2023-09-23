package org.embeddedt.archaicfix.helpers;

import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DragonAPIHelper {
    public static boolean isVersionInInclusiveRange(int minMajor, char minMinor, int maxMajor, char maxMinor) {
        try(InputStream is = DragonAPIHelper.class.getResourceAsStream("/version_dragonapi.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                int major = Integer.parseInt(props.getProperty("Major"));
                char minor = props.getProperty("Minor").charAt(0);

                Pair<Integer, Character> min = Pair.of(minMajor, minMinor);
                Pair<Integer, Character> max = Pair.of(maxMajor, maxMinor);
                Pair<Integer, Character> ver = Pair.of(major, minor);

                return min.compareTo(ver) <= 0 && ver.compareTo(max) <= 0;
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
