package appeng.integration.modules.NEIHelpers;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNEIBookmark;
import codechicken.nei.api.IBookmarkContainerHandler;

public class NEIAETerminalBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        for (ItemStack bookmarkItem : bookmarkItems) {
            int backupStackSize = bookmarkItem.stackSize;
            int bookmarkStackSize = bookmarkItem.stackSize;
            int maxStackSize = bookmarkItem.getMaxStackSize();
            try {
                while (bookmarkStackSize > 0) {
                    if (bookmarkStackSize <= maxStackSize) {
                        NetworkHandler.instance.sendToServer(new PacketNEIBookmark(packBookmarkItem(bookmarkItem)));
                        bookmarkStackSize = 0;
                    } else {
                        ItemStack splitStack = bookmarkItem.splitStack(maxStackSize);
                        NetworkHandler.instance.sendToServer(new PacketNEIBookmark(packBookmarkItem(splitStack)));
                        bookmarkStackSize -= maxStackSize;
                    }
                }
            } catch (final Exception | Error ignored) {}
            bookmarkItem.stackSize = backupStackSize;
        }
    }

    private NBTTagCompound packBookmarkItem(ItemStack bookmarkItem) throws IOException {
        final NBTTagCompound comp = new NBTTagCompound();
        bookmarkItem.writeToNBT(comp);
        return comp;
    }
}
