package appeng.api.config;

public enum ReshufflePhase {
    IDLE,
    BEFORE_SNAPSHOT,
    AFTER_SNAPSHOT,
    EXTRACTION,
    INJECTION,
    DONE,
    CANCEL,
    ERROR;
}
