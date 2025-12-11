package appeng.items.contents;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.features.IWirelessTermHandler;
import appeng.api.parts.ICraftingTerminal;
import appeng.api.util.IConfigManager;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;

public class WirelessCraftingTerminalGuiObject extends WirelessTerminalGuiObject implements ICraftingTerminal {

    final String nbtPrefix = "crafting";
    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);

    public WirelessCraftingTerminalGuiObject(IWirelessTermHandler wh, ItemStack is, EntityPlayer ep, World w, int x,
            int y, int z) {
        super(wh, is, ep, w, x, y, z);
        readInventory();
    }

    @Override
    public void readInventory() {
        final NBTTagCompound tag = getItemStack().getTagCompound();
        craftingGrid.readFromNBT(tag, nbtPrefix);
    }

    @Override
    public void writeInventory() {
        final NBTTagCompound tag = getItemStack().getTagCompound();
        craftingGrid.writeToNBT(tag, this.nbtPrefix);
    }

    @Override
    public void saveChanges() {}

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
        if (mc != InvOperation.markDirty) writeInventory(); // couz spam
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {}

    @Override
    public IInventory getInventoryByName(String name) {
        return this.craftingGrid;
    }
}
