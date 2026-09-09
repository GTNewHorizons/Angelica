package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.WorldRandomTickAccessor;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flies a deterministic camera path
 */
public final class FlybyRunner {
    public static final FlybyRunner INSTANCE = new FlybyRunner();

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final long[] NO_FRAMES = new long[0];
    private static final double[] NO_PATH = new double[0];
    private static final float[] NO_YAW = new float[0];
    private static final byte[] NO_PHASE = new byte[0];
    private static final int MAX_RECORDED_FRAMES = 200_000;
    private static final int EXIT_TICKS = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int SPAWN_POLL_TICKS = 10;
    private static final int SPAWN_STABLE_POLLS = 3;
    private static final int SPAWN_TIMEOUT_TICKS = 200;
    private static final int LOAD_PROGRESS_TICKS = 200;
    private static final int TRACY_WAIT_WARN_TICKS = 100;
    private static final int TRACY_WAIT_WARN_REPEAT_TICKS = 400;
    private static final int SETTLE_AFTER_ARRIVAL_TICKS = 150;
    private static final int MIN_SECTION_GROWTH = 256;
    private static final int CHUNKS_SENT_PER_TICK = 5;
    private static final int MESH_DRAIN_STABLE_TICKS = 20;
    private static final int MESH_DRAIN_TIMEOUT_TICKS = 600;
    private static volatile boolean entitiesFrozen;
    private static volatile boolean blockTicksFrozen;
    private static volatile boolean worldChangesDiscarded;
    private static volatile boolean pacingSuppressed;
    private final TracyCaptureProcess capture = new TracyCaptureProcess();
    private final AtomicInteger chunksEnteringWorld = new AtomicInteger();
    private final AtomicInteger chunksReadFromDisk = new AtomicInteger();
    private State state = State.IDLE;
    private FlybyRoute route;
    private int warmupTicks;
    private int runLength;
    private double speed;
    private int runTicks;
    private int tick;
    private boolean captureEveryRun;
    private boolean propertiesLogged;
    private String loggedSettingsTag = "";
    private boolean waitForTracy;
    private boolean waitForFocus;
    private boolean exitWhenDone;
    private boolean startedFromProperties;
    private boolean warnedNotSingleplayer;
    private int tracyWaitTicks;
    private int runIndex = 1;
    private int totalRuns = 1;
    private int cooldownTicks;
    private String saveFolder;
    private String saveName;
    private volatile boolean freezeRequested;
    private boolean pauseOnLostFocusSaved;
    private boolean pauseOnLostFocusOverridden;
    private volatile boolean mobResetRequested;
    private volatile boolean seedRequested;
    private volatile int expectedMobs;
    private boolean mobResetRetried;
    private String mobSpec = "";
    private boolean worldHeld;
    private double originX, originY, originZ;
    private float originYaw, originPitch;
    private volatile int originDimension;
    private boolean originCaptured;
    private VSyncMode configuredVSyncMode;
    private double routeCenterX, routeCenterZ;
    private VSyncMode savedVSyncMode;
    private int warmupStride = 1;
    private int chunkWaitTicks;
    private int cleanSweepTicks;
    private int maxSections;
    private boolean deliveryLogged;
    private int meshDrainTicks;
    private int meshDrainStableTicks;
    private int spawnTicks;
    private int lastEntityCount;
    private int stableEntityPolls;
    private int lastAppliedIndex = -1;
    private double[] pathX = NO_PATH;
    private double[] pathZ = NO_PATH;
    private float[] pathYaw = NO_YAW;
    private byte[] pathLeg = NO_PHASE;
    private byte[] pathTurning = NO_PHASE;
    private long plotLeg;
    private long plotTurning;
    private int lastPhase = -1;
    private long phaseSection;
    private long legSection;
    private long plotRun;
    private long plotPhase;
    private long[] frameTimesNs = NO_FRAMES;
    private int frameCount;
    private boolean framesTruncated;
    private long lastFrameNs;
    private long runStartNs;

    private FlybyRunner() {
    }

    public static boolean entitiesFrozen() {
        return entitiesFrozen;
    }

    public static boolean blockTicksFrozen() {
        return blockTicksFrozen;
    }

    public static boolean worldChangesDiscarded() {
        return worldChangesDiscarded;
    }

    public static boolean pacingSuppressed() {
        return pacingSuppressed;
    }

