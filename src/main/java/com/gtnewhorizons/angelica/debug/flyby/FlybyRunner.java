package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/** Flies a deterministic camera path */
public final class FlybyRunner {
    public static final FlybyRunner INSTANCE = new FlybyRunner();

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final long[] NO_FRAMES = new long[0];
    private static final double[] NO_PATH = new double[0];
    private static final float[] NO_YAW = new float[0];
    private static final byte[] NO_PHASE = new byte[0];
    private static final int MAX_RECORDED_FRAMES = 200_000;
    private static final long FIXED_TIME_OF_DAY = 6000L;

    private enum State { IDLE, WAITING, WARMUP, RUNNING, SETTLE, EXITING, DONE }

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
    private boolean exitWhenDone;
    private boolean startedFromProperties;
    private volatile boolean freezeRequested;

    private double originX, originY, originZ;
    private float originYaw, originPitch;

    private double[] pathX = NO_PATH;
    private double[] pathZ = NO_PATH;
    private float[] pathYaw = NO_YAW;
    private byte[] pathLeg = NO_PHASE;
    private byte[] pathTurning = NO_PHASE;

    private long plotLeg;
    private long plotTurning;
    private int lastPhase = -1;

    private long[] frameTimesNs = NO_FRAMES;
    private int frameCount;
    private boolean framesTruncated;
    private long lastFrameNs;
    private long runStartNs;

    private FlybyRunner() {}

    public void startFromProperties() {
        final String id = SystemProperties.FLYBY_ROUTE;
        if (id == null || id.isEmpty()) return;

        final FlybyRoute configured = FlybyRoute.byId(id);
        if (configured == null) {
            LOGGER.error("Unknown flyby route '{}', expected one of {}", id, FlybyRoute.ids());
            return;
        }

        this.startedFromProperties = true;
        this.start(configured, SystemProperties.FLYBY_LENGTH, SystemProperties.FLYBY_WARMUP_TICKS, SystemProperties.FLYBY_SPEED);
        this.waitForTracy = SystemProperties.FLYBY_WAIT_FOR_TRACY;
        this.exitWhenDone = SystemProperties.FLYBY_EXIT_WHEN_DONE;
        LOGGER.info("Flyby started from properties: route={} warmup={} length={} {} ({} ticks) waitForTracy={} exitWhenDone={}",
            configured.id(), this.warmupTicks, this.runLength, configured.lengthUnit(), this.runTicks,
            this.waitForTracy, this.exitWhenDone);
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
        this.exitWhenDone = false;
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
        if (event.phase != TickEvent.Phase.END || this.state == State.IDLE || this.state == State.DONE) return;

        final Minecraft mc = Minecraft.getMinecraft();
        final EntityClientPlayerMP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) return;

