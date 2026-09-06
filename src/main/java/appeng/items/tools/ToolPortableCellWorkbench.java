package appeng.items.tools;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.items.AEBaseItem;
import appeng.items.contents.PortableCellWorkbenchHost;
import appeng.util.Platform;

public class ToolPortableCellWorkbench extends AEBaseItem implements IGuiItem {

    public ToolPortableCellWorkbench() {
        this.setFeature(EnumSet.of(AEFeature.PortableCellWorkbench));
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        Platform.openGUI(player, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_CELL_WORKBENCH);
        return stack;
    }

    @Override
    public IGuiItemObject getGuiObject(ItemStack stack, World world, EntityPlayer player, int x, int y, int z) {
        if (player == null || x < 0
                || x >= player.inventory.getSizeInventory()
                || player.inventory.getStackInSlot(x) != stack) {
            return null;
        }
        return new PortableCellWorkbenchHost(player.inventory, this, x);
    }
}
