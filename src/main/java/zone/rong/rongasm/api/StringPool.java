package zone.rong.rongasm.api;

import org.embeddedt.archaicfix.ArchaicLogger;
import speiger.src.collections.ints.maps.impl.misc.Int2ObjectArrayMap;
import speiger.src.collections.objects.sets.ObjectOpenHashSet;

import java.util.Locale;

public class StringPool {

    public static final int FILE_PERMISSIONS_ID = 1;

    private static final Int2ObjectArrayMap<Internal> POOLS = new Int2ObjectArrayMap<>();

    static {
        establishPool(-1, 12288, "", " ");
        POOLS.setDefaultReturnValue(POOLS.get(-1));
    }

    public static void establishPool(int poolId, int expectedSize, String... startingValues) {
        if (POOLS.containsKey(poolId)) {
            return;
        }
        POOLS.put(poolId, new Internal(poolId, expectedSize, startingValues));
    }

    public static Internal purgePool(int poolId) {
        return POOLS.remove(poolId);
    }

    public static int getSize() {
        return POOLS.getDefaultReturnValue().internalPool.size();
    }

    public static int getSize(int pool) {
        return POOLS.get(pool).internalPool.size();
    }

    public static long getDeduplicatedCount() {
        return POOLS.getDefaultReturnValue().deduplicatedCount;
    }

    public static long getDeduplicatedCount(int pool) {
        return POOLS.get(pool).deduplicatedCount;
    }

    public static String canonicalize(String string) {
        synchronized (POOLS) {
            return POOLS.getDefaultReturnValue().addOrGet(string);
        }
    }

    public static String unsafe$Canonicalize(String string) {
        return POOLS.getDefaultReturnValue().addOrGet(string);
    }

    @SuppressWarnings("unused")
    public static String lowerCaseAndCanonicalize(String string) {
        synchronized (POOLS) {
            return POOLS.getDefaultReturnValue().addOrGet(string.toLowerCase(Locale.ROOT));
        }
    }

    @SuppressWarnings("unused")
    public static String unsafe$LowerCaseAndCanonicalize(String string) {
        return POOLS.getDefaultReturnValue().addOrGet(string.toLowerCase(Locale.ROOT));
    }

    public static String canonicalize(String string, int poolId, boolean checkMainPool) {
        if (checkMainPool) {
            synchronized (POOLS) {
                ObjectOpenHashSet<String> internalPool = POOLS.get(poolId).internalPool;
                String canonicalized = internalPool.contains(string) ? internalPool.addOrGet(string) : null;
                if (canonicalized != null) {
                    return canonicalized;
                }
            }
        }
        synchronized (POOLS) {
            return POOLS.get(poolId).addOrGet(string);
        }
    }

    public static String unsafe$Canonicalize(String string, int poolId, boolean checkMainPool) {
        if (checkMainPool) {
            ObjectOpenHashSet<String> internalPool = POOLS.get(poolId).internalPool;
            String canonicalized = internalPool.contains(string) ? internalPool.addOrGet(string) : null;
            if (canonicalized != null) {
                return canonicalized;
            }
        }
        return POOLS.get(poolId).addOrGet(string);
    }

    public static class EventHandler {
        /*
        @SubscribeEvent
        public void onDebugList(RenderGameOverlayEvent.Text event) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.gameSettings.showDebugInfo) {
                ArrayList<String> list = event.left;
                if (!list.get(list.size() - 1).equals("")) {
                    list.add("");
                }
                int size = getSize();
                long deduplicatedCount = getDeduplicatedCount();
                list.add(String.format("%s%s%s: %s strings processed. %s unique, %s deduplicated.", EnumChatFormatting.AQUA, "<ArchaicFix>", EnumChatFormatting.RESET, deduplicatedCount, size, deduplicatedCount - size));
            }
        }
        */
    }



    static class Internal {

        final int id;
        final ObjectOpenHashSet<String> internalPool;

        long deduplicatedCount;

        @SuppressWarnings("all")
        Internal(int id, int expectedSize, String... startingValues) {
            this.id = id;
            this.internalPool = new ObjectOpenHashSet<>(expectedSize);
            for (String startingValue : startingValues) {
                this.internalPool.add(startingValue);
            }
        }

        String addOrGet(String string) {
            deduplicatedCount++;
            return internalPool.addOrGet(string);
        }

        @Override
        protected void finalize() {
            ArchaicLogger.LOGGER.warn("Clearing LoliStringPool {}", id);
        }
    }

}
