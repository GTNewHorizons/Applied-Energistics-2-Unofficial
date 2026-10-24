package appeng.helpers;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.DimensionalCoord;
import io.netty.buffer.ByteBuf;

/**
 * Wireless endpoint identity: block position + attachment side (UNKNOWN = block-form host). Save compat: side UNKNOWN
 * serializes exactly like a bare {@link DimensionalCoord}, and reads use hasKey so legacy entries stay UNKNOWN.
 */
public final class WirelessAnchor {

    private static final String NBT_SIDE = "side";

    private final int x;
    private final int y;
    private final int z;
    private final int dim;
    private final ForgeDirection side;

    public WirelessAnchor(final DimensionalCoord coord, final ForgeDirection side) {
        this(coord.x, coord.y, coord.z, coord.getDimension(), side);
    }

    public WirelessAnchor(final int x, final int y, final int z, final int dim, final ForgeDirection side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    public DimensionalCoord getCoord() {
        return new DimensionalCoord(this.x, this.y, this.z, this.dim);
    }

    public ForgeDirection getSide() {
        return this.side;
    }

    public int getDimension() {
        return this.dim;
    }

    public boolean isBlockForm() {
        return this.side == ForgeDirection.UNKNOWN;
    }

    public String getGuiTextShort() {
        return this.getCoord().getGuiTextShort() + this.sideSuffix();
    }

    public String getGuiTextShortNoDim() {
        return this.getCoord().getGuiTextShortNoDim() + this.sideSuffix();
    }

    public String getSideSuffix() {
        return this.sideSuffix();
    }

    private String sideSuffix() {
        if (this.side == ForgeDirection.UNKNOWN) return "";
        return " " + this.side.name().charAt(0);
    }

    public void writeToNBT(final NBTTagCompound data) {
        this.getCoord().writeToNBT(data);
        if (this.side != ForgeDirection.UNKNOWN) {
            data.setByte(NBT_SIDE, (byte) this.side.ordinal());
        }
    }

    public static WirelessAnchor readFromNBT(final NBTTagCompound data) {
        final DimensionalCoord coord = DimensionalCoord.readFromNBT(data);
        final ForgeDirection side = data.hasKey(NBT_SIDE) ? ForgeDirection.getOrientation(data.getByte(NBT_SIDE))
                : ForgeDirection.UNKNOWN;
        return new WirelessAnchor(coord, side);
    }

    public static void writeListToNBT(final NBTTagCompound tag, final List<WirelessAnchor> list) {
        int i = 0;
        for (final WirelessAnchor anchor : list) {
            final NBTTagCompound data = new NBTTagCompound();
            anchor.writeToNBT(data);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static List<WirelessAnchor> readAsListFromNBT(final NBTTagCompound tag) {
        final List<WirelessAnchor> list = new ArrayList<>();
        int i = 0;
        while (tag.hasKey("pos#" + i)) {
            list.add(readFromNBT(tag.getCompoundTag("pos#" + i)));
            i++;
        }
        return list;
    }

    // packets run same-version on both ends, so the side byte is always written
    public void writeToPacket(final ByteBuf data) {
        this.getCoord().writeToPacket(data);
        data.writeByte(this.side.ordinal());
    }

    public static WirelessAnchor readFromPacket(final ByteBuf data) {
        final DimensionalCoord coord = DimensionalCoord.readFromPacket(data);
        return new WirelessAnchor(coord, ForgeDirection.getOrientation(data.readByte()));
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof WirelessAnchor other)) return false;
        return this.x == other.x && this.y == other.y
                && this.z == other.z
                && this.dim == other.dim
                && this.side == other.side;
    }

    @Override
    public int hashCode() {
        int hash = this.x;
        hash = hash * 31 + this.y;
        hash = hash * 31 + this.z;
        hash = hash * 31 + this.dim;
        hash = hash * 31 + this.side.ordinal();
        return hash;
    }

    @Override
    public String toString() {
        return "WirelessAnchor{x=" + this.x
                + ", y="
                + this.y
                + ", z="
                + this.z
                + ", dim="
                + this.dim
                + ", side="
                + this.side
                + "}";
    }
}
