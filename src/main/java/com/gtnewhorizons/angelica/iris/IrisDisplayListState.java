package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Records Java-side rendering scopes alongside cached geometry.
 */
public final class IrisDisplayListState {

    private IrisDisplayListState() {}

    public static void runProgramTransition(Runnable transition) {
        BatchStateGuard.suspend();
        try {
            runUnrecorded(transition);
        } finally {
            BatchStateGuard.resume();
        }
    }

    public static void runUnrecorded(Runnable transition) {
        final DisplayListManager.RecordMode mode = DisplayListManager.getRecordMode();
        if (mode == DisplayListManager.RecordMode.COMPILE) return;
        if (mode == DisplayListManager.RecordMode.NONE) {
            transition.run();
            return;
        }
        final CommandRecorder recorder = DisplayListManager.pauseRecording();
        try {
            transition.run();
        } finally {
            DisplayListManager.resumeRecording(recorder);
        }
    }

    private static void record(DisplayListCommand cmd) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommand(cmd);
        }
    }

    public static void recordSpecialCondition(SpecialCondition condition, boolean begin) {
        if (DisplayListManager.isRecording()) record(new SpecialConditionCmd(condition, begin));
    }

    public static void recordGlintSpan(boolean begin) {
        if (DisplayListManager.isRecording()) record(new GlintSpanCmd(begin));
    }

    /** Keep nested items on the entity pass, including its depth and TAA behavior. */
    public static void recordOverridePhase(WorldRenderingPhase phase) {
        if (DisplayListManager.isRecording()) record(new OverridePhaseCmd(phase));
    }

    public static void recordPhaseScope(WorldRenderingPhase phase, boolean begin) {
        if (DisplayListManager.isRecording()) record(new PhaseScopeCmd(phase, begin));
    }

    public static void recordNestedEntityScope(boolean begin) {
        if (DisplayListManager.isRecording()) record(new NestedEntityScopeCmd(begin));
    }

    public static void recordTranslucency(Boolean translucent) {
        if (DisplayListManager.isRecording()) record(new TranslucencyCmd(translucent));
    }

    public static void recordTranslucencyScope(Boolean translucent, boolean begin) {
        if (DisplayListManager.isRecording()) record(new TranslucencyScopeCmd(translucent, begin));
    }

    /** Record explicit IDs as well as item identities so replay cannot inherit a previous draw's ID. */
    public static void recordItemId(int itemId) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommandIfChanged(new ItemIdCmd(itemId));
        }
    }

    public static void recordItem(ItemStack item) {
        if (DisplayListManager.isRecording()) {
            record(new ItemCmd(item == null ? null : item.copy()));
        }
    }

    public static void recordItem(Item item, int metadata) {
        if (DisplayListManager.isRecording()) record(new ItemTypeCmd(item, metadata));
    }

    public static void recordNamedItem(NamespacedId item) {
        if (DisplayListManager.isRecording()) record(new NamedItemCmd(item));
    }

    public static void recordBlockItem(Block block, int metadata) {
        if (DisplayListManager.isRecording()) record(new BlockItemCmd(block, metadata));
    }

    public static void recordItemScope(boolean push) {
        if (DisplayListManager.isRecording()) record(new ItemScopeCmd(push));
    }

    public static void recordEntity(Entity entity) {
        if (DisplayListManager.isRecording()) {
            record(new EntityCmd(EntityIdHelper.snapshot(entity)));
        }
    }

    public static void recordEntityScope(boolean push) {
        if (DisplayListManager.isRecording()) record(new EntityScopeCmd(push));
    }

    public static void recordEntityAndItem(int entity, int item) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommandIfChanged(new EntityAndItemCmd(entity, item));
        }
    }

    public static void recordNamedEntity(NamespacedId entity) {
        if (DisplayListManager.isRecording()) record(new NamedEntityCmd(entity));
    }

    public static void recordBlockEntity(int id) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommandIfChanged(new BlockEntityIdCmd(id));
        }
    }

    public static void recordBlockEntity(Block block, int metadata) {
        if (DisplayListManager.isRecording()) record(new BlockEntityCmd(block, metadata));
    }

    public static void recordBlockEntityScope(boolean push) {
        if (DisplayListManager.isRecording()) record(new BlockEntityScopeCmd(push));
    }

    public static void recordEntityColor(float r, float g, float b, float a) {
        if (DisplayListManager.isRecording()) {
            DisplayListManager.recordStateCommandIfChanged(new EntityColorCmd(r, g, b, a));
        }
    }

    public static void recordEntityColorScope(boolean push) {
        if (DisplayListManager.isRecording()) record(new EntityColorScopeCmd(push));
    }

    public static void recordBlockEntityAttribute(Block block, int metadata) {
        if (DisplayListManager.isRecording()) record(new BlockEntityAttributeCmd(block, metadata));
    }

    private record BlockEntityAttributeCmd(Block block, int metadata) implements DisplayListCommand {
        @Override
        public void execute() {
            if (block == null) BlockMaterialAttribute.reset();
            else BlockMaterialAttribute.set(block, metadata);
        }
    }

    private record EntityAndItemCmd(int entity, int item) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(entity, item); }
    }

    private record NamedEntityCmd(NamespacedId entity) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentNamedEntity(entity); }
    }

    private record BlockEntityIdCmd(int id) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentBlockEntity(id); }
    }

    private record BlockEntityCmd(Block block, int metadata) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentBlockEntity(block, metadata); }
    }

    private record BlockEntityScopeCmd(boolean push) implements DisplayListCommand {
        @Override
        public void execute() {
            if (push) CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
            else CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
        }
    }

    private record EntityColorCmd(float r, float g, float b, float a) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentEntityColor(r, g, b, a); }
    }

    private record EntityColorScopeCmd(boolean push) implements DisplayListCommand {
        @Override
        public void execute() {
            if (push) CapturedRenderingState.INSTANCE.pushCurrentEntityColor();
            else CapturedRenderingState.INSTANCE.popCurrentEntityColor();
        }
    }

    private record EntityCmd(EntityIdHelper.EntityIdentity identity) implements DisplayListCommand {
        @Override
        public void execute() {
            CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(identity == null ? -1 : identity.resolve(), 0);
        }
    }

    private record EntityScopeCmd(boolean push) implements DisplayListCommand {
        @Override
        public void execute() {
            if (push) CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
            else CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
        }
    }

    private record ItemScopeCmd(boolean push) implements DisplayListCommand {
        @Override
        public void execute() {
            if (push) {
                CapturedRenderingState.INSTANCE.pushCurrentRenderedItem();
            } else {
                CapturedRenderingState.INSTANCE.popCurrentRenderedItem();
            }
        }
    }

    private record SpecialConditionCmd(SpecialCondition condition, boolean begin) implements DisplayListCommand {
        @Override
        public void execute() {
            if (begin) {
                GbufferPrograms.setupSpecialRenderCondition(condition);
            } else {
                GbufferPrograms.teardownSpecialRenderCondition();
            }
        }
    }

    private record GlintSpanCmd(boolean begin) implements DisplayListCommand {
        @Override
        public void execute() {
            if (begin) {
                ShaderGlint.beginGlint();
            } else {
                ShaderGlint.endGlint();
            }
        }
    }

    private record OverridePhaseCmd(WorldRenderingPhase phase) implements DisplayListCommand {
        @Override
        public void execute() {
            GbufferPrograms.setOverridePhase(phase);
        }
    }

    private record PhaseScopeCmd(WorldRenderingPhase phase, boolean begin) implements DisplayListCommand {
        @Override
        public void execute() {
            if (begin) GbufferPrograms.pushOverridePhase(phase);
            else GbufferPrograms.popOverridePhase();
        }
    }

    private static final BooleanArrayList replayNestedEntityStack = new BooleanArrayList();

    private record NestedEntityScopeCmd(boolean begin) implements DisplayListCommand {
        @Override
        public void execute() {
            if (begin) replayNestedEntityStack.push(GbufferPrograms.beginNestedEntityPhase());
            else GbufferPrograms.endNestedEntityPhase(replayNestedEntityStack.popBoolean());
        }
    }

    private record TranslucencyCmd(Boolean translucent) implements DisplayListCommand {
        @Override
        public void execute() {
            GbufferPrograms.setTranslucencyDeclaration(translucent);
        }
    }

    private static final ObjectArrayList<Boolean> replayTranslucencyStack = new ObjectArrayList<>();

    private record TranslucencyScopeCmd(Boolean translucent, boolean begin) implements DisplayListCommand {
        @Override
        public void execute() {
            if (begin) replayTranslucencyStack.add(GbufferPrograms.beginTranslucencyDeclaration(translucent));
            else GbufferPrograms.endTranslucencyDeclaration(replayTranslucencyStack.pop());
        }
    }

    private record ItemIdCmd(int itemId) implements DisplayListCommand {
        @Override
        public void execute() {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemId);
        }
    }

    private record ItemCmd(ItemStack item) implements DisplayListCommand {
        @Override
        public void execute() {
            ItemIdManager.setItemId(item);
        }
    }

    private record ItemTypeCmd(Item item, int metadata) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentRenderedItem(item, metadata); }
    }

    private record NamedItemCmd(NamespacedId item) implements DisplayListCommand {
        @Override
        public void execute() { CapturedRenderingState.INSTANCE.setCurrentNamedItem(item); }
    }

    private record BlockItemCmd(Block block, int metadata) implements DisplayListCommand {
        @Override
        public void execute() {
            ItemIdManager.setBlockId(block, metadata);
        }
    }
}
