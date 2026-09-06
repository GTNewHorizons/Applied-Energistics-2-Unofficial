package appeng.core.settings;

public enum ControllerAnimation {

    ORIGINAL_RAINBOW,
    WAVE,
    COUNTERFLOW,
    CORE_RIPPLE,
    BREATHING,
    SOFT_INTERFERENCE,
    SINGULARITY,
    CIRCUIT_TRACE;

    public ControllerAnimation next() {
        final ControllerAnimation[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }

    public boolean usesOriginalTexture() {
        return this == ORIGINAL_RAINBOW;
    }

    public boolean followsCircuitPaths() {
        return this == CIRCUIT_TRACE;
    }

    public int frameCount() {
        // The two interference waves meet again after five 2.4-second cycles.
        return this == SOFT_INTERFERENCE ? 240 : 48;
    }

    public double brightness(final double x, final double y, final double time) {
        return switch (this) {
            case ORIGINAL_RAINBOW -> 0;
            case WAVE -> pulse(x + y - time, 4);
            case COUNTERFLOW -> pulse(y + (x < 0.5 ? -time : time), 4);
            case CORE_RIPPLE -> pulse(Math.hypot(x - 0.5, y - 0.5) * 1.5 - time, 4);
            case BREATHING -> pulse(time, 1) * 0.65;
            case SOFT_INTERFERENCE -> 0.8 * pulse(x + y - time * 0.6, 2) * pulse(x - y + time * 0.4, 2);
            case SINGULARITY -> 0.8 * pulse(
                    Math.hypot(x - 0.5, y - 0.5) * 2 + Math.atan2(y - 0.5, x - 0.5) / (2 * Math.PI) * 3 - time,
                    4);
            case CIRCUIT_TRACE -> pulse(x + y - time, 4);
        };
    }

    private static double pulse(final double phase, final int sharpness) {
        return Math.pow((1 + Math.cos(2 * Math.PI * phase)) / 2, sharpness);
    }
}
