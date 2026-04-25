package appeng.items.contents;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

public class CellConfigLegacy implements IInventory {

    private final IAEStackInventory config;
    private final IAEStackType<?> type;

    public CellConfigLegacy(IAEStackInventory config, IAEStackType<?> type) {
        this.config = config;
        this.type = type;
    }

    @Override
    public int getSizeInventory() {
        return 63;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        if (this.config.getAEStackInSlot(slotIn) instanceof IAEItemStack ais) {
            return ais.getItemStack();
        }
        return null;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (this.config.getAEStackInSlot(index) instanceof IAEItemStack ais) {
            if (count >= ais.getStackSize()) {
                this.config.putAEStackInSlot(index, null);
            } else {
                ais.decStackSize(count);
            }

            this.markDirty();
            return ais.getItemStack();
        }

        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (this.type == ITEM_STACK_TYPE) {
            this.config.putAEStackInSlot(index, AEItemStack.create(stack));
        }
    }

    @Override
    public String getInventoryName() {
        return "legacy-cell-config";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        this.config.markDirty();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {

    }

    @Override
    public void closeInventory() {

    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }
}
