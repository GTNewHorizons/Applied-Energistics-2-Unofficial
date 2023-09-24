package appeng.core.features.registries.entries;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.client.texture.ExtraBlockTextures;
import appeng.items.storage.ItemVoidStorageCell;
import appeng.me.storage.VoidCellInventory;

public class VoidCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && is.getItem() instanceof ItemVoidStorageCell;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider host, StorageChannel channel) {
        if (channel == StorageChannel.ITEMS && is != null && is.getItem() instanceof ItemVoidStorageCell) {
            return VoidCellInventory.getCell(is);
        }
        return null;
    }

    @Override
    public IIcon getTopTexture_Light() {
        return ExtraBlockTextures.BlockMEChestItems_Light.getIcon();
    }

    @Override
    public IIcon getTopTexture_Medium() {
        return ExtraBlockTextures.BlockMEChestItems_Medium.getIcon();
    }

    @Override
    public IIcon getTopTexture_Dark() {
        return ExtraBlockTextures.BlockMEChestItems_Dark.getIcon();
    }

    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
            IMEInventoryHandler inv, ItemStack is, StorageChannel chan) {
        // NO-OP
    }

    @Override
    public int getStatusForCell(ItemStack is, IMEInventory handler) {
        // There is always room to void items.
        return 1;
    }

    @Override
    public double cellIdleDrain(ItemStack is, IMEInventory handler) {
        // Power free
        return 0;
    }
}
