package appeng.me.diagnostics;

import appeng.api.storage.data.IAEStack;

public final class DiagnosticRowView {

    public final IAEStack<?> stack;
    public final long totalProduced;
    public final long elapsedTimeTicks;
    public final long sampleCount;

    public DiagnosticRowView(final IAEStack<?> stack, final long totalProduced, final long elapsedTimeTicks,
            final long sampleCount) {
        this.stack = stack;
        this.totalProduced = totalProduced;
        this.elapsedTimeTicks = elapsedTimeTicks;
        this.sampleCount = sampleCount;
    }
}
