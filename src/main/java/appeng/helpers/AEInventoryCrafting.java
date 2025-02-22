package appeng.helpers;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;

import appeng.api.storage.data.IAEItemStack;

public class AEInventoryCrafting extends InventoryCrafting {

    private IAEItemStack[] stackListAE;
    private int inventoryWidthAE;
    private Container eventHandler;

    public AEInventoryCrafting(Container cont, int width, int height) {
        super(cont, width, height);
        int k = width * height;
        this.stackListAE = new IAEItemStack[k];
        this.eventHandler = cont;
        this.inventoryWidthAE = width;
    }

    public IAEItemStack getAEStackInSlot(int slotIn) {
        return slotIn >= this.getSizeInventory() ? null : this.stackListAE[slotIn];
    }

    public IAEItemStack getAEStackInRowAndColumn(int row, int column) {
        if (row >= 0 && row < this.inventoryWidthAE) {
            int k = row + column * this.inventoryWidthAE;
            return this.getAEStackInSlot(k);
        } else {
            return null;
        }
    }

    @Override
    public String getInventoryName() {
        return "containerAE.crafting";
    }

    public IAEItemStack getAEStackInSlotOnClosing(int index) {
        if (this.stackListAE[index] != null) {
            IAEItemStack ais = this.stackListAE[index];
            this.stackListAE[index] = null;
            return ais;
        } else {
            return null;
        }
    }

    public IAEItemStack decrAEStackSize(int index, int count) {
        super.decrStackSize(index, count);
        if (this.stackListAE[index] != null) {
            IAEItemStack ais;

            if (this.stackListAE[index].getStackSize() <= count) {
                ais = this.stackListAE[index];
                this.stackListAE[index] = null;
                this.eventHandler.onCraftMatrixChanged(this);
                return ais;
            } else {
                this.stackListAE[index].decStackSize(count);
                ais = this.stackListAE[index].copy();

                if (this.stackListAE[index].getStackSize() == 0) {
                    this.stackListAE[index] = null;
                }

                this.eventHandler.onCraftMatrixChanged(this);
                return ais;
            }
        } else {
            return null;
        }
    }

    public void setInventorySlotContents(int index, IAEItemStack stack) {
        this.stackListAE[index] = stack;
        this.eventHandler.onCraftMatrixChanged(this);
        super.setInventorySlotContents(index, stack.getItemStack());
    }

    public boolean isItemValidForSlot(int index, IAEItemStack stack) {
        return true;
    }
}
