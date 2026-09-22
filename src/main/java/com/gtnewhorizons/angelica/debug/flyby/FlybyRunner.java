package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.config.SystemProperties.FlybyPacing;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder.StopResult;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder.StopStatus;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.FpsReducer;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Flies a deterministic camera path */
public final class FlybyRunner {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final long[] NO_FRAMES = new long[0];
    private static final double[] NO_PATH = new double[0];
    private static final float[] NO_YAW = new float[0];
    private static final byte[] NO_PHASE = new byte[0];
    private static final String[] NO_COMMANDS = new String[0];
    private static final int MAX_RECORDED_FRAMES = 200_000;

    public static final FlybyRunner INSTANCE = new FlybyRunner();

    private enum State { IDLE, WAITING, PREPARING, WARMUP, RUNNING, SETTLE, EXITING, DONE }

    private static final int EXIT_TICKS = 20;
    private static final int SETTLE_TICKS = 40;

    private State state = State.IDLE;
    private FlybyRoute route;
    private int warmupTicks;
    private int runLength;
    private double speed;
    private int runTicks;
    private int tick;
    private boolean waitForTracy;
    private boolean waitForFocus;
    private boolean exitWhenDone;
    private boolean startedFromProperties;
    private boolean armed;
    private int waitTicks;
    private boolean jfr;
    private long recordingId;
    private final AtomicReference<FlybyRequest> pendingRequest = new AtomicReference<>();
    private FlybyRequest activeRequest;
    private String[] sceneCommands = NO_COMMANDS;
    private volatile boolean sceneClearRequested;
    private int sceneSpawnTotal;
    private boolean pauseOnLostFocusSaved;
    private boolean pauseOnLostFocusOverridden;
    private boolean vsyncOverridden;

    private static volatile boolean sceneGuarded;
    private static volatile boolean worldChangesDiscarded;

    private double parkedX, parkedY, parkedZ;
    private float parkedYaw, parkedPitch;
    private double originX, originZ;
    private float originYaw;
    private FlybyOrigin fixedOrigin;
    private float eyeOffset;
    private double flightY;

    private double[] pathX = NO_PATH;
    private double[] pathZ = NO_PATH;
    private float[] pathYaw = NO_YAW;
    private byte[] pathLeg = NO_PHASE;
    private byte[] pathTurning = NO_PHASE;

    private long plotLeg;
    private long plotTurning;
    private int lastPhase = -1;

    private long warmupSection;
    private long runSection;
    private long legSection;

    private long[] frameTimesNs = NO_FRAMES;
    private int frameCount;
    private final int[] phaseStartFrame = new int[64];
    private final int[] phaseKey = new int[64];
    private int phaseCount;
    private boolean framesTruncated;
    private long lastFrameNs;
    private long runStartNs;

    private FlybyRunner() {}

    public static boolean sceneGuarded() {
        return sceneGuarded;
    }

    public static boolean worldChangesDiscarded() {
        return worldChangesDiscarded;
    }

    public void startFromProperties() {
        final String id = SystemProperties.FLYBY_ROUTE;
        if (id == null || id.isEmpty()) return;

        final FlybyRoute configured = FlybyRoute.byId(id);
        if (configured == null) {
            LOGGER.error("Unknown flyby route '{}', expected one of {}", id, FlybyRoute.ids());
            return;
        }

        final FlybyOrigin origin;
        try {
            origin = FlybyOrigin.parse(SystemProperties.FLYBY_ORIGIN);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid flyby origin '{}', expected x,z[,yaw]", SystemProperties.FLYBY_ORIGIN);
            return;
        }

        this.startedFromProperties = true;
        this.start(configured, SystemProperties.FLYBY_LENGTH, SystemProperties.FLYBY_WARMUP_TICKS, SystemProperties.FLYBY_SPEED);
        this.fixedOrigin = origin;
        this.waitForTracy = SystemProperties.FLYBY_WAIT_FOR_TRACY;
        if (this.waitForTracy && !Tracy.ENABLED) {
            LOGGER.warn("Flyby: waitForTracy requested but Tracy is disabled in this build");
            this.waitForTracy = false;
        }
        this.waitForFocus = SystemProperties.FLYBY_WAIT_FOR_FOCUS;
        this.exitWhenDone = SystemProperties.FLYBY_EXIT_WHEN_DONE;
        this.jfr = SystemProperties.FLYBY_JFR;
        if (!SystemProperties.FLYBY_COMMANDS.isEmpty()) {
            this.sceneCommands = FlybyScene.load(SystemProperties.FLYBY_COMMANDS);
            LOGGER.info("Flyby scene '{}': {} commands", SystemProperties.FLYBY_COMMANDS, this.sceneCommands.length);
        }
        LOGGER.info("Flyby started from properties: route={} warmup={} length={} {} ({} ticks) waitForTracy={} waitForFocus={} exitWhenDone={} jfr={}",
            configured.id(), this.warmupTicks, this.runLength, configured.lengthUnit(), this.runTicks,
            this.waitForTracy, this.waitForFocus, this.exitWhenDone, this.jfr);
    }

