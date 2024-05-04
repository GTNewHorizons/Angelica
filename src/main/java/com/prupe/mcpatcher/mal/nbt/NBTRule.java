package com.prupe.mcpatcher.mal.nbt;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import jss.notfine.util.NBTTagCompoundExpansion;
import jss.notfine.util.NBTTagListExpansion;

abstract public class NBTRule {

    public static final String NBT_RULE_PREFIX = "nbt.";
    public static final String NBT_RULE_SEPARATOR = ".";
    public static final String NBT_RULE_WILDCARD = "*";
    public static final String NBT_REGEX_PREFIX = "regex:";
    public static final String NBT_IREGEX_PREFIX = "iregex:";
    public static final String NBT_GLOB_PREFIX = "pattern:";
    public static final String NBT_IGLOB_PREFIX = "ipattern:";

    private final String[] tagName;
    private final Integer[] tagIndex;

    public static NBTRule create(String tag, String value) {
        if (tag == null || value == null || !tag.startsWith(NBT_RULE_PREFIX)) {
            return null;
        }
        try {
            tag = tag.substring(NBT_RULE_PREFIX.length());
            if (value.startsWith(NBT_REGEX_PREFIX)) {
                return new Regex(tag, value.substring(NBT_REGEX_PREFIX.length()), true);
            } else if (value.startsWith(NBT_IREGEX_PREFIX)) {
                return new Regex(tag, value.substring(NBT_IREGEX_PREFIX.length()), false);
            } else if (value.startsWith(NBT_GLOB_PREFIX)) {
                return new Glob(tag, value.substring(NBT_GLOB_PREFIX.length()), true);
            } else if (value.startsWith(NBT_IGLOB_PREFIX)) {
                return new Glob(tag, value.substring(NBT_IGLOB_PREFIX.length()), false);
            } else {
                return new Exact(tag, value);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    protected NBTRule(String tag, String value) {
        tagName = tag.split(Pattern.quote(NBT_RULE_SEPARATOR));
        tagIndex = new Integer[tagName.length];
        for (int i = 0; i < tagName.length; i++) {
            if (NBT_RULE_WILDCARD.equals(tagName[i])) {
                tagName[i] = null;
                tagIndex[i] = null;
            } else {
                try {
                    tagIndex[i] = Integer.valueOf(tagName[i]);
                } catch (NumberFormatException e) {
                    tagIndex[i] = -1;
                }
            }
        }
    }

    public final boolean match(NBTTagCompound nbt) {
        return nbt != null && match(nbt, 0);
    }

    private boolean match(NBTTagCompound nbt, int index) {
        if (index >= tagName.length) {
            return false;
        } else if (tagName[index] == null) {
            for (NBTBase nbtBase : ((NBTTagCompoundExpansion) nbt).getTags()) {
                if (match1(nbtBase, index + 1)) {
                    return true;
                }
            }
            return false;
        } else {
            NBTBase nbtBase = nbt.getTag(tagName[index]);
            return match1(nbtBase, index + 1);
        }
    }

    private boolean match(NBTTagList nbt, int index) {
        if (index >= tagIndex.length) {
            return false;
        } else if (tagIndex[index] == null) {
            for (int i = 0; i < nbt.tagCount(); i++) {
                if (match1(((NBTTagListExpansion) nbt).tagAt(i), index + 1)) {
                    return true;
                }
            }
            return false;
        } else {
            int tagNum = tagIndex[index];
            return tagNum >= 0 && tagNum < nbt.tagCount()
                && match1(((NBTTagListExpansion) nbt).tagAt(tagNum), index + 1);
        }
    }

    private boolean match1(NBTBase nbt, int index) {
        if (nbt == null) {
            return false;
        } else if (nbt instanceof NBTTagCompound) {
            return match((NBTTagCompound) nbt, index);
        } else if (nbt instanceof NBTTagList) {
            return match((NBTTagList) nbt, index);
        } else if (index < tagName.length) {
            return false;
        } else if (nbt instanceof NBTTagString) {
            return match((NBTTagString) nbt);
        } else if (nbt instanceof NBTTagInt) {
            return match((NBTTagInt) nbt);
        } else if (nbt instanceof NBTTagDouble) {
            return match((NBTTagDouble) nbt);
        } else if (nbt instanceof NBTTagFloat) {
            return match((NBTTagFloat) nbt);
        } else if (nbt instanceof NBTTagLong) {
            return match((NBTTagLong) nbt);
        } else if (nbt instanceof NBTTagShort) {
            return match((NBTTagShort) nbt);
        } else if (nbt instanceof NBTTagByte) {
            return match((NBTTagByte) nbt);
        } else {
            return false;
        }
    }

    protected boolean match(NBTTagByte nbt) {
        return false;
    }

    protected boolean match(NBTTagDouble nbt) {
        return false;
    }

    protected boolean match(NBTTagFloat nbt) {
        return false;
    }

    protected boolean match(NBTTagInt nbt) {
        return false;
    }

    protected boolean match(NBTTagLong nbt) {
        return false;
    }

    protected boolean match(NBTTagShort nbt) {
        return false;
    }

    protected boolean match(NBTTagString nbt) {
        return false;
    }

    private static final class Exact extends NBTRule {

        private final Byte byteValue;
        private final Double doubleValue;
        private final Float floatValue;
        private final Integer integerValue;
        private final Long longValue;
        private final Short shortValue;
        private final String stringValue;

        Exact(String tag, String value) {
            super(tag, value);
            stringValue = value;

            doubleValue = parse(Double.class, value);
            if (doubleValue == null) {
                floatValue = null;
            } else {
                floatValue = doubleValue.floatValue();
            }

            longValue = parse(Long.class, value);
            if (longValue == null) {
                byteValue = null;
                integerValue = null;
                shortValue = null;
            } else {
                byteValue = longValue.byteValue();
                integerValue = longValue.intValue();
                shortValue = longValue.shortValue();
            }
        }

        // Cursed
        private static <T extends Number> T parse(Class<T> cl, String value) {
            try {
                Method valueOf = cl.getDeclaredMethod("valueOf", String.class);
                Object result = valueOf.invoke(null, value);
                if (result != null && cl.isAssignableFrom(result.getClass())) {
                    return cl.cast(result);
                }
            } catch (Throwable e) {}
            return null;
        }

        @Override
        protected boolean match(NBTTagByte nbt) {
            return byteValue != null && byteValue == nbt.func_150290_f();
        }

        @Override
        protected boolean match(NBTTagDouble nbt) {
            return doubleValue != null && doubleValue == nbt.func_150286_g();
        }

        @Override
        protected boolean match(NBTTagFloat nbt) {
            return floatValue != null && floatValue == nbt.func_150288_h();
        }

        @Override
        protected boolean match(NBTTagInt nbt) {
            return integerValue != null && integerValue == nbt.func_150287_d();
        }

        @Override
        protected boolean match(NBTTagLong nbt) {
            return longValue != null && longValue == nbt.func_150291_c();
        }

        @Override
        protected boolean match(NBTTagShort nbt) {
            return shortValue != null && shortValue == nbt.func_150289_e();
        }

        @Override
        protected boolean match(NBTTagString nbt) {
            return nbt.func_150285_a_()
                .equals(stringValue);
        }
    }

    private static final class Regex extends NBTRule {

        private final Pattern pattern;

        Regex(String tag, String value, boolean caseSensitive) {
            super(tag, value);
            pattern = Pattern.compile(value, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
        }

        @Override
        protected boolean match(NBTTagString nbt) {
            return pattern.matcher(nbt.func_150285_a_())
                .matches();
        }
    }

    private static final class Glob extends NBTRule {

        private static final char STAR = '*';
        private static final char SINGLE = '?';
        private static final char ESCAPE = '\\';

        private final String glob;
        private final boolean caseSensitive;

        protected Glob(String tag, String value, boolean caseSensitive) {
            super(tag, value);
            this.caseSensitive = caseSensitive;
            if (!caseSensitive) {
                value = value.toLowerCase();
            }
            glob = value;
        }

        @Override
        protected boolean match(NBTTagString nbt) {
            String value = nbt.func_150285_a_();
            return matchPartial(value, 0, value.length(), 0, glob.length());
        }

        private boolean matchPartial(String value, int curV, int maxV, int curG, int maxG) {
            for (; curG < maxG; curG++, curV++) {
                char g = glob.charAt(curG);
                if (g == STAR) {
                    while (true) {
                        if (matchPartial(value, curV, maxV, curG + 1, maxG)) {
                            return true;
                        }
                        if (curV >= maxV) {
                            break;
                        }
                        curV++;
                    }
                    return false;
                } else if (curV >= maxV) {
                    break;
                } else if (g == SINGLE) {
                    continue;
                }
                if (g == ESCAPE && curG + 1 < maxG) {
                    curG++;
                    g = glob.charAt(curG);
                }
                if (!matchChar(g, value.charAt(curV))) {
                    return false;
                }
            }
            return curG == maxG && curV == maxV;
        }

        private boolean matchChar(char a, char b) {
            return a == (caseSensitive ? b : Character.toLowerCase(b));
        }
    }
}
