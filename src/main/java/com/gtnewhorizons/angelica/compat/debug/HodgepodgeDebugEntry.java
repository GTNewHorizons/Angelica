package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenApi;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.mitchej123.hodgepodge.config.DebugConfig;
import com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler;
import com.mitchej123.hodgepodge.mixins.hooks.ChunkGenStats;
import net.minecraftforge.classloading.FMLForgePlugin;

public final class HodgepodgeDebugEntry implements DebugScreenEntry {

    static final String GROUP_ID = "hodgepodge:chunkgen";
    static final String ENTRY_ID = "hodgepodge:chunkgen";

    private static DebugEntryHandle handle;

    private final DebugGroupHandle group;
    private boolean scheduled;
    private double budgetUsedMs;
    private double budgetAvailMs;
    private double terrainGenEmaMs;
    private double populateEmaMs;
    private int genQueue;
    private int popQueue;
    private int pipelineBacklog;
    private int terrainGenCount;
    private int popCount;
    private int blockedLoads;
    private int stuckCount;
    private int neighborFills;
    private double debtMs;

    private HodgepodgeDebugEntry(DebugGroupHandle group) {
        this.group = group;
    }

    static void register() {
        if (!DebugConfig.showChunkGenDebug && FMLForgePlugin.RUNTIME_DEOBF) return;
        handle = DebugCompat.register(GROUP_ID, ENTRY_ID, "Chunk Generation", 0xFFAA00, entry -> new HodgepodgeDebugEntry(DebugScreenApi.group(GROUP_ID)));
    }

    public static boolean active() {
        return DebugCompat.active(handle);
    }

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (context.isSampleDue()) sample(context);
        if (!scheduled) return;
        writeFacts(displayer, group, budgetUsedMs, budgetAvailMs, terrainGenEmaMs, populateEmaMs, genQueue, popQueue, pipelineBacklog, terrainGenCount, popCount, blockedLoads, stuckCount, neighborFills, debtMs);
    }

    private void sample(DebugContext context) {
        final ChunkGenScheduler scheduler = ChunkGenScheduler.getForDimension(context.player().dimension);
        scheduled = scheduler != null;
        if (scheduler == null) return;
        final ChunkGenStats stats = scheduler.getStats();
        budgetUsedMs = stats.getSnapBudgetUsedMs();
        budgetAvailMs = stats.getSnapBudgetAvailMs();
        terrainGenEmaMs = stats.getTerrainGenEmaMs();
        populateEmaMs = stats.getPopulateEmaMs();
        genQueue = stats.getSnapGenQueue();
        popQueue = stats.getSnapPopQueue();
        pipelineBacklog = stats.getSnapPipelineBacklog();
        terrainGenCount = stats.getSnapTerrainGenCount();
        popCount = stats.getSnapPopCount();
        blockedLoads = stats.getSnapBlockedLoads();
        stuckCount = stats.getSnapStuckCount();
        neighborFills = stats.getSnapNeighborFills();
        debtMs = stats.getDebtMs();
    }

    static void writeFacts(DebugScreenDisplayer displayer, DebugGroupHandle group, double budgetUsedMs, double budgetAvailMs, double terrainGenEmaMs, double populateEmaMs, int genQueue, int popQueue, int pipelineBacklog, int terrainGenCount, int popCount, int blockedLoads, int stuckCount, int neighborFills, double debtMs) {
        displayer.fact(group, "Budget").value(budgetUsedMs, 1).text(" / ").value(budgetAvailMs, 1).text(" ms");
        displayer.fact(group, "Average").text("terrain ").value(terrainGenEmaMs, 1).text(" ms, populate ").value(populateEmaMs, 1).text(" ms");
        displayer.fact(group, "Queues").text("gen ").value(genQueue).text(", populate ").value(popQueue).text(", pipeline ").value(pipelineBacklog);
        displayer.fact(group, "Active").text("gen ").value(terrainGenCount).text(", populate ").value(popCount);
        displayer.fact(group, "Blocked").value(blockedLoads).text(" blocked, ").value(stuckCount).text(" stuck, ").value(neighborFills).text(" fills");
        displayer.fact(group, "Debt").value(debtMs, 1).text(" ms");
    }
}
