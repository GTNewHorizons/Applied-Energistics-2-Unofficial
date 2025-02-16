package appeng.tile.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.core.AELog;
import appeng.helpers.ItemStackLong;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.util.Platform;

public class AppEngInternalInventoryLong {

    private final int size;
    protected final ItemStackLong[] inv;
    private boolean enableClientEvents = false;
    private PartPatternTerminal te;
    private long maxStack;
    private boolean ignoreStackLimit = false;

    public AppEngInternalInventoryLong(final PartPatternTerminal inventory, final int size) {
        this(inventory, size, 64);
    }

    public AppEngInternalInventoryLong(final PartPatternTerminal inventory, final int size, final int maxstack) {
        this.setTileEntity(inventory);
        this.size = size;
        this.maxStack = maxstack;
        this.inv = new ItemStackLong[size];
    }

    public AppEngInternalInventoryLong(final PartPatternTerminal inventory, final int size, final int maxstack,
            final boolean ignoreStackLimit) {
        this(inventory, size, maxstack);
        this.ignoreStackLimit = ignoreStackLimit;
    }

    public boolean isEmpty() {
        for (int x = 0; x < this.size; x++) {
            if (this.getStackInSlot(x) != null) {
                return false;
            }
        }
        return true;
    }

    public int getSizeInventory() {
        return this.size;
    }

    public ItemStackLong getStackInSlot(final int var1) {
        return this.inv[var1];
    }

    protected boolean eventsEnabled() {
        return Platform.isServer() || this.isEnableClientEvents();
    }

    public ItemStack getStackInSlotOnClosing(final int var1) {
        return null;
    }

    public void setInventorySlotContents(final int slot, final ItemStackLong newItemStack) {
        final ItemStackLong oldStack = this.inv[slot];
        this.inv[slot] = newItemStack;

        if (this.getTileEntity() != null && this.eventsEnabled()) {
            ItemStackLong removed = oldStack;
            ItemStackLong added = newItemStack;

            if (oldStack != null && newItemStack != null
                    && Platform.isSameItemPrecise(oldStack.itemStack, newItemStack.itemStack)) {
                if (oldStack.stackSize > newItemStack.stackSize) {
                    removed = removed.copy();
                    removed.stackSize -= newItemStack.stackSize;
                    added = null;
                } else if (oldStack.stackSize < newItemStack.stackSize) {
                    added = added.copy();
                    added.stackSize -= oldStack.stackSize;
                    removed = null;
                } else {
                    removed = added = null;
                }
            }

            this.getTileEntity().onChangeInventory(this, slot, InvOperation.setInventorySlotContents, removed, added);
        }

        this.markDirty();
    }

    public String getInventoryName() {
        return "appeng-internal";
    }

    public boolean hasCustomInventoryName() {
        return false;
    }

    public long getInventoryStackLimit() {
        return Long.MAX_VALUE;
    }

    public void markDirty() {
        if (this.getTileEntity() != null && this.eventsEnabled()) {
            this.getTileEntity().onChangeInventory(this, -1, InvOperation.markDirty, null, null);
        }
    }

    public boolean isUseableByPlayer(final EntityPlayer var1) {
        return true;
    }

    public void openInventory() {}

    public void closeInventory() {}

    public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
        return true;
    }

    public void setMaxStackSize(final int s) {
        this.maxStack = s;
    }

    // for guis...
    public void markDirty(final int slotIndex) {
        if (this.getTileEntity() != null && this.eventsEnabled()) {
            this.getTileEntity().onChangeInventory(this, slotIndex, InvOperation.markDirty, null, null);
        }
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = new NBTTagCompound();
        this.writeToNBT(c);
        data.setTag(name, c);
    }

    protected void writeToNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final NBTTagCompound c = new NBTTagCompound();

                if (this.inv[x] != null) {
                    this.inv[x].writeToNBT(c);
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

    public void readFromNBT(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final NBTTagCompound c = target.getCompoundTag("#" + x);

                if (c != null) {
                    this.inv[x] = ItemStackLong.loadItemStackFromNBT(c);
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    private boolean isEnableClientEvents() {
        return this.enableClientEvents;
    }

    public void setEnableClientEvents(final boolean enableClientEvents) {
        this.enableClientEvents = enableClientEvents;
    }

    private PartPatternTerminal getTileEntity() {
        return this.te;
    }

    public void setTileEntity(final PartPatternTerminal te) {
        this.te = te;
    }
}
