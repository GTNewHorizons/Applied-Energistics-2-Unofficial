package appeng.api.config;

public enum CraftingPinsRows {

    DISABLED,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT;

    public static CraftingPinsRows fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Invalid ordinal for CraftingPinsRows: " + ordinal);
        }
        return values()[ordinal];
    }

    public int getSlotCount() {
        return ordinal() * 9;
    }
}
