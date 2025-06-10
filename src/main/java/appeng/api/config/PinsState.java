package appeng.api.config;

public enum PinsState {

    DISABLED,
    ONE,
    TWO,
    THREE,
    FOUR;

    public static PinsState fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Invalid ordinal for PinsNumber: " + ordinal);
        }
        return values()[ordinal];
    }

    public static int getPinsCount() {
        return (Settings.PINS_STATE.getPossibleValues().size() - 1) * 9;
    }
}
