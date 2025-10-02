package com.embeddedt.chunkbert;

import net.minecraftforge.common.config.Config;

@Config(modid = Chunkbert.MOD_ID)
public class ChunkbertConfig {
    public static boolean enabled = true;
    @Config.Comment("Do not load block entities (e.g. chests) in fake chunks.\nThese need updating every tick which can add up.\n\nEnabled by default because the render distance for block entities is usually smaller than the server-view distance anyway.")
    public static boolean noBlockEntities = true;
    @Config.Comment("Delays the unloading of chunks which are outside your view distance.\nSaves you from having to reload all chunks when leaving the area for a short moment (e.g. cut scenes).\nDoes not work across dimensions.")
    public static int unloadDelaySecs = 60;

    @Config.Comment("Overwrites the view-distance of the integrated server.\nThis allows Bobby to be useful in Singleplayer.\n\nDisabled when at 0.\nBobby is active in singleplayer only if this is enabled.\nRequires re-log to en-/disable.")
    @Config.RangeInt(min = 0)
    public static int viewDistanceOverwrite = 0;
}