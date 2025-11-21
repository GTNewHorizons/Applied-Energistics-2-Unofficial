package appeng.api.storage;

public enum StorageName {

    NONE(""),
    CRAFTING_INPUT("crafting"),
    CRAFTING_OUTPUT("output"),
    CRAFTING_PATTERN("pattern");

    private final String name;

    StorageName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