        switch (this.state) {
            case WAITING -> {
                if (this.waitForTracy && !Tracy.isConnected()) return;
                this.begin(mc, player);
            }
            case WARMUP -> {
                this.applyPosition(player, 0);
                if (++this.tick >= this.warmupTicks) {
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
            case EXITING -> {
                if (++this.tick >= EXIT_TICKS) {
                    this.state = State.DONE;
                    LOGGER.info("Flyby complete, shutting down so the capture can finalise");
                    mc.shutdown();
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

    private void begin(Minecraft mc, EntityClientPlayerMP player) {
        this.originX = player.posX;
        this.originY = player.posY;
        this.originZ = player.posZ;
        this.originPitch = player.rotationPitch;

        this.originYaw = Math.round(player.rotationYaw / 90.0F) * 90.0F;
        if (this.originYaw != player.rotationYaw) {
            LOGGER.info("Flyby snapped heading {} -> {}", player.rotationYaw, this.originYaw);
        }

        this.buildPath();
        this.freezeTimeAndWeather(mc);

        this.state = this.warmupTicks > 0 ? State.WARMUP : State.RUNNING;
        this.tick = 0;
        if (this.state == State.RUNNING) this.beginMeasuring();

        LOGGER.info("Flyby {} starting at {} {} {} yaw {}", this.route.id(),
            this.originX, this.originY, this.originZ, this.originYaw);
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
        if (!Tracy.ENABLED || index >= this.pathLeg.length) return;

        final int leg = this.pathLeg[index];
        final int turning = this.pathTurning[index];
        Tracy.plotInt(this.plotLeg, leg);
        Tracy.plotInt(this.plotTurning, turning);

        final int phase = (leg << 1) | turning;
        if (phase != this.lastPhase) {
            this.lastPhase = phase;
            Tracy.message("flyby " + (turning != 0 ? "turn" : "leg") + " " + leg);
        }
    }

    private void beginMeasuring() {
        this.state = State.RUNNING;
        this.plotLeg = Tracy.plotHandle("flyby.leg");
        this.plotTurning = Tracy.plotHandle("flyby.turning");
        this.lastPhase = -1;
        this.frameTimesNs = new long[Math.min(MAX_RECORDED_FRAMES, Math.max(1024, this.runTicks * 20))];
        this.frameCount = 0;
        this.lastFrameNs = 0L;
        this.runStartNs = System.nanoTime();
        Tracy.message("flyby start route=" + this.route.id()
            + " length=" + this.runLength + this.route.lengthUnit()
            + " speed=" + this.route.speedOr(this.speed) + "b/t"
            + " ticks=" + this.runTicks
            + " sdlgpu=" + SystemProperties.USE_SDL_GPU);
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
        player.prevPosY = player.lastTickPosY = this.originY;
        player.prevPosZ = player.lastTickPosZ = prevZ;

        player.prevRotationYaw = prevYaw;
        player.rotationYaw = yaw;
        player.prevRotationYawHead = prevYaw;
        player.rotationYawHead = yaw;
        player.prevRotationPitch = player.rotationPitch = this.originPitch;

        player.setPosition(x, this.originY, z);
    }

    /** Requests the freeze; the write happens on the server thread in {@link #onServerTick}. */
    private void freezeTimeAndWeather(Minecraft mc) {
        final MinecraftServer server = mc.getIntegratedServer();
        if (server == null) {
            LOGGER.warn("Flyby: not singleplayer, cannot freeze time/weather - results may not be comparable");
            return;
        }

        this.freezeRequested = true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !this.freezeRequested) return;
        this.freezeRequested = false;

        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

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

            if (world.getWorldTime() % 24000L != FIXED_TIME_OF_DAY) {
                LOGGER.info("Flyby: freezing time at {}", FIXED_TIME_OF_DAY);
                world.setWorldTime(FIXED_TIME_OF_DAY);
            }
        }
    }

    private void finish(Minecraft mc, EntityClientPlayerMP player) {
        this.state = State.SETTLE;
        this.tick = 0;
        final long elapsedNs = System.nanoTime() - this.runStartNs;
        Tracy.message("flyby end route=" + this.route.id() + " frames=" + this.frameCount);

        final String summary = this.summarise(elapsedNs, player);
        LOGGER.info(summary);
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "[Angelica] " + EnumChatFormatting.WHITE + summary));
        }

    }

    private void teardown(Minecraft mc, EntityClientPlayerMP player) {
        Tracy.message("flyby teardown");

        this.returnToOrigin(player);

        this.tick = 0;
        this.state = this.exitWhenDone ? State.EXITING : State.DONE;
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
        LOGGER.info("Flyby returned to origin {} {} {}", this.originX, this.originY, this.originZ);
    }

    private String summarise(long elapsedNs, EntityClientPlayerMP player) {
        final StringBuilder sb = new StringBuilder(192);
        sb.append("Flyby ").append(this.route.id())
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
            LOGGER.info("Flyby cancelled");
            this.state = State.DONE;
        }
    }

    public boolean startedFromProperties() {
        return this.startedFromProperties;
    }
}
