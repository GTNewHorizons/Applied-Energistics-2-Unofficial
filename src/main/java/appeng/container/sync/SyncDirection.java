package appeng.container.sync;

public enum SyncDirection {

    SERVER_TO_CLIENT,
    CLIENT_TO_SERVER,
    BIDIRECTIONAL;

    public boolean canSendFrom(final SyncEndpoint endpoint) {
        return switch (this) {
            case SERVER_TO_CLIENT -> endpoint == SyncEndpoint.SERVER;
            case CLIENT_TO_SERVER -> endpoint == SyncEndpoint.CLIENT;
            case BIDIRECTIONAL -> true;
        };
    }

    public boolean canReceiveFrom(final SyncEndpoint remoteEndpoint) {
        return this.canSendFrom(remoteEndpoint);
    }

    public boolean sendsInitialStateFrom(final SyncEndpoint endpoint) {
        return endpoint == SyncEndpoint.SERVER && this != CLIENT_TO_SERVER;
    }
}
