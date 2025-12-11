package appeng.tile.inventory;

import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.writeStackNBT;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.Platform;

public class IAEStackInventory {

    private final IIAEStackInventory aesInventory;
    private final IAEStack<?>[] inv;
    private final int size;
    private final StorageName storageName;

    public IAEStackInventory(final IIAEStackInventory te, final int size, StorageName storageName) {
        this.aesInventory = te;
        this.size = size;
        this.inv = new IAEStack<?>[size];
        this.storageName = storageName;
    }

    public IAEStackInventory(final IIAEStackInventory te, final int size) {
        this.aesInventory = te;
        this.size = size;
        this.inv = new IAEStack<?>[size];
        this.storageName = StorageName.NONE;
    }

    public boolean isEmpty() {
        for (int x = 0; x < this.size; x++) {
            if (this.getAEStackInSlot(x) != null) {
                return false;
            }
        }
        return true;
    }

    public IAEStack<?> getAEStackInSlot(final int n) {
        return this.inv[n];
    }

    public void putAEStackInSlot(final int n, IAEStack<?> aes) {
        this.inv[n] = aes;
        markDirty();
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = new NBTTagCompound();
        this.writeToNBT(c);
        data.setTag(name, c);
    }

    private void writeToNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final NBTTagCompound c = new NBTTagCompound();
                final IAEStack<?> tmp = this.inv[x];

                if (tmp != null) {
                    writeStackNBT(tmp, c, true);
                }

                target.setTag("#" + x, c);
            } catch (final Exception ignored) {}
        }
    }

    public void readFromNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = data.getCompoundTag(name);
        if (c != null) {
            this.readFromNBT(c);
        }
    }

    private void readFromNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final NBTTagCompound c = target.getCompoundTag("#" + x);

                if (c != null) {
                    final IAEStack<?> stack = readStackNBT(c, false);
                    if (stack != null && stack.getStackSize() == 0) stack.setStackSize(c.getInteger("Count"));
                    this.inv[x] = stack;
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    public int getSizeInventory() {
        return this.size;
    }

    public void markDirty() {
        if (this.aesInventory != null && Platform.isServer()) {
            this.aesInventory.saveAEStackInv();
        }
    }

    public StorageName getStorageName() {
        return storageName;
    }
}