    public void start(FlybyRoute route, int length, int warmupTicks, double speed) {
        this.route = route;
        this.speed = speed;
        this.runLength = length > 0 ? length : route.defaultLength();
        this.runTicks = route.toTicks(this.runLength, speed);
        this.warmupTicks = Math.max(0, warmupTicks);
        this.tick = 0;
        this.frameCount = 0;
        this.framesTruncated = false;
        this.waitForTracy = false;
        this.waitForFocus = false;
        this.exitWhenDone = false;
        this.jfr = false;
        this.armed = false;
        this.fixedOrigin = null;
        sceneGuarded = false;
        this.state = State.WAITING;
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
        if (event.phase != TickEvent.Phase.END) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (worldChangesDiscarded && mc.getIntegratedServer() == null) {
            worldChangesDiscarded = false;
        }
        if (this.state == State.IDLE || this.state == State.DONE) return;

        if (mc.theWorld == null && (this.state == State.PREPARING || this.state == State.WARMUP || this.state == State.RUNNING || this.state == State.SETTLE)) {
            this.cancel();
            return;
        }

        if (mc.theWorld == null && this.armed) {
            this.restorePauseOnLostFocus(mc);
            this.armed = false;
        }

        if (this.state == State.EXITING) {
            if (++this.tick >= EXIT_TICKS) {
                this.state = State.DONE;
                this.exitGame(mc);
            }
            return;
        }

        final EntityClientPlayerMP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) return;

