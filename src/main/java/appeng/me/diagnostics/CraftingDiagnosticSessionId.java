package appeng.me.diagnostics;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public final class CraftingDiagnosticSessionId implements Comparable<CraftingDiagnosticSessionId> {

    private final long value;

    private CraftingDiagnosticSessionId(final long value) {
        this.value = value;
    }

    public static CraftingDiagnosticSessionId of(final long value) {
        return value > 0L ? new CraftingDiagnosticSessionId(value) : null;
    }

    public static CraftingDiagnosticSessionId fromNBT(final NBTTagCompound tag, final String key) {
        if (tag == null || key == null || key.isEmpty()) {
            return null;
        }

        if (tag.hasKey(key, Constants.NBT.TAG_LONG)) {
            return of(tag.getLong(key));
        }

        if (tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            return fromSerializedString(tag.getString(key));
        }

        return null;
    }

    public static CraftingDiagnosticSessionId fromSerializedString(final String serializedId) {
        if (serializedId == null || serializedId.isEmpty()) {
            return null;
        }

        try {
            return of(Long.parseLong(serializedId, Character.MAX_RADIX));
        } catch (final NumberFormatException ignored) {
            long hash = 1125899906842597L;
            for (int i = 0; i < serializedId.length(); i++) {
                hash = 31L * hash + serializedId.charAt(i);
            }

            if (hash == Long.MIN_VALUE) {
                hash = Long.MAX_VALUE;
            }

            hash = Math.abs(hash);
            return of(hash == 0L ? 1L : hash);
        }
    }

    public long asLong() {
        return this.value;
    }

    public void writeToNBT(final NBTTagCompound tag, final String key) {
        tag.setLong(key, this.value);
    }

    @Override
    public int compareTo(final CraftingDiagnosticSessionId other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof final CraftingDiagnosticSessionId other)) {
            return false;
        }

        return this.value == other.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value, Character.MAX_RADIX);
    }
}
