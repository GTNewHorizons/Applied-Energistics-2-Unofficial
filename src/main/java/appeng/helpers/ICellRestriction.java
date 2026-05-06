package appeng.helpers;

import net.minecraft.item.ItemStack;

import appeng.container.guisync.IGuiPacketWritable;
import io.netty.buffer.ByteBuf;

public interface ICellRestriction {

    class CellData implements IGuiPacketWritable {

        public final long totalBytes;
        public final int totalTypes;
        public final int perType;
        public final int perByte;

        public CellData(final long totalBytes, final int totalTypes, final int perType, final int perByte) {
            this.totalBytes = totalBytes;
            this.totalTypes = totalTypes;
            this.perType = perType;
            this.perByte = perByte;
        }

        public CellData(ByteBuf buf) {
            this.totalBytes = buf.readLong();
            this.totalTypes = buf.readInt();
            this.perType = buf.readInt();
            this.perByte = buf.readInt();
        }

        public CellData copy() {
            return new CellData(this.totalBytes, this.totalTypes, this.perType, this.perByte);
        }

        @Override
        public void writeToPacket(ByteBuf buf) {
            buf.writeLong(this.totalBytes);
            buf.writeInt(this.totalTypes);
            buf.writeInt(this.perType);
            buf.writeInt(this.perByte);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CellData cd) {
                return cd.totalBytes == this.totalBytes && cd.totalTypes == this.totalTypes
                        && cd.perType == this.perType
                        && cd.perByte == this.perByte;
            }
            return false;
        }
    }

    class CellRestrictionData implements IGuiPacketWritable {

        public final byte restrictionTypes;
        public final long restrictionAmount;

        public CellRestrictionData(final byte restrictionTypes, final long restrictionAmount) {
            this.restrictionTypes = restrictionTypes;
            this.restrictionAmount = restrictionAmount;
        }

        public CellRestrictionData(ByteBuf buf) {
            this.restrictionTypes = buf.readByte();
            this.restrictionAmount = buf.readLong();
        }

        public CellRestrictionData copy() {
            return new CellRestrictionData(this.restrictionTypes, this.restrictionAmount);
        }

        @Override
        public void writeToPacket(ByteBuf buf) {
            buf.writeByte(this.restrictionTypes);
            buf.writeLong(this.restrictionAmount);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CellRestrictionData crd) {
                return crd.restrictionTypes == this.restrictionTypes && crd.restrictionAmount == this.restrictionAmount;
            }
            return false;
        }

        public boolean isReset() {
            return this.restrictionTypes == 0 && this.restrictionAmount == 0;
        }
    }

    CellData getCellData(ItemStack is);

    CellRestrictionData getCellRestrictionData(ItemStack is);

    void setCellRestriction(ItemStack is, CellRestrictionData newRestriction);
}