        switch (this.state) {
            case WAITING -> {
                if (!this.armed) {
                    this.armed = true;
                    this.waitTicks = 0;
                    this.overridePauseOnLostFocus(mc);
                }
                if (mc.currentScreen != null) mc.displayGuiScreen(null);

                if (this.waitForTracy && !Tracy.isConnected()) {
                    if (this.waitTicks++ % 100 == 0) LOGGER.info("Flyby waiting for Tracy connection");
                    return;
                }
                if (this.waitForFocus && !Display.isActive()) {
                    if (this.waitTicks++ % 100 == 0) LOGGER.info("Flyby waiting for window focus");
                    return;
                }
                this.begin(mc, player);
            }
            case PREPARING -> {
                final Double feetY = this.activeRequest.feetY().getNow(null);
                if (feetY == null) return;
                this.flightY = feetY + this.eyeOffset;
                this.startRun(mc);
            }
            case WARMUP -> {
                this.applyPosition(player, 0);
                if (this.jfr && this.tick == Math.max(0, this.warmupTicks - 40)) {
                    final String error;
                    synchronized (AsprofRecorder.class) {
                        error = AsprofRecorder.start(this.route.id(), SystemProperties.PROFILE_OPTS);
                        if (error == null) this.recordingId = AsprofRecorder.recordingId();
                    }
                    if (error != null) {
                        LOGGER.warn("Flyby: failed to start async-profiler: {}", error);
                        player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[Angelica] Flyby: failed to start async-profiler: " + error));
                    }
                }
                if (++this.tick >= this.warmupTicks) {
                    this.tick = 0;
                    this.beginMeasuring(mc);
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

    private void overridePauseOnLostFocus(Minecraft mc) {
        if (this.pauseOnLostFocusOverridden) return;
        this.pauseOnLostFocusSaved = mc.gameSettings.pauseOnLostFocus;
        this.pauseOnLostFocusOverridden = true;
        mc.gameSettings.pauseOnLostFocus = false;
    }

    private void begin(Minecraft mc, EntityClientPlayerMP player) {
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        this.overridePauseOnLostFocus(mc);

        this.parkedX = player.posX;
        this.parkedY = player.posY;
        this.parkedZ = player.posZ;
        this.parkedYaw = player.rotationYaw;
        this.parkedPitch = player.rotationPitch;
        this.eyeOffset = player.yOffset;

        final boolean creative = player.capabilities.isCreativeMode;
        if (!creative) {
            LOGGER.warn("Flyby: player is not in creative mode, flying at the player height{}", this.fixedOrigin != null ? " and ignoring the pinned origin" : "");
        }

        if (creative && this.fixedOrigin != null) {
            this.originX = this.fixedOrigin.x();
            this.originZ = this.fixedOrigin.z();
            this.originYaw = this.fixedOrigin.yaw();
        } else {
            this.originX = player.posX;
            this.originZ = player.posZ;
            this.originYaw = Math.round(player.rotationYaw / 90.0F) * 90.0F;
            if (this.originYaw != player.rotationYaw) {
                LOGGER.info("Flyby snapped heading {} -> {}", player.rotationYaw, this.originYaw);
            }
        }

        this.buildPath();
        this.suppressPacing();

        this.tick = 0;
        if (mc.getIntegratedServer() != null) {
            final FlybyRequest request = new FlybyRequest(this.pathX, this.pathZ, this.originX, this.originZ, player.posY - this.eyeOffset, player.getCommandSenderName(), creative, this.sceneCommands.length > 0, new CompletableFuture<>());
            this.activeRequest = request;
            sceneGuarded = true;
            this.pendingRequest.set(request);
            this.state = State.PREPARING;
        } else {
            LOGGER.warn("Flyby: not singleplayer, cannot freeze time/weather or sample terrain - flying at the player height, results may not be comparable");
            this.flightY = player.posY;
            this.startRun(mc);
        }
    }

    private void startRun(Minecraft mc) {
        this.tick = 0;
        if (this.warmupTicks > 0) {
            this.state = State.WARMUP;
            this.warmupSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, "flyby warmup");
        } else {
            this.beginMeasuring(mc);
        }

        LOGGER.info("Flyby {} starting at {} {} {} yaw {}", this.route.id(), this.originX, this.flightY, this.originZ, this.originYaw);
    }

    private void suppressPacing() {
        FpsReducer.beginBenchmark();
        if (SystemProperties.FLYBY_PACING == FlybyPacing.UNCAPPED && GLStateManager.getEffectiveVSyncMode().tearFree()) {
            GLStateManager.setVSyncMode(VSyncMode.OFF);
            this.vsyncOverridden = true;
        }
    }

    private void restorePacing(Minecraft mc) {
        FpsReducer.endBenchmark();
        if (!this.vsyncOverridden) return;
        GLStateManager.setVSyncEnabled(mc.gameSettings.enableVsync);
        this.vsyncOverridden = false;
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
    }

    private void emitPhase(int index) {
        if (index >= this.pathLeg.length) return;

        final int leg = this.pathLeg[index];
        final int turning = this.pathTurning[index];
        if (Tracy.ENABLED) {
            Tracy.plotInt(this.plotLeg, leg);
            Tracy.plotInt(this.plotTurning, turning);
        }

        final int phase = (leg << 1) | turning;
        if (phase != this.lastPhase) {
            this.lastPhase = phase;
            if (this.phaseCount < this.phaseStartFrame.length) {
                this.phaseStartFrame[this.phaseCount] = this.frameCount;
                this.phaseKey[this.phaseCount] = phase;
                this.phaseCount++;
            }
            if (Tracy.ENABLED) {
                final String label = phaseLabel(phase);
                Tracy.message(label);
                Tracy.sectionLeave(this.legSection);
                this.legSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, label);
            }
        }
    }

    private static String phaseLabel(int phase) {
        return "flyby " + ((phase & 1) != 0 ? "turn" : "leg") + " " + (phase >> 1);
    }

    private void beginMeasuring(Minecraft mc) {
        this.state = State.RUNNING;
        Tracy.sectionLeave(this.warmupSection);
        this.warmupSection = 0L;
        if (Tracy.ENABLED) this.runSection = Tracy.sectionEnter(Tracy.SECTION_BENCHMARK, "flyby " + this.route.id());
        this.plotLeg = Tracy.plotHandle("flyby.leg");
        this.plotTurning = Tracy.plotHandle("flyby.turning");
        this.lastPhase = -1;
        this.frameTimesNs = new long[Math.min(MAX_RECORDED_FRAMES, Math.max(1024, this.runTicks * 20))];
        this.frameCount = 0;
        this.phaseCount = 0;
        this.lastFrameNs = 0L;
        this.runStartNs = System.nanoTime();
        FramePacer.beginStats();

        final String config = "backend=" + GLStateManager.getRenderBackendName() + " pacing=" + SystemProperties.FLYBY_PACING + " vsync=" + GLStateManager.getEffectiveVSyncMode() + " discard=" + (mc.getIntegratedServer() != null);
        if (Tracy.ENABLED) {
            Tracy.message("flyby start route=" + this.route.id() + " length=" + this.runLength + this.route.lengthUnit() + " speed=" + this.route.speedOr(this.speed) + "b/t ticks=" + this.runTicks + " " + config);
        }
        LOGGER.info("Flyby measuring: {}", config);
    }

    private void holdPosition(EntityClientPlayerMP player) {
        final int last = this.pathX.length - 1;
        if (last < 0) return;

        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.ySize = 0.0F;

        player.prevPosX = player.lastTickPosX = this.pathX[last];
        player.prevPosY = player.lastTickPosY = this.flightY;
        player.prevPosZ = player.lastTickPosZ = this.pathZ[last];
        player.prevRotationYaw = player.rotationYaw = this.pathYaw[last];
        player.prevRotationYawHead = player.rotationYawHead = this.pathYaw[last];
        player.prevRotationPitch = player.rotationPitch = this.parkedPitch;
        player.setPosition(this.pathX[last], this.flightY, this.pathZ[last]);
    }

    private void applyPosition(EntityClientPlayerMP player, int index) {
        final int i = Math.min(index, this.pathX.length - 1);
        final int prev = Math.max(0, i - 1);

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
        player.prevPosY = player.lastTickPosY = this.flightY;
        player.prevPosZ = player.lastTickPosZ = prevZ;

        player.prevRotationYaw = prevYaw;
        player.rotationYaw = yaw;
        player.prevRotationYawHead = prevYaw;
        player.rotationYawHead = yaw;
        player.prevRotationPitch = player.rotationPitch = this.parkedPitch;

        player.setPosition(x, this.flightY, z);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final boolean clear = this.sceneClearRequested;
        if (!clear && this.pendingRequest.get() == null) return;

        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        if (clear) {
            this.sceneClearRequested = false;
            this.clearScene(server);
        }

        final FlybyRequest request = this.pendingRequest.getAndSet(null);
        if (request == null) return;

        this.discardWorldChanges(server);
        final double feetY = this.flightFeetY(server, request);
        request.feetY().complete(feetY);
        this.applyFreeze(server);
        if (request.scene()) {
            this.runScene(server, request, feetY);
        }
    }

    private void discardWorldChanges(MinecraftServer server) {
        if (worldChangesDiscarded) return;

        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            try {
                world.saveAllChunks(true, null);
            } catch (MinecraftException e) {
                LOGGER.error("Flyby: failed to save the world before discarding changes", e);
            }
        }
        server.getConfigurationManager().saveAllPlayerData();

        worldChangesDiscarded = true;
        LOGGER.warn("Flyby: world saving is off until you leave this world");
        server.getConfigurationManager().sendChatMsg(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + "Flyby: world saving is off until you leave this world"));
    }

    private void applyFreeze(MinecraftServer server) {
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            if (world.getGameRules().getGameRuleBooleanValue("doDaylightCycle")) {
                world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                LOGGER.warn("Flyby: doDaylightCycle was on, disabled it");
            }
            if (world.getWorldInfo().isRaining() || world.getWorldInfo().isThundering()) {
                LOGGER.warn("Flyby: weather was active, clearing it");
                world.getWorldInfo().setRaining(false);
                world.getWorldInfo().setThundering(false);
            }
            world.getWorldInfo().setRainTime(Integer.MAX_VALUE);
            world.getWorldInfo().setThunderTime(Integer.MAX_VALUE);

            if (world.getWorldTime() % 24000L != SystemProperties.FLYBY_TIME_OF_DAY) {
                LOGGER.info("Flyby: freezing time at {}", SystemProperties.FLYBY_TIME_OF_DAY);
                world.setWorldTime(SystemProperties.FLYBY_TIME_OF_DAY);
            }
        }
    }

    private double flightFeetY(MinecraftServer server, FlybyRequest request) {
        if (!request.sampleTerrain()) return request.parkedFeetY();

        final EntityPlayerMP player = server.getConfigurationManager().func_152612_a(request.playerName());
        if (player == null) {
            LOGGER.warn("Flyby terrain: no server player named '{}', flying at the parked feet Y {}", request.playerName(), request.parkedFeetY());
            return request.parkedFeetY();
        }

        final WorldServer world = player.getServerForPlayer();
        if (world.provider.hasNoSky) {
            LOGGER.info("Flyby terrain: dimension has a ceiling, flying at the parked feet Y {}", request.parkedFeetY());
            return request.parkedFeetY();
        }

        final IChunkProvider chunks = world.getChunkProvider();
        final int floor = FlybyTerrain.routeFloor(request.pathX(), request.pathZ(), FlybyTerrain.CORRIDOR_RADIUS, (x, z) -> precipitationHeight(chunks, x, z));
        if (floor < 1) {
            LOGGER.info("Flyby terrain: no ground within {} blocks of the path, flying at the parked feet Y {}", FlybyTerrain.CORRIDOR_RADIUS, request.parkedFeetY());
            return request.parkedFeetY();
        }

        final double feetY = floor + FlybyTerrain.HOVER_BLOCKS;
        LOGGER.info("Flyby terrain: route floor {} within {} blocks of the path, flight feet Y {}", floor, FlybyTerrain.CORRIDOR_RADIUS, feetY);
        if (Tracy.ENABLED) Tracy.message("flyby terrain floor=" + floor + " radius=" + FlybyTerrain.CORRIDOR_RADIUS + " feetY=" + feetY);
        return feetY;
    }

    private static int precipitationHeight(IChunkProvider chunks, int x, int z) {
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        Chunk chunk = chunks.loadChunk(chunkX, chunkZ);
        if (chunk == null) chunk = chunks.provideChunk(chunkX, chunkZ);
        return chunk == null ? Integer.MIN_VALUE : chunk.getPrecipitationHeight(x & 15, z & 15);
    }

    private void runScene(MinecraftServer server, FlybyRequest request, double feetY) {
        final EntityPlayerMP player = server.getConfigurationManager().func_152612_a(request.playerName());
        if (player == null) {
            LOGGER.warn("Flyby scene: no server player named '{}', skipping scene", request.playerName());
            return;
        }

        this.clearScene(server);

        final FlybyCommandSender sender = new FlybyCommandSender(player, new ChunkCoordinates(MathHelper.floor_double(request.originX()), MathHelper.floor_double(feetY + 0.5D), MathHelper.floor_double(request.originZ())));
        for (String line : this.sceneCommands) {
            if (server.getCommandManager().executeCommand(sender, line) == 0) {
                LOGGER.warn("flyby scene command did not execute: {}", line);
            }
        }
        LOGGER.info("Flyby scene: ran {} commands", this.sceneCommands.length);
        if (Tracy.ENABLED) Tracy.message("flyby scene " + this.sceneCommands.length + " commands");
        int total = 0;
        for (WorldServer world : server.worldServers) {
            if (world != null) total += FlybyScene.count(world);
        }
        this.sceneSpawnTotal = total;
    }

    private void clearScene(MinecraftServer server) {
        int remaining = 0;
        int cleared = 0;
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            remaining += FlybyScene.count(world);
            cleared += FlybyScene.clear(world);
        }
        if (cleared > 0) LOGGER.info("Flyby scene: cleared {} entities and blocks", cleared);
        if (worldChangesDiscarded && remaining < this.sceneSpawnTotal) {
            LOGGER.warn("Flyby scene: {} of {} spawned entities and blocks were lost before the clear", this.sceneSpawnTotal - remaining, this.sceneSpawnTotal);
        }
        this.sceneSpawnTotal = 0;
    }

    private void finish(Minecraft mc, EntityClientPlayerMP player) {
        this.state = State.SETTLE;
        this.tick = 0;
        final long elapsedNs = System.nanoTime() - this.runStartNs;
        Tracy.sectionLeave(this.legSection);
        this.legSection = 0L;
        Tracy.sectionLeave(this.runSection);
        this.runSection = 0L;
        if (Tracy.ENABLED) Tracy.message("flyby end route=" + this.route.id() + " frames=" + this.frameCount);

        final String summary = this.summarise(elapsedNs, player);
        LOGGER.info(summary);
        LOGGER.info(FramePacer.endStats());
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + summary));
        }

        this.stopRecording(mc);
    }

    private void stopRecording(Minecraft mc) {
        final long ownedId = this.recordingId;
        this.recordingId = 0;
        final StopResult result = AsprofRecorder.stopIfRecording(ownedId);
        if (result.status() == StopStatus.NO_MATCH) return;
        final boolean failed = result.status() == StopStatus.FAILED;
        final String message = failed ? "Flyby: failed to stop async-profiler: " + result.error()
            : "Flyby: profile written to " + result.path();
        if (failed) LOGGER.warn(message);
        else LOGGER.info(message);
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText((failed ? EnumChatFormatting.RED : EnumChatFormatting.AQUA)
                + "[Angelica] " + message));
        }
        if (Tracy.ENABLED) Tracy.message(message);
    }

    private void teardown(Minecraft mc, EntityClientPlayerMP player) {
        Tracy.message("flyby teardown");

        this.returnToOrigin(player);
        this.restorePauseOnLostFocus(mc);
        this.armed = false;
        sceneGuarded = false;
        this.sceneClearRequested = this.sceneCommands.length > 0;
        this.restorePacing(mc);

        this.tick = 0;
        this.state = this.exitWhenDone ? State.EXITING : State.DONE;
    }

    private void exitGame(Minecraft mc) {
        this.stopRecording(mc);
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
        player.setPositionAndRotation(this.parkedX, this.parkedY, this.parkedZ, this.parkedYaw, this.parkedPitch);
        player.lastTickPosX = this.parkedX;
        player.lastTickPosY = this.parkedY;
        player.lastTickPosZ = this.parkedZ;
        LOGGER.info("Flyby returned to {} {} {}", this.parkedX, this.parkedY, this.parkedZ);
    }

    private String summarise(long elapsedNs, EntityClientPlayerMP player) {
        final StringBuilder sb = new StringBuilder(192);
        sb.append("Flyby ").append(this.route.id())
          .append(": ").append(this.frameCount).append(" frames in ")
          .append(String.format("%.2fs", elapsedNs / 1_000_000_000.0D));

        if (this.frameCount > 0) appendFrameStats(sb, 0, this.frameCount);
        if (this.framesTruncated) sb.append(" (frame samples truncated)");
        for (int i = 0; i < this.phaseCount; i++) {
            final int from = this.phaseStartFrame[i];
            final int to = i + 1 < this.phaseCount ? this.phaseStartFrame[i + 1] : this.frameCount;
            if (to <= from) continue;
            sb.append("; ").append(phaseLabel(this.phaseKey[i]).substring(6)).append(": ").append(to - from).append(" frames");
            appendFrameStats(sb, from, to);
        }

        sb.append(String.format(", end position %.6f %.6f %.6f", player.posX, player.posY, player.posZ));
        return sb.toString();
    }

    private void appendFrameStats(StringBuilder sb, int from, int to) {
        final int n = to - from;
        final long[] sorted = Arrays.copyOfRange(this.frameTimesNs, from, to);
        Arrays.sort(sorted);
        long total = 0L;
        for (long ns : sorted) total += ns;
        sb.append(String.format(", avg %.2fms, p50 %.2fms, p99 %.2fms, max %.2fms",
            total / (double) n / 1e6D,
            sorted[n / 2] / 1e6D,
            sorted[Math.min(n - 1, (int) (n * 0.99D))] / 1e6D,
            sorted[n - 1] / 1e6D));
    }

    public void cancel() {
        if (this.isActive()) {
            LOGGER.info("Flyby cancelled");
            this.state = State.DONE;
            Tracy.sectionLeave(this.warmupSection);
            this.warmupSection = 0L;
            Tracy.sectionLeave(this.legSection);
            this.legSection = 0L;
            Tracy.sectionLeave(this.runSection);
            this.runSection = 0L;
            FramePacer.endStats();
            final Minecraft mc = Minecraft.getMinecraft();
            this.restorePauseOnLostFocus(mc);
            this.armed = false;
            this.stopRecording(mc);
            this.pendingRequest.set(null);
            this.activeRequest = null;
            sceneGuarded = false;
            this.sceneClearRequested = this.sceneCommands.length > 0;
            this.restorePacing(mc);
        }
    }

    public boolean startedFromProperties() {
        return this.startedFromProperties;
    }

    private record FlybyRequest(double[] pathX, double[] pathZ, double originX, double originZ, double parkedFeetY, String playerName, boolean sampleTerrain, boolean scene, CompletableFuture<Double> feetY) {}
}
