package appeng.container.sync.codecs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.container.sync.DeltaSyncCodec;
import appeng.container.sync.deltas.TypeFilterDelta;
import appeng.util.AEStackTypeFilter;
import io.netty.buffer.ByteBuf;

public final class AEStackTypeFilterSyncCodec implements DeltaSyncCodec<AEStackTypeFilter, TypeFilterDelta> {

    public static final AEStackTypeFilterSyncCodec INSTANCE = new AEStackTypeFilterSyncCodec();

    private static final byte OP_TOGGLE = 0;

    private AEStackTypeFilterSyncCodec() {}

    @Override
    public void write(final ByteBuf buf, final AEStackTypeFilter value) {
        value.writeToPacket(buf);
    }

    @Override
    public AEStackTypeFilter read(final ByteBuf buf) {
        return new AEStackTypeFilter(buf);
    }

    @Override
    public AEStackTypeFilter copy(final AEStackTypeFilter value) {
        return new AEStackTypeFilter(value);
    }

    @Override
    public boolean valuesEqual(final AEStackTypeFilter a, final AEStackTypeFilter b) {
        return a.equals(b);
    }

    @Override
    public @Nullable List<TypeFilterDelta> diff(final AEStackTypeFilter previous, final AEStackTypeFilter current) {
        List<TypeFilterDelta> deltas = null;
        for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (previous.isEnabled(type) != current.isEnabled(type)) {
                if (deltas == null) {
                    deltas = new ArrayList<>();
                }
                deltas.add(TypeFilterDelta.toggle(type));
            }
        }

        return deltas;
    }

    @Override
    public void writeDelta(final ByteBuf buf, final TypeFilterDelta delta) throws IOException {
        if (delta instanceof final TypeFilterDelta.Toggle toggle) {
            buf.writeByte(OP_TOGGLE);
            buf.writeByte(toggle.getNetworkId());
            return;
        }

        throw new IOException("Unknown AEStackTypeFilter delta type: " + delta.getClass().getName());
    }

    @Override
    public AEStackTypeFilter applyDelta(@Nullable final AEStackTypeFilter base, final ByteBuf buf) throws IOException {
        if (base == null) {
            throw new IOException("Cannot apply AEStackTypeFilter delta without a base value.");
        }

        final byte op = buf.readByte();
        switch (op) {
            case OP_TOGGLE -> {
                final IAEStackType<?> type = this.requireType(buf.readByte());
                base.toggle(type);
                return base;
            }
            default -> throw new IOException("Unknown AEStackTypeFilter delta opcode: " + op);
        }
    }

    @Override
    public String getTypeKey() {
        return AEStackTypeFilter.class.getName();
    }

    private IAEStackType<?> requireType(final byte networkId) throws IOException {
        final IAEStackType<?> type = AEStackTypeRegistry.getTypeFromNetworkId(networkId);
        if (type == null) {
            throw new IOException("Unknown AE stack type network id: " + networkId);
        }

        return type;
    }
}
