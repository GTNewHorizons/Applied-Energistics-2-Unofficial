package appeng.integration.modules.NEIHelpers;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class NEIAEBookmarkContainerHandler implements IBookmarkContainerHandler {

    protected static Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        List<ItemStack> containerStacks = guiContainer.inventorySlots.getInventory();
        for (ItemStack bookmarkItem : bookmarkItems) {

            int bookmarkSizeBackup = bookmarkItem.stackSize;

            for (int i = 0; i < containerStacks.size() - 4 * 9; i++) {
                ItemStack containerItem = containerStacks.get(i);

                if (containerItem == null) {
                    continue;
                }

                if (bookmarkItem.isItemEqual(containerItem)) {
                    if (bookmarkItem.stackSize <= 0) {
                        break;
                    }

                    int transferAmount = Math.min(bookmarkItem.stackSize, containerItem.stackSize);

                    moveItems(guiContainer, i, transferAmount);
                    bookmarkItem.stackSize -= transferAmount;

                    if (bookmarkItem.stackSize == 0) {
                        break;
                    }
                }
            }
            bookmarkItem.stackSize = bookmarkSizeBackup;
        }
    }

    private void moveItems(GuiContainer container, int fromSlot, int transferAmount) {
        for (int i = 0; i < transferAmount; i++) {
            int toSlot = findValidPlayerInventoryDestination(container.inventorySlots, fromSlot);
            if (toSlot == -1) {
                return;
            }
            clickSlot(container, fromSlot, 0);
            clickSlot(container, toSlot, 1);
            clickSlot(container, fromSlot, 0);
        }
    }

    private void clickSlot(GuiContainer container, int slotIdx, int button) {
        mc.playerController.windowClick(container.inventorySlots.windowId, slotIdx, button, 0, mc.thePlayer);
    }

    private int findValidPlayerInventoryDestination(Container container, int fromSlot) {
        ItemStack stackToMove = container.getSlot(fromSlot).getStack();
        for (int i = container.getInventory().size() - 4 * 9; i < container.getInventory().size(); i++) {
            if (container.getInventory().get(i) == null) {
                return i;
            }
            int diff = stackToMove.getMaxStackSize() - container.getInventory().get(i).stackSize;
            if (container.getInventory().get(i).isItemEqual(stackToMove) && diff > 0) {
                return i;
            }
        }
        return -1;
    }
}
