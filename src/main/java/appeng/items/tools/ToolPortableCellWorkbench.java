package appeng.items.tools;

import java.util.EnumSet;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.items.AEBaseItem;
import appeng.items.contents.PortableCellWorkbenchHost;
import appeng.util.Platform;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.common.Optional;

@Optional.InterfaceList(
        value = { @Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles"),
                @Optional.Interface(iface = "baubles.api.expanded.IBaubleExpanded", modid = "Baubles|Expanded") })
public class ToolPortableCellWorkbench extends AEBaseItem implements IGuiItem, IBauble, IBaubleExpanded {

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
        if (player == null || x < 0) {
            return null;
        }

        IInventory inventory = player.inventory;
        int inventorySlot = x;
        if (Platform.isBaublesLoaded && x >= Platform.baublesSlotsOffset) {
            inventory = BaublesApi.getBaubles(player);
            inventorySlot = x - Platform.baublesSlotsOffset;
        }
        if (inventory == null || inventorySlot >= inventory.getSizeInventory()
                || inventory.getStackInSlot(inventorySlot) != stack) {
            return null;
        }
        return new PortableCellWorkbenchHost(inventory, this, inventorySlot, x);
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.RING;
    }

    @Override
    @Optional.Method(modid = "Baubles|Expanded")
    public String[] getBaubleTypes(ItemStack itemStack) {
        return new String[] { AppEng.PORTABLE_CELL_WORKBENCH_BAUBLE_SLOT };
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onWornTick(ItemStack itemStack, EntityLivingBase player) {}

    @Override
    @Optional.Method(modid = "Baubles")
    public void onEquipped(ItemStack itemStack, EntityLivingBase player) {}

    @Override
    @Optional.Method(modid = "Baubles")
    public void onUnequipped(ItemStack itemStack, EntityLivingBase player) {}

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canEquip(ItemStack itemStack, EntityLivingBase player) {
        return true;
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canUnequip(ItemStack itemStack, EntityLivingBase player) {
        return true;
    }
}
