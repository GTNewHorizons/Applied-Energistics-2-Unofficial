package appeng.tile.inventory;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.core.AELog;
import appeng.util.Platform;

public class BiggerAppEngInventory extends AppEngInternalInventory {

    public BiggerAppEngInventory(IAEAppEngInventory inventory, int size) {
        super(inventory, size);
    }

    @Override
    protected void writeToNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.getSizeInventory(); x++) {
            try {
                if (this.inv[x] != null) {
                    final NBTTagCompound c = new NBTTagCompound();
                    Platform.writeItemStackToNBT(this.inv[x], c);
                    target.setTag("#" + x, c);
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.getSizeInventory(); x++) {
            try {
                final String key = "#" + x;
                if (target.hasKey(key, NBT.TAG_COMPOUND)) {
                    final NBTTagCompound c = target.getCompoundTag(key);
                    if (c != null) {
                        this.inv[x] = Platform.loadItemStackFromNBT(c);
                    }
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }
}
