package appeng.api.config;

public enum CraftingAllow {

    ALLOW_ALL,

    ONLY_PLAYER,

    ONLY_NONPLAYER;

    public CraftingAllow next() {
        return switch (this) {
            case ONLY_PLAYER -> CraftingAllow.ONLY_NONPLAYER;
            case ONLY_NONPLAYER -> CraftingAllow.ALLOW_ALL;
            case ALLOW_ALL -> CraftingAllow.ONLY_PLAYER;
        };
    }
}
