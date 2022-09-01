package appeng.tile.inventory;

import appeng.core.AELog;
import appeng.util.Platform;
import net.minecraft.nbt.NBTTagCompound;

public class BiggerAppEngInventory extends AppEngInternalInventory {

    public BiggerAppEngInventory(IAEAppEngInventory inventory, int size) {
        super(inventory, size);
    }

    protected void writeToNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.getSizeInventory(); x++) {
            try {
                final NBTTagCompound c = new NBTTagCompound();

                if (this.inv[x] != null) {
                    Platform.writeItemStackToNBT(this.inv[x], c);
                }

                target.setTag("#" + x, c);
            } catch (final Exception ignored) {
            }
        }
    }

    public void readFromNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.getSizeInventory(); x++) {
            try {
                final NBTTagCompound c = target.getCompoundTag("#" + x);

                if (c != null) {
                    this.inv[x] = Platform.loadItemStackFromNBT(c);
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }
}
