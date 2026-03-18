package appeng.api.config;

public enum PinsRows {

    DISABLED,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    ELEVEN,
    TWELVE,
    THIRTEEN,
    FOURTEEN,
    FIFTEEN,
    SIXTEEN;

    public static PinsRows fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Invalid ordinal for PinsRows: " + ordinal);
        }
        return values()[ordinal];
    }

    public int getSlotCount() {
        return ordinal() * 9;
    }
}
