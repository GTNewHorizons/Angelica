package net.coderbot.iris.block_rendering;

import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.materialmap.PropertiesTokenizer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps objects with specific NBT properties to shader IDs. Generic over the
 * key type so it works for blocks, items, and entities.
 *
 * Example:
 *   block.37=flower_pot[Item=minecraft:red_flower,Data=0]
 *   item.10=diamond_sword[display.Name='Pointy Stick']
 */
public class NbtConditionalIdMap<K> {

    /**
     * A single property matcher within a condition.
     */
    record PropertyMatcher(String key, String[] path, String expectedValue, long expectedLong, boolean isExpectedLong) {
        boolean isPathMatch() { return path != null; }
        boolean isExistenceOnly() { return expectedValue == null; }
    }

    record NbtCondition(PropertyMatcher[] matchers, int shaderId) {}

    private final Map<K, List<NbtCondition>> conditionsByKey;

    public NbtConditionalIdMap() {
        this.conditionsByKey = new HashMap<>();
    }

    /**
     * Registers an NBT condition. Unquoted values are resolved via item/block
     * registries at registration time. Single-quoted values are kept as literals.
     * Dot notation paths are pre-split here so we prevent allocations.
     */
    public void addCondition(K key, Map<String, PropertiesTokenizer.NbtValue> nbtProperties, int shaderId) {
        final PropertyMatcher[] matchers = new PropertyMatcher[nbtProperties.size()];
        int i = 0;

        for (Map.Entry<String, PropertiesTokenizer.NbtValue> entry : nbtProperties.entrySet()) {
            final String nbtKey = entry.getKey();
            final PropertiesTokenizer.NbtValue nbtValue = entry.getValue();

            final String resolvedValue;
            if (nbtValue == null) {
                resolvedValue = null;

            } else if (nbtValue.literal()) {
                resolvedValue = nbtValue.value();

            } else {
                resolvedValue = resolveValue(nbtValue.value());
            }

            final String[] path = nbtKey.contains(".") ? nbtKey.split("\\.") : null;

            long expectedLong = 0;
            boolean isExpectedLong = false;
            if (resolvedValue != null) {
                try {
                    expectedLong = Long.parseLong(resolvedValue);
                    isExpectedLong = true;
                } catch (NumberFormatException ignored) {}
            }

            matchers[i++] = new PropertyMatcher(nbtKey, path, resolvedValue, expectedLong, isExpectedLong);
        }

        conditionsByKey.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new NbtCondition(matchers, shaderId));
    }

    public boolean hasConditions(K key) {
        return conditionsByKey.containsKey(key);
    }

    public boolean isEmpty() {
        return conditionsByKey.isEmpty();
    }

    /**
     * Resolves the shader ID by matching the provided NBT data.
     */
    public int resolve(K key, NBTTagCompound nbt) {
        final List<NbtCondition> conditions = conditionsByKey.get(key);
        if (conditions == null) {
            return -1;
        }

        for (NbtCondition condition : conditions) {
            if (matchesNbt(nbt, condition.matchers())) {
                return condition.shaderId;
            }
        }

        return -1;
    }

    private static boolean matchesNbt(NBTTagCompound nbt, PropertyMatcher[] matchers) {
        for (PropertyMatcher matcher : matchers) {
            if (matcher.isPathMatch()) {
                if (!matchesPath(nbt, matcher.path(), 0, matcher)) {
                    return false;
                }

            } else {
                if (!nbt.hasKey(matcher.key())) {
                    return false;
                }

                if (!matcher.isExistenceOnly()) {
                    if (!matchesNbtValue(nbt.getTag(matcher.key()), matcher)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Search through dot-separated path in NBT data.
     */
    private static boolean matchesPath(NBTBase tag, String[] path, int depth, PropertyMatcher matcher) {
        if (depth == path.length) {
            if (matcher.isExistenceOnly()) {
                return true;
            }

            return matchesNbtValue(tag, matcher);
        }

        final String segment = path[depth];

        if (tag instanceof NBTTagCompound compound) {
            if (!compound.hasKey(segment)) {
                return false;
            }

            return matchesPath(compound.getTag(segment), path, depth + 1, matcher);
        }

        if (tag instanceof NBTTagList list) {
            for (int i = 0; i < list.tagCount(); i++) {
                if (matchesPath(list.getCompoundTagAt(i), path, depth, matcher)) {
                    return true;
                }
            }

            return false;
        }

        return false;
    }

    /**
     * Compares an NBT tag's value against an expected string using type-specific
     * accessors to avoid toString() allocations where possible.
     * Makes it so you don't need to specify the type either in the .properties files.
     */
    private static boolean matchesNbtValue(NBTBase tag, PropertyMatcher matcher) {
        return switch (tag.getId()) {
            case 1 -> // TAG_Byte
                matcher.isExpectedLong() && ((net.minecraft.nbt.NBTTagByte) tag).func_150290_f() == matcher.expectedLong();
            case 2 -> // TAG_Short
                matcher.isExpectedLong() && ((net.minecraft.nbt.NBTTagShort) tag).func_150289_e() == matcher.expectedLong();
            case 3 -> // TAG_Int
                matcher.isExpectedLong() && ((net.minecraft.nbt.NBTTagInt) tag).func_150287_d() == matcher.expectedLong();
            case 4 -> // TAG_Long
                matcher.isExpectedLong() && ((net.minecraft.nbt.NBTTagLong) tag).func_150291_c() == matcher.expectedLong();
            case 5 -> // TAG_Float
                matcher.expectedValue().equals(String.valueOf(((net.minecraft.nbt.NBTTagFloat) tag).func_150288_h()));
            case 6 -> // TAG_Double
                matcher.expectedValue().equals(String.valueOf(((net.minecraft.nbt.NBTTagDouble) tag).func_150286_g()));
            case 8 -> // TAG_String
                matcher.expectedValue().equals(((net.minecraft.nbt.NBTTagString) tag).func_150285_a_());
            default -> // Compounds, lists, arrays — fall back to toString
                matcher.expectedValue().equals(PropertiesTokenizer.stripQuotes(tag.toString()));
        };
    }

    /**
     * Tries to resolve a value as a registry name (item or block) to its numeric ID.
     * If the value is already numeric or can't be resolved, return it unchanged.
     */
    private static String resolveValue(String value) {
        try {
            Integer.parseInt(value);
            return value;
        } catch (NumberFormatException ignored) {}

        if (!value.contains(":")) {
            Iris.logger.warn("NBT value '{}' is missing a namespace, please use the full form e.g. 'minecraft:{}'", value, value);
            return value;
        }

        final ResourceLocation loc = new ResourceLocation(value);

        final Item item = (Item) Item.itemRegistry.getObject(loc.toString());
        if (item != null) {
            return String.valueOf(Item.getIdFromItem(item));
        }

        final Block block = (Block) Block.blockRegistry.getObject(loc.toString());
        if (block != null) {
            return String.valueOf(Block.getIdFromBlock(block));
        }

        Iris.logger.warn("Could not resolve '{}' to an item or block ID", value);
        return value;
    }
}
