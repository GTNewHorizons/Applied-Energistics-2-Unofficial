package appeng.container.sync;

import java.io.IOException;

public enum SyncMode {

    FULL,
    DELTA,
    REQUEST_FULL;

    public static SyncMode fromOrdinal(final int ordinal) throws IOException {
        final SyncMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IOException("Unknown sync mode ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