    private static int totalSections() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || renderer.getRenderSectionManager() == null) return -1;
        return renderer.getRenderSectionManager().getTotalSections();
    }

    private static void keepGraphSearching() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || renderer.getRenderSectionManager() == null) return;
        renderer.getRenderSectionManager().markGraphDirty();
    }

    private static String describePending() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || renderer.getRenderSectionManager() == null) return "no renderer";
        return renderer.getRenderSectionManager().describePendingMeshUpdates();
    }

    private static int pendingMeshUpdates() {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null || renderer.getRenderSectionManager() == null) return -1;
        return renderer.getRenderSectionManager().getPendingMeshUpdates();
    }

    public static boolean cannotRun() {
        return Minecraft.getMinecraft().getIntegratedServer() == null;
    }

    private static int wrapDegrees(float degrees) {
        final int wrapped = Math.round(degrees) % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }

    public void startFromProperties() {
        final String id = SystemProperties.FLYBY_ROUTE;
        if (id == null || id.isEmpty()) return;

        final FlybyRoute configured = FlybyRoute.byId(id);
        if (configured == null) {
            LOGGER.error("Unknown flyby route '{}', expected one of {}", id, FlybyRoute.ids());
            return;
        }

        this.startedFromProperties = true;
        this.start(configured, SystemProperties.FLYBY_LENGTH, SystemProperties.FLYBY_WARMUP_TICKS, SystemProperties.FLYBY_SPEED, SystemProperties.FLYBY_MOBS);
        this.captureEveryRun = this.capture.isConfigured();
        this.waitForTracy = SystemProperties.FLYBY_WAIT_FOR_TRACY || this.captureEveryRun;
        this.waitForFocus = SystemProperties.FLYBY_WAIT_FOR_FOCUS;
        this.exitWhenDone = SystemProperties.FLYBY_EXIT_WHEN_DONE;
        this.totalRuns = Math.max(1, SystemProperties.FLYBY_RUNS);
        if (!this.propertiesLogged) {
            this.propertiesLogged = true;
            LOGGER.info("Started from properties: route={} warmup={} length={} {} ({} ticks) runs={} waitForTracy={} waitForFocus={} exitWhenDone={} captureEveryRun={}",
                configured.id(), this.warmupTicks, this.runLength, configured.lengthUnit(), this.runTicks,
                this.totalRuns, this.waitForTracy, this.waitForFocus, this.exitWhenDone, this.captureEveryRun);
        }
    }

    public void start(FlybyRoute route, int length, int warmupTicks, double speed, String mobSpec) {
        this.route = route;
        this.mobSpec = mobSpec == null ? "" : mobSpec;
        this.speed = speed;
        this.runLength = length > 0 ? length : route.defaultLength();
        this.runTicks = route.toTicks(this.runLength, speed);
        this.warmupTicks = Math.max(0, warmupTicks);
        this.tick = 0;
        this.frameCount = 0;
        this.framesTruncated = false;
        this.waitForTracy = false;
        this.captureEveryRun = false;
        this.tracyWaitTicks = 0;
        this.waitForFocus = false;
        this.exitWhenDone = false;
        this.chunkWaitTicks = 0;
        this.cleanSweepTicks = 0;
        this.maxSections = 0;
        this.chunksEnteringWorld.set(0);
        this.chunksReadFromDisk.set(0);
        this.lastAppliedIndex = -1;
        this.runIndex = 1;
        this.totalRuns = 1;
        this.originCaptured = false;
        this.spawnTicks = 0;
        this.lastEntityCount = -1;
        this.stableEntityPolls = 0;
        this.mobResetRequested = false;
        this.seedRequested = false;
        this.worldHeld = false;
        entitiesFrozen = false;
        blockTicksFrozen = false;
        pacingSuppressed = false;
        this.setState(State.WAITING);
    }

    private void setState(State next) {
        if (this.state == next) return;

        this.state = next;
        this.applyCaptureSuppression(next);
        if (next == State.COOLDOWN || next == State.EXITING || next == State.DONE) this.capture.stop();
        Tracy.sectionLeave(this.phaseSection);
        this.phaseSection = 0L;

        if (!Tracy.ENABLED) return;

        if (this.plotRun == 0L) {
            this.plotRun = Tracy.plotHandle("flyby.run");
            this.plotPhase = Tracy.plotHandle("flyby.phase");
        }
        Tracy.plotInt(this.plotRun, this.runIndex);
        Tracy.plotInt(this.plotPhase, next.ordinal());

        if (next == State.IDLE || next == State.DONE) return;
        this.phaseSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, this.phaseLabel(next));
    }

    private void applyCaptureSuppression(State next) {
        if (!SystemProperties.FLYBY_MEASURED_WINDOW_ONLY) return;

        final boolean benchmarking = next != State.IDLE && next != State.DONE;
        CaptureGate.suppressed = benchmarking && next != State.RUNNING;
        CaptureGate.refresh();
    }

    private String phaseLabel(State phase) {
        final String name = phase.name().toLowerCase(Locale.ROOT);
        final String where = this.totalRuns > 1 ? this.runIndex + "/" + this.totalRuns + " " : "";
        return phase == State.RUNNING && this.route != null
            ? "flyby " + where + name + " " + this.route.id()
            : "flyby " + where + name;
    }

    public boolean isActive() {
        return this.state != State.IDLE && this.state != State.DONE;
    }

    public String describe() {
        if (this.route == null) return "idle";
        return this.route.id() + " " + this.state + " tick " + this.tick + "/" + this.runTicks;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == State.IDLE || this.state == State.DONE) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (this.state == State.EXITING) {
            if (++this.tick >= EXIT_TICKS) {
                this.state = State.DONE;
                this.exitGame(mc);
            }
            return;
        }

        final EntityClientPlayerMP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            if (this.state == State.COOLDOWN) {
                this.tickCooldown(mc);
                return;
            }
            if (this.state != State.WAITING) {
                LOGGER.warn("The world went away mid-run!");
                this.cancel();
                if (this.startedFromProperties) {
                    final int resumeAt = this.runIndex;
                    final boolean keepOrigin = this.originCaptured;
                    this.startFromProperties();
                    this.runIndex = Math.min(resumeAt, this.totalRuns);
                    this.originCaptured = keepOrigin;
                    LOGGER.info("We stopped at run {}/{}, load a world and attach the profiler again to continue",
                        this.runIndex, this.totalRuns);
                }
            }
            return;
        }

        switch (this.state) {
            case WAITING -> {
                if (cannotRun()) {
                    if (!this.warnedNotSingleplayer) {
                        this.warnedNotSingleplayer = true;
                        LOGGER.error("Needs an integrated server! aka Singleplayer!!");
                    }
                    return;
                }
                this.holdWorld();
                if (this.captureEveryRun) this.capture.start(this.route.id(), this.runIndex, this.totalRuns);
                if (this.waitForTracy && !Tracy.isConnected()) {
                    this.warnTracyNotConnected(player);
                    return;
                }
                this.tracyWaitTicks = 0;
                if (this.waitForFocus && !Display.isActive()) return;
                this.begin(mc, player);
            }
            case LOADING -> {
                this.applyPosition(player, this.sweepIndex());
                keepGraphSearching();
                this.logLoadProgress(mc, "loading", this.chunkWaitTicks);
                this.chunkWaitTicks++;
                if (this.chunkWaitDone()) {
                    this.tick = 0;
                    this.beginWarmup();
                }
            }
            case WARMUP -> {
                this.applyPosition(player, this.tick * this.warmupStride);
                keepGraphSearching();
                this.logLoadProgress(mc, "warmup", this.tick);
                if (++this.tick >= this.warmupTicks) {
                    this.tick = 0;
                    this.beginMeshWait();
                }
            }
            case MESH_WAIT -> {
                this.applyPosition(player, 0);
                keepGraphSearching();
                this.logLoadProgress(mc, "meshing", this.meshDrainTicks);
                this.meshDrainTicks++;
                if (this.meshWaitDone()) {
                    this.tick = 0;
                    this.beginSpawnWait();
                }
            }
            case SPAWNING -> {
                this.applyPosition(player, 0);
                this.spawnTicks++;
                if (this.spawnTicks % SPAWN_POLL_TICKS == 0 && this.spawnWaitDone(mc)) {
                    this.tick = 0;
                    this.beginMeasuring();
                }
            }
            case RUNNING -> {
                this.applyPosition(player, this.tick);
                this.emitPhase(this.tick);
                if (++this.tick > this.runTicks) {
                    this.finish(mc, player);
                }
            }
            case SETTLE -> {
                this.holdPosition(player);
                if (++this.tick >= SETTLE_TICKS) {
                    this.teardown(mc, player);
                }
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world != null && !event.world.isRemote) this.chunksEnteringWorld.incrementAndGet();
    }

    @SubscribeEvent
    public void onChunkReadFromDisk(ChunkDataEvent.Load event) {
        this.chunksReadFromDisk.incrementAndGet();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state != State.RUNNING) return;

        final long now = System.nanoTime();
        if (this.lastFrameNs != 0L) {
            if (this.frameCount < this.frameTimesNs.length) {
                this.frameTimesNs[this.frameCount++] = now - this.lastFrameNs;
            } else {
                this.framesTruncated = true;
            }
        }
        this.lastFrameNs = now;
    }

    private void captureOrigin(EntityClientPlayerMP player) {
        final int dimension = player.worldObj.provider.dimensionId;
        if (this.originCaptured) {
            if (dimension != this.originDimension) {
                LOGGER.error("Rejoined in dimension {} but the set started in {}??",
                    dimension, this.originDimension);
            }
            return;
        }

        this.originX = player.posX;
        this.originY = player.posY;
        this.originZ = player.posZ;
        this.originPitch = player.rotationPitch;
        this.originDimension = dimension;

        this.originYaw = Math.round(player.rotationYaw / 90.0F) * 90.0F;
        if (this.originYaw != player.rotationYaw) {
            LOGGER.info("Snapped heading {} -> {}", player.rotationYaw, this.originYaw);
        }
        this.originCaptured = true;
    }

    private void begin(Minecraft mc, EntityClientPlayerMP player) {
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        if (!this.pauseOnLostFocusOverridden) {
            this.pauseOnLostFocusSaved = mc.gameSettings.pauseOnLostFocus;
            this.pauseOnLostFocusOverridden = true;
            mc.gameSettings.pauseOnLostFocus = false;
        }

        this.captureOrigin(player);

        this.buildPath();
        this.holdWorld();
        this.disablePacing();
        this.reloadRenderer();

        this.warmupStride = this.warmupTicks > 0
            ? Math.max(1, (this.pathX.length + this.warmupTicks - 1) / this.warmupTicks)
            : 1;

        this.tick = 0;
        this.beginChunkWait();
    }

    private void buildPath() {
        final int n = this.runTicks + 1;
        this.pathX = new double[n];
        this.pathZ = new double[n];
        this.pathYaw = new float[n];
        this.pathLeg = new byte[n];
        this.pathTurning = new byte[n];

        double x = this.originX;
        double z = this.originZ;
        double yaw = this.originYaw;

        final double effectiveSpeed = this.route.speedOr(this.speed);
        final int legTicks = this.route.kind() == FlybyRoute.Kind.SQUARE ? this.route.legTicks(this.runLength, this.speed) : 0;
        final int turnTicks = this.route.kind() == FlybyRoute.Kind.SQUARE ? this.route.turnTicks() : 0;
        final int segment = legTicks + turnTicks;

        final double cornerStep = turnTicks > 0 ? FlybyRoute.CIRCUIT_TURN_DEGREES / turnTicks : 0.0D;
        final double sweepStep = this.runTicks > 0 ? (double) this.runLength / this.runTicks : 0.0D;

        for (int i = 0; i < n; i++) {
            this.pathX[i] = x;
            this.pathZ[i] = z;
            this.pathYaw[i] = (float) yaw;

            double advance = 0.0D;
            double turn = 0.0D;

            switch (this.route.kind()) {
                case STRAIGHT -> advance = effectiveSpeed;
                case ROTATE -> turn = sweepStep;
                case SQUARE -> {
                    if (segment > 0 && (i % segment) < legTicks) {
                        advance = effectiveSpeed;
                    } else {
                        turn = cornerStep;
                        this.pathTurning[i] = 1;
                    }
                    this.pathLeg[i] = (byte) Math.min(FlybyRoute.CIRCUIT_LEGS - 1, segment > 0 ? i / segment : 0);
                }
                case STILL -> { }
            }
            if (this.route.kind() == FlybyRoute.Kind.ROTATE) this.pathTurning[i] = 1;

            if (advance != 0.0D) {
                final double yawRad = Math.toRadians(yaw);
                x += -Math.sin(yawRad) * advance;
                z += Math.cos(yawRad) * advance;
            }
            yaw += turn;
        }

        double sumX = 0.0D;
        double sumZ = 0.0D;
        for (int i = 0; i < n; i++) {
            sumX += this.pathX[i];
            sumZ += this.pathZ[i];
        }
        this.routeCenterX = sumX / n;
        this.routeCenterZ = sumZ / n;
    }

    private void emitPhase(int index) {
        if (!Tracy.ENABLED || index >= this.pathLeg.length) return;

        final int leg = this.pathLeg[index];
        final int turning = this.pathTurning[index];
        Tracy.plotInt(this.plotLeg, leg);
        Tracy.plotInt(this.plotTurning, turning);

        final int phase = (leg << 1) | turning;
        if (phase != this.lastPhase) {
            this.lastPhase = phase;
            final String label = "flyby " + (turning != 0 ? "turn" : "leg") + " " + leg;
            Tracy.message(label);
            Tracy.sectionLeave(this.legSection);
            this.legSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, label);
        }
    }

    private void beginChunkWait() {
        this.lastAppliedIndex = -1;
        this.chunkWaitTicks = 0;
        this.cleanSweepTicks = 0;
        this.deliveryLogged = false;
        this.maxSections = 0;
        if (SystemProperties.FLYBY_CHUNK_WAIT_TIMEOUT_TICKS <= 0) {
            this.beginWarmup();
            return;
        }
        final int queued = serverQueuedChunks();
        final int needed = queued > 0 ? queued / CHUNKS_SENT_PER_TICK + SETTLE_AFTER_ARRIVAL_TICKS : 0;
        if (needed > SystemProperties.FLYBY_CHUNK_WAIT_TIMEOUT_TICKS) {
            LOGGER.warn("{} chunks left to send at {}/tick needs about {} ticks with the settle, but chunkWaitTimeoutTicks is {}",
                queued, CHUNKS_SENT_PER_TICK, needed, SystemProperties.FLYBY_CHUNK_WAIT_TIMEOUT_TICKS);
        }
        this.setState(State.LOADING);
    }

    private void beginWarmup() {
        if (this.warmupTicks <= 0) {
            this.beginMeshWait();
            return;
        }
        this.tick = 0;
        this.lastAppliedIndex = -1;
        this.setState(State.WARMUP);
    }

    private void beginMeshWait() {
        this.meshDrainTicks = 0;
        this.meshDrainStableTicks = 0;
        this.lastAppliedIndex = -1;
        this.setState(State.MESH_WAIT);
    }

    private boolean meshWaitDone() {
        final int pending = pendingMeshUpdates();
        if (pending != 0) {
            this.meshDrainStableTicks = 0;
        } else if (++this.meshDrainStableTicks >= MESH_DRAIN_STABLE_TICKS) {
            LOGGER.info("Mesh queue empty for {} ticks after {} ticks of draining ({} sections)",
                MESH_DRAIN_STABLE_TICKS, this.meshDrainTicks, totalSections());
            return true;
        }

        if (this.meshDrainTicks >= MESH_DRAIN_TIMEOUT_TICKS) {
            LOGGER.warn("Mesh queue still busy after {} ticks [{}]. Running now...",
                this.meshDrainTicks, describePending());
            return true;
        }
        return false;
    }

    private void abandonRun() {
        entitiesFrozen = false;
        blockTicksFrozen = false;
        this.restorePacing();

        if (this.runIndex < this.totalRuns && this.beginCooldown(Minecraft.getMinecraft())) return;
        this.setState(this.exitWhenDone ? State.EXITING : State.DONE);
    }

    private int sweepIndex() {
        final int n = this.pathX.length;
        if (n == 0) return 0;
        if (!SystemProperties.FLYBY_FLY_ROUTE_WHILE_LOADING) return 0;
        return (this.chunkWaitTicks * this.warmupStride) % n;
    }

    private boolean chunkWaitDone() {
        final int pending = pendingMeshUpdates();
        final int sections = totalSections();
        final int queued = serverQueuedChunks();
        final int growthTolerance = Math.max(MIN_SECTION_GROWTH, this.maxSections / 50);

        final boolean parked = !SystemProperties.FLYBY_FLY_ROUTE_WHILE_LOADING;
        final boolean stillDelivering = parked && queued != 0;
        if (parked && !stillDelivering && !this.deliveryLogged) {
            this.deliveryLogged = true;
            LOGGER.info("Server finished sending chunks after {} ticks ({} sections, {} pending)",
                this.chunkWaitTicks, sections, pending);
        }

        if (sections > this.maxSections + growthTolerance) {
            if (this.cleanSweepTicks > 0) {
                LOGGER.info("Sections grew to {} (was {}, tolerance {}) {} ticks into the settle, restarting it",
                    sections, this.maxSections, growthTolerance, this.cleanSweepTicks);
            }
            this.maxSections = sections;
            this.cleanSweepTicks = 0;
        } else if (sections <= 0 || stillDelivering) {
            this.cleanSweepTicks = 0;
        } else if (++this.cleanSweepTicks >= SETTLE_AFTER_ARRIVAL_TICKS) {
            final int entering = this.chunksEnteringWorld.get();
            final int generated = entering - this.chunksReadFromDisk.get();
            LOGGER.info("Sections plateaued at {} for {} ticks ({} pending, {} queued server-side) after {} ticks of loading",
                this.maxSections, SETTLE_AFTER_ARRIVAL_TICKS, pending, queued, this.chunkWaitTicks);
            if (generated > 0) {
                LOGGER.warn("{} of {} server chunks were generated.",
                    generated, entering);
            }
            return true;
        }

        if (this.chunkWaitTicks >= SystemProperties.FLYBY_CHUNK_WAIT_TIMEOUT_TICKS) {
            if (sections <= 0) {
                LOGGER.error("No terrain sections after {} ticks, the world never rendered! Skipping run {}/{}.",
                    this.chunkWaitTicks, this.runIndex, this.totalRuns);
                this.abandonRun();
                return false;
            } else if (queued > 0) {
                LOGGER.warn("Gave up waiting after {} ticks with {} chunks still queued ({}/tick, so about {} ticks short). Raise -Dangelica.flyby.chunkWaitTimeoutTicks!",
                    this.chunkWaitTicks, queued, CHUNKS_SENT_PER_TICK, queued / CHUNKS_SENT_PER_TICK);
            } else {
                LOGGER.warn("Gave up waiting after {} ticks - {} sections (high-water {}), {} pending, {} queued server-side, {} generated, steady {}/{} ticks [{}]",
                    this.chunkWaitTicks, sections, this.maxSections, pending, queued,
                    this.chunksEnteringWorld.get() - this.chunksReadFromDisk.get(),
                    this.cleanSweepTicks, SETTLE_AFTER_ARRIVAL_TICKS, describePending());
            }
            return true;
        }
        return false;
    }

    private void warnTracyNotConnected(EntityClientPlayerMP player) {
        final int elapsed = this.tracyWaitTicks++;
        if (elapsed < TRACY_WAIT_WARN_TICKS) return;
        if ((elapsed - TRACY_WAIT_WARN_TICKS) % TRACY_WAIT_WARN_REPEAT_TICKS != 0) return;

        final int seconds = elapsed / 20;
        LOGGER.warn("Waited {}s for Tracy and will not start until the profiler connects", seconds);
    }

    private void logLoadProgress(Minecraft mc, String phase, int elapsed) {
        if (elapsed == 0 || elapsed % LOAD_PROGRESS_TICKS != 0) return;

        final String chunks = mc.theWorld != null && mc.theWorld.getChunkProvider() != null
            ? mc.theWorld.getChunkProvider().makeString()
            : "no chunk provider";
        final int entering = this.chunksEnteringWorld.get();
        final int fromDisk = this.chunksReadFromDisk.get();
        LOGGER.info("{} +{}: sections={} pending={} entities={} chunks[{}] {} serverChunks[loaded={} generated={}]",
            phase, elapsed, totalSections(), pendingMeshUpdates(),
            mc.theWorld != null ? mc.theWorld.loadedEntityList.size() : -1,
            chunks, describeServerSide(mc), fromDisk, entering - fromDisk);
    }

    private String describeServerSide(Minecraft mc) {
        try {
            final MinecraftServer server = mc.getIntegratedServer();
            if (server == null) return "server=none";

            final WorldServer world = DimensionManager.getWorld(this.originDimension);
            if (world == null) return "server=no dim " + this.originDimension;

            final int players = world.playerEntities.size();
            final String queued = players > 0 && world.playerEntities.getFirst() instanceof EntityPlayerMP mp
                ? String.valueOf(mp.loadedChunks.size())
                : "n/a";
            return "server[world=@" + Integer.toHexString(System.identityHashCode(world))
                + " players=" + players + " queuedChunks=" + queued + "]";
        } catch (RuntimeException e) {
            return "server=unreadable(" + e.getClass().getSimpleName() + ")";
        }
    }

    private int serverQueuedChunks() {
        try {
            final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
            if (server == null) return -1;

            final WorldServer world = DimensionManager.getWorld(this.originDimension);
            if (world == null || world.playerEntities.isEmpty()) return -1;
            return world.playerEntities.getFirst() instanceof EntityPlayerMP mp ? mp.loadedChunks.size() : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private void beginSpawnWait() {
        if (this.mobSpec.isEmpty()) {
            this.beginMeasuring();
            return;
        }
        this.spawnTicks = 0;
        this.lastEntityCount = -1;
        this.stableEntityPolls = 0;
        this.expectedMobs = 0;
        this.mobResetRetried = false;
        this.mobResetRequested = true;
        this.setState(State.SPAWNING);
    }

    private boolean spawnWaitDone(Minecraft mc) {
        final int count = mc.theWorld != null ? mc.theWorld.loadedEntityList.size() : -1;
        if (count == this.lastEntityCount) {
            this.stableEntityPolls++;
        } else {
            this.stableEntityPolls = 0;
            this.lastEntityCount = count;
        }

        if (this.mobResetRequested) return false;

        final boolean populated = count > 1;
        if (populated && this.stableEntityPolls >= SPAWN_STABLE_POLLS) {
            if (count < this.expectedMobs) {
                LOGGER.info("Population settled at {} of the {} spawned",
                    count, this.expectedMobs);
            } else {
                LOGGER.info("Population settled at {} entities (spawned {}) after {} ticks",
                    count, this.expectedMobs, this.spawnTicks);
            }
            return true;
        }

        if (!populated && !this.mobResetRetried && this.spawnTicks >= SPAWN_TIMEOUT_TICKS / 2) {
            this.mobResetRetried = true;
            this.mobResetRequested = true;
            this.stableEntityPolls = 0;
            this.lastEntityCount = -1;
            LOGGER.warn("Client holds {} entities but the server spawned {}, retrying the mob reset",
                count, this.expectedMobs);
            return false;
        }

        if (this.spawnTicks >= SPAWN_TIMEOUT_TICKS) {
            if (populated) {
                LOGGER.warn("Population still moving after {} ticks ({} entities)",
                    this.spawnTicks, count);
            } else {
                LOGGER.error("The client never received any of the {} spawned mobs?",
                    this.expectedMobs);
            }
            return true;
        }
        return false;
    }

    private void beginMeasuring() {
        this.setState(State.RUNNING);
        this.plotLeg = Tracy.plotHandle("flyby.leg");
        this.plotTurning = Tracy.plotHandle("flyby.turning");
        this.lastPhase = -1;
        this.lastAppliedIndex = -1;
        this.frameTimesNs = new long[Math.clamp(this.runTicks * 20L, 1024, MAX_RECORDED_FRAMES)];
        this.frameCount = 0;
        this.lastFrameNs = 0L;
        this.runStartNs = System.nanoTime();
        this.prepareMeasuredWindow();
        FramePacer.beginStats();
        final String run = this.describeRun();
        final String settings = FlybySettings.describe();
        final String tag = FlybySettings.tag(this.tagInput() + " " + settings);
        LOGGER.info("Run [{}] {}", tag, run);
        if (!tag.equals(this.loggedSettingsTag)) {
            this.loggedSettingsTag = tag;
            LOGGER.info("Settings [{}] {}", tag, settings);
        }
        if (Tracy.ENABLED) {
            Tracy.message("flyby start " + run + " settings=" + tag + " " + this.population(Minecraft.getMinecraft()));
        }
    }

    private void holdPosition(EntityClientPlayerMP player) {
        final int last = this.pathX.length - 1;
        if (last < 0) return;

        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.ySize = 0.0F;

        player.prevPosX = player.lastTickPosX = this.pathX[last];
        player.prevPosY = player.lastTickPosY = this.originY;
        player.prevPosZ = player.lastTickPosZ = this.pathZ[last];
        player.prevRotationYaw = player.rotationYaw = this.pathYaw[last];
        player.prevRotationYawHead = player.rotationYawHead = this.pathYaw[last];
        player.prevRotationPitch = player.rotationPitch = this.originPitch;
        player.setPosition(this.pathX[last], this.originY, this.pathZ[last]);
    }

    private void applyPosition(EntityClientPlayerMP player, int index) {
        final int i = Math.min(index, this.pathX.length - 1);
        final int prev = this.lastAppliedIndex < 0 ? i : Math.min(this.lastAppliedIndex, this.pathX.length - 1);
        this.lastAppliedIndex = i;

        final double x = this.pathX[i];
        final double z = this.pathZ[i];
        final double prevX = this.pathX[prev];
        final double prevZ = this.pathZ[prev];
        final float yaw = this.pathYaw[i];
        final float prevYaw = this.pathYaw[prev];

        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.ySize = 0.0F;

        player.prevPosX = player.lastTickPosX = prevX;
        player.prevPosY = player.lastTickPosY = this.originY;
        player.prevPosZ = player.lastTickPosZ = prevZ;

        player.prevRotationYaw = prevYaw;
        player.rotationYaw = yaw;
        player.prevRotationYawHead = prevYaw;
        player.rotationYawHead = yaw;
        player.prevRotationPitch = player.rotationPitch = this.originPitch;

        player.setPosition(x, this.originY, z);
    }

    private void holdWorld() {
        if (this.worldHeld) return;
        this.worldHeld = true;

        if (SystemProperties.FLYBY_FREEZE_ENTITIES) {
            entitiesFrozen = true;
        }
        blockTicksFrozen = SystemProperties.FLYBY_FREEZE_BLOCK_TICKS;
        this.freezeRequested = true;
    }

    private void prepareMeasuredWindow() {
        if (SystemProperties.FLYBY_SEED == 0L) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) mc.theWorld.rand.setSeed(SystemProperties.FLYBY_SEED);
        this.seedRequested = true;
    }

    private void reloadRenderer() {
        if (!SystemProperties.FLYBY_RELOAD_RENDERER) return;

        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null) {
            LOGGER.warn("No terrain renderer to reload, run starts from whatever state the session left");
            return;
        }
        renderer.requestReload();
    }

    private void disablePacing() {
        pacingSuppressed = true;

        final VSyncMode configured = ClientProxy.options().advanced.vsyncMode;
        this.configuredVSyncMode = configured;
        if (configured != null && configured.tearFree()) {
            this.savedVSyncMode = configured;
            GLStateManager.setVSyncMode(VSyncMode.OFF);
        }
    }

    private void restorePacing() {
        pacingSuppressed = false;

        if (this.savedVSyncMode != null) {
            GLStateManager.setVSyncMode(this.savedVSyncMode);
            this.savedVSyncMode = null;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        if (this.mobResetRequested) {
            this.resetMobs(server);
            this.mobResetRequested = false;
        }
        if (this.seedRequested) {
            this.seedRequested = false;
            this.seedWorlds(server);
        }
        if (!this.freezeRequested) return;
        this.freezeRequested = false;

        if (SystemProperties.FLYBY_DISCARD_WORLD_CHANGES) {
            this.discardWorldChanges(server);
        }

        int daylight = 0, spawning = 0, weather = 0, time = 0;
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            if (world.getGameRules().getGameRuleBooleanValue("doDaylightCycle")) {
                world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                daylight++;
            }
            if (world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) {
                world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
                spawning++;
            }
            if (world.getWorldInfo().isRaining() || world.getWorldInfo().isThundering()) {
                world.getWorldInfo().setRaining(false);
                world.getWorldInfo().setThundering(false);
                weather++;
            }
            world.getWorldInfo().setRainTime(Integer.MAX_VALUE);
            world.getWorldInfo().setThunderTime(Integer.MAX_VALUE);

            if (world.getWorldTime() % 24000L != SystemProperties.FLYBY_TIME_OF_DAY) {
                world.setWorldTime(SystemProperties.FLYBY_TIME_OF_DAY);
                time++;
            }
        }
        if (time + daylight + spawning + weather > 0) {
            LOGGER.info("World held: time frozen at {} in {} worlds, daylight off in {}, mob spawning off in {}, weather cleared in {}",
                SystemProperties.FLYBY_TIME_OF_DAY, time, daylight, spawning, weather);
        }
    }

    private void seedWorlds(MinecraftServer server) {
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            world.rand.setSeed(SystemProperties.FLYBY_SEED);
            ((WorldRandomTickAccessor) world).angelica$setUpdateLCG((int) SystemProperties.FLYBY_SEED);
        }
    }

    private void discardWorldChanges(MinecraftServer server) {
        if (worldChangesDiscarded) return;

        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            try {
                world.saveAllChunks(true, null);
            } catch (MinecraftException e) {
                LOGGER.error("Could not flush {} before turning saving off!", world.provider.getDimensionName(), e);
            }
        }
        if (server.getConfigurationManager() != null) {
            server.getConfigurationManager().saveAllPlayerData();
        }
        worldChangesDiscarded = true;
        final EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW
                + "[Angelica] World saving is off for the rest of this session."));
        }
    }

    private void resetMobs(MinecraftServer server) {
        if (SystemProperties.FLYBY_SEED != 0L) {
            this.seedWorlds(server);
        }

        int removed = 0;
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
                if (entity instanceof EntityPlayer) continue;
                entity.setDead();
                removed++;
            }
        }

        final WorldServer world = server.worldServerForDimension(this.originDimension);
        if (world == null) {
            LOGGER.error("Mobs: dimension {} is not loaded, nothing spawned", this.originDimension);
            this.expectedMobs = 0;
            return;
        }
        int spawned = 0;
        for (String group : this.mobSpec.split(",")) {
            spawned += this.spawnMobGroup(world, group.trim());
        }
        this.expectedMobs = spawned;
        final String what = "Mobs: killed {} entities, spawned {} from '{}' around route centre {} {} {}";
        if (worldChangesDiscarded) {
            LOGGER.info(what, removed, spawned, this.mobSpec, this.routeCenterX, this.originY, this.routeCenterZ);
        } else {
            LOGGER.warn(what, removed, spawned, this.mobSpec, this.routeCenterX, this.originY, this.routeCenterZ);
        }
    }

    private int spawnMobGroup(WorldServer world, String group) {
        final int star = group.indexOf('*');
        final int at = group.indexOf('@');
        if (star <= 0 || at <= star) {
            LOGGER.error("Mobs: cannot parse '{}', expected Name*count@radius", group);
            return 0;
        }

        final String name = group.substring(0, star);
        final int count;
        final double radius;
        try {
            count = Integer.parseInt(group.substring(star + 1, at).trim());
            radius = Double.parseDouble(group.substring(at + 1).trim());
        } catch (NumberFormatException e) {
            LOGGER.error("Mobs: cannot parse '{}', expected Name*count@radius", group);
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final Entity entity = EntityList.createEntityByName(name, world);
            if (entity == null) {
                LOGGER.error("Mobs: unknown entity '{}'", name);
                return spawned;
            }
            final double angle = 2.0D * Math.PI * i / count;
            entity.setLocationAndAngles(
                this.routeCenterX + Math.cos(angle) * radius,
                this.originY,
                this.routeCenterZ + Math.sin(angle) * radius,
                (float) Math.toDegrees(angle), 0.0F);
            world.spawnEntityInWorld(entity);
            spawned++;
        }
        return spawned;
    }

    private String tagInput() {
        return "route=" + this.route.id()
            + " length=" + this.runLength
            + " speed=" + this.route.speedOr(this.speed)
            + " ticks=" + this.runTicks
            + " warmup=" + this.warmupTicks
            + " flyRouteWhileLoading=" + SystemProperties.FLYBY_FLY_ROUTE_WHILE_LOADING
            + " origin=" + Math.round(this.originX) + "," + Math.round(this.originY) + "," + Math.round(this.originZ)
            + " yaw=" + wrapDegrees(this.originYaw) + " pitch=" + Math.round(this.originPitch)
            + " dim=" + this.originDimension
            + " seed=" + SystemProperties.FLYBY_SEED
            + " freezeEntities=" + SystemProperties.FLYBY_FREEZE_ENTITIES
            + " freezeBlockTicks=" + SystemProperties.FLYBY_FREEZE_BLOCK_TICKS
            + " discardWorldChanges=" + SystemProperties.FLYBY_DISCARD_WORLD_CHANGES
            + " reloadRenderer=" + SystemProperties.FLYBY_RELOAD_RENDERER
            + " mobs=" + this.mobSpec
            + " vsync=" + this.configuredVSyncMode;
    }

    private String describeRun() {
        return "run=" + this.runIndex + "/" + this.totalRuns
            + " route=" + this.route.id()
            + " length=" + this.runLength + this.route.lengthUnit()
            + " speed=" + this.route.speedOr(this.speed) + "b/t"
            + " ticks=" + this.runTicks
            + " warmup=" + this.warmupTicks
            + " chunkWait=" + this.chunkWaitTicks
            + " meshWait=" + this.meshDrainTicks
            + " queuedChunks=" + serverQueuedChunks()
            + " flyRouteWhileLoading=" + SystemProperties.FLYBY_FLY_ROUTE_WHILE_LOADING
            + String.format(" origin=%.3f,%.3f,%.3f", this.originX, this.originY, this.originZ)
            + " yaw=" + this.originYaw + " pitch=" + this.originPitch
            + " dim=" + this.originDimension
            + " seed=" + (SystemProperties.FLYBY_SEED == 0L ? "off" : Long.toHexString(SystemProperties.FLYBY_SEED))
            + " freezeEntities=" + SystemProperties.FLYBY_FREEZE_ENTITIES
            + " freezeBlockTicks=" + SystemProperties.FLYBY_FREEZE_BLOCK_TICKS
            + " discardWorldChanges=" + SystemProperties.FLYBY_DISCARD_WORLD_CHANGES
            + " reloadRenderer=" + SystemProperties.FLYBY_RELOAD_RENDERER
            + " mobs=" + (this.mobSpec.isEmpty() ? "none" : this.mobSpec)
            + " vsync=" + this.configuredVSyncMode + "/" + GLStateManager.getEffectiveVSyncMode()
            + " cap=" + Minecraft.getMinecraft().gameSettings.limitFramerate;
    }

    private String population(Minecraft mc) {
        final List<?> entities = mc.theWorld != null ? mc.theWorld.loadedEntityList : null;
        return "entities=" + (entities != null ? entities.size() : -1)
            + " particles=" + (mc.effectRenderer != null ? mc.effectRenderer.getStatistics() : "?");
    }

    private void finish(Minecraft mc, EntityClientPlayerMP player) {
        final long elapsedNs = System.nanoTime() - this.runStartNs;
        Tracy.sectionLeave(this.legSection);
        this.legSection = 0L;
        final String population = this.population(mc);
        if (Tracy.ENABLED)
            Tracy.message("flyby end route=" + this.route.id() + " frames=" + this.frameCount + " " + population);

        this.setState(State.SETTLE);
        this.tick = 0;

        final String summary = this.summarise(elapsedNs, player) + ", " + population;
        LOGGER.info(summary);
        FramePacer.endStats();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Flyby " + summary));
        }

    }

    private void teardown(Minecraft mc, EntityClientPlayerMP player) {
        Tracy.message("flyby teardown");
        entitiesFrozen = false;
        blockTicksFrozen = false;
        this.restorePacing();

        this.returnToOrigin(player);
        this.restorePauseOnLostFocus(mc);

        this.tick = 0;
        if (this.runIndex < this.totalRuns && this.beginCooldown(mc)) return;
        this.setState(this.exitWhenDone ? State.EXITING : State.DONE);
    }

    private boolean beginCooldown(Minecraft mc) {
        final MinecraftServer server = mc.getIntegratedServer();
        if (server == null) {
            LOGGER.warn("No integrated server to rejoin, stopping after run {}/{}", this.runIndex, this.totalRuns);
            return false;
        }

        this.saveFolder = server.getFolderName();
        this.saveName = server.getWorldName();
        LOGGER.info("Run {}/{} done, leaving '{}' for {} ticks",
            this.runIndex, this.totalRuns, this.saveFolder, SystemProperties.FLYBY_COOLDOWN_TICKS);
        Tracy.message("flyby cooldown after run " + this.runIndex + "/" + this.totalRuns);

        if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());

        this.cooldownTicks = 0;
        this.setState(State.COOLDOWN);
        return true;
    }

    private void tickCooldown(Minecraft mc) {
        if (++this.cooldownTicks < SystemProperties.FLYBY_COOLDOWN_TICKS) return;

        final int next = this.runIndex + 1;
        LOGGER.info("Cooled for {} ticks, rejoining '{}' for run {}/{}",
            this.cooldownTicks, this.saveFolder, next, this.totalRuns);
        mc.launchIntegratedServer(this.saveFolder, this.saveName, null);
        this.armNextRun(next);
    }

    private void armNextRun(int next) {
        final boolean keepOrigin = this.originCaptured;
        this.startFromProperties();
        this.runIndex = next;
        this.originCaptured = keepOrigin;
        this.relabelPhase();
    }

    private void relabelPhase() {
        if (!Tracy.ENABLED) return;

        Tracy.sectionLeave(this.phaseSection);
        this.phaseSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, this.phaseLabel(this.state));
        Tracy.plotInt(this.plotRun, this.runIndex);
    }

    private void exitGame(Minecraft mc) {
        LOGGER.info("Flyby complete, shutting down.");
        if (mc.theWorld != null) {
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            mc.displayGuiScreen(new GuiMainMenu());
        }
        mc.shutdown();
    }

    private void restorePauseOnLostFocus(Minecraft mc) {
        if (!this.pauseOnLostFocusOverridden) return;
        mc.gameSettings.pauseOnLostFocus = this.pauseOnLostFocusSaved;
        this.pauseOnLostFocusOverridden = false;
    }

    private void returnToOrigin(EntityClientPlayerMP player) {
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.ySize = 0.0F;
        player.setPositionAndRotation(this.originX, this.originY, this.originZ, this.originYaw, this.originPitch);
        player.lastTickPosX = this.originX;
        player.lastTickPosY = this.originY;
        player.lastTickPosZ = this.originZ;
    }

    private String summarise(long elapsedNs, EntityClientPlayerMP player) {
        final StringBuilder sb = new StringBuilder(192);
        sb.append(this.route.id())
            .append(": ").append(this.frameCount).append(" frames in ")
            .append(String.format("%.2fs", elapsedNs / 1_000_000_000.0D));

        if (this.frameCount > 0) {
            final long[] sorted = Arrays.copyOf(this.frameTimesNs, this.frameCount);
            Arrays.sort(sorted);
            long total = 0L;
            for (long ns : sorted) total += ns;
            sb.append(String.format(", avg %.2fms, p50 %.2fms, p99 %.2fms, max %.2fms",
                total / (double) this.frameCount / 1e6D,
                sorted[this.frameCount / 2] / 1e6D,
                sorted[Math.min(this.frameCount - 1, (int) (this.frameCount * 0.99D))] / 1e6D,
                sorted[this.frameCount - 1] / 1e6D));
        }
        if (this.framesTruncated) sb.append(" (frame samples truncated)");

        sb.append(String.format(", end position %.6f %.6f %.6f", player.posX, player.posY, player.posZ));
        return sb.toString();
    }

    public void cancel() {
        if (this.isActive()) {
            LOGGER.info("Cancelled");
            Tracy.sectionLeave(this.legSection);
            this.legSection = 0L;
            this.setState(State.DONE);
            entitiesFrozen = false;
            blockTicksFrozen = false;
            this.restorePacing();
            FramePacer.endStats();
            this.restorePauseOnLostFocus(Minecraft.getMinecraft());
        }
    }

    public boolean startedFromProperties() {
        return this.startedFromProperties;
    }

    private enum State {IDLE, WAITING, LOADING, WARMUP, MESH_WAIT, SPAWNING, RUNNING, SETTLE, COOLDOWN, EXITING, DONE}
}
