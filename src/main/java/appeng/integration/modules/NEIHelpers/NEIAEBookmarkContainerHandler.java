package appeng.integration.modules.NEIHelpers;

import java.util.ArrayList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class NEIAEBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> realItems) {
        System.out.println(guiContainer);
    }
}
