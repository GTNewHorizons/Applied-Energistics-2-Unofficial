package appeng.api.networking.security;

/**
 * Represents an action originating from internal processing.
 */
public class InternalActionSource implements BaseActionSourceV2 {

    @Override
    public boolean isInternal() {
        return true;
    }
}
