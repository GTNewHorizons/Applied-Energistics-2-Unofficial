package appeng.util.inv;

import static appeng.util.Platform.stackConvertPacket;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;

import appeng.api.storage.data.IAEStack;

public class MEInventoryCrafting extends InventoryCrafting {

    IAEStack<?>[] aeStackList;

    public MEInventoryCrafting(Container cont, int width, int height) {
        super(cont, width, height);
        aeStackList = new IAEStack<?>[width * height];
    }

    public IAEStack<?> getAEStackInSlot(int slotIn) {
        return slotIn >= this.getSizeInventory() ? null : aeStackList[slotIn];
    }

    public void setInventorySlotContents(int index, IAEStack<?> stack) {
        aeStackList[index] = stack;
        setInventorySlotContents(index, stack == null ? null : stackConvertPacket(stack).getItemStack());
    }
}
