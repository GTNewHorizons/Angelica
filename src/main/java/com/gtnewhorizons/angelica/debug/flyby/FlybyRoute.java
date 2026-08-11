package com.gtnewhorizons.angelica.debug.flyby;

/**
 * A deterministic camera path, expressed relative to the position the player held when the run was started.
 * <p>
 * Moving routes are measured in blocks rather than tick.
 */
public enum FlybyRoute {
    /**
     * Straight line path, 512 blocks (32 chunks) by default.
     */
    STRAIGHT("straight", Kind.STRAIGHT, 0.5D, 0.0D, 512),

    /**
     * Stationary rotation, 720 degrees by default.
     */
    PAN("pan", Kind.ROTATE, 0.0D, 1.5D, 720),

    /**
     * Closed square: four legs of {@code length} blocks joined by four 90-degree turns, ending where it started.
     */
    CIRCUIT("circuit", Kind.SQUARE, 0.5D, 4.0D, 128),

    /** Fixed vantage, no motion, measured in ticks. */
    STATIC("static", Kind.STILL, 0.0D, 0.0D, 600);

    public enum Kind { STRAIGHT, ROTATE, SQUARE, STILL }

    public static final int CIRCUIT_LEGS = 4;
    public static final double CIRCUIT_TURN_DEGREES = 90.0D;

    private final String id;
    private final Kind kind;
    private final double blocksPerTick;
    private final double degreesPerTick;
    private final int defaultLength;

    FlybyRoute(String id, Kind kind, double blocksPerTick, double degreesPerTick, int defaultLength) {
        this.id = id;
        this.kind = kind;
        this.blocksPerTick = blocksPerTick;
        this.degreesPerTick = degreesPerTick;
        this.defaultLength = defaultLength;
    }

    public String id() {
        return this.id;
    }

    public Kind kind() {
        return this.kind;
    }

    public double blocksPerTick() {
        return this.blocksPerTick;
    }

    public double degreesPerTick() {
        return this.degreesPerTick;
    }

    public int defaultLength() {
        return this.defaultLength;
    }

    public String lengthUnit() {
        return switch (this.kind) {
            case STRAIGHT -> "blocks";
            case SQUARE -> "blocks per leg";
            case ROTATE -> "degrees";
            case STILL -> "ticks";
        };
    }

    public double speedOr(double override) {
        return override > 0.0D ? override : this.blocksPerTick;
    }

    public int legTicks(int length, double speed) {
        return (int) Math.ceil(length / this.speedOr(speed));
    }

    public int turnTicks() {
        return (int) Math.ceil(CIRCUIT_TURN_DEGREES / this.degreesPerTick);
    }

    /**
     * Converts a run length in {@link #lengthUnit()} into ticks at {@code speed} blocks per tick.
     */
    public int toTicks(int length, double speed) {
        if (length <= 0) length = this.defaultLength;
        return switch (this.kind) {
            case STRAIGHT -> this.legTicks(length, speed);
            case ROTATE -> (int) Math.ceil(length / this.degreesPerTick);
            case SQUARE -> CIRCUIT_LEGS * (this.legTicks(length, speed) + this.turnTicks());
            case STILL -> length;
        };
    }

    public static FlybyRoute byId(String id) {
        for (FlybyRoute route : values()) {
            if (route.id.equalsIgnoreCase(id)) {
                return route;
            }
        }
        return null;
    }

    public static String ids() {
        StringBuilder sb = new StringBuilder();
        for (FlybyRoute route : values()) {
            if (sb.length() > 0) sb.append('|');
            sb.append(route.id);
        }
        return sb.toString();
    }
}
