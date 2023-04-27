package appeng.integration.modules.helpers;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel;

public class JabbaBarrel implements IMEInventory<IAEItemStack> {

    private final TileEntityBarrel barrel;

    public JabbaBarrel(final TileEntity te) {
        barrel = (TileEntityBarrel) te;
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final BaseActionSource src) {
        final ItemStack is = this.barrel.getStoredItemType();
        if (is != null) {
            if (input.equals(is)) {
                final long max = this.barrel.getMaxStoredCount();
                long storedItems = is.stackSize;
                if (max == storedItems) {
                    return input;
                }

                storedItems += input.getStackSize();
                if (storedItems > max && !barrel.getStorage().isVoid()) {
                    final IAEItemStack overflow = AEItemStack.create(is);
                    overflow.setStackSize(storedItems - max);
                    if (mode == Actionable.MODULATE) {
                        this.barrel.setStoredItemCount((int) max);
                    }
                    return overflow;
                } else {
                    if (mode == Actionable.MODULATE) {
                        this.barrel.setStoredItemCount(is.stackSize + (int) input.getStackSize());
                    }
                    return null;
                }
            }
        } else {
            if (input.getTagCompound() != null) {
                return input;
            }
            long max = ((long) this.barrel.getStorage().getMaxStacks()) * input.getItemStack().getMaxStackSize();
            if (input.getStackSize() <= max || barrel.getStorage().isVoid()) {
                if (mode == Actionable.MODULATE) {
                    this.barrel.setStoredItemType(input.getItemStack(), (int) input.getStackSize());
                }
            } else {
                final IAEItemStack overflow = AEItemStack.create(input.getItemStack());
                overflow.setStackSize(input.getStackSize() - max);
                if (mode == Actionable.MODULATE) {
                    this.barrel.setStoredItemType(input.getItemStack(), (int) max);
                }
                return overflow;
            }
            return null;
        }
        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        ItemStack is = this.barrel.getStoredItemType();
        if (request.equals(is)) {
            if (request.getStackSize() >= is.stackSize) {
                is = is.copy();
                if (mode == Actionable.MODULATE) {
                    this.barrel.setStoredItemCount(0);
                }
                return AEItemStack.create(is);
            } else {
                if (mode == Actionable.MODULATE) {
                    this.barrel.setStoredItemCount(is.stackSize - (int) request.getStackSize());
                }
                return request.copy();
            }
        }
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        final ItemStack is = this.barrel.getStoredItemType();
        if (is != null) {
            out.add(AEItemStack.create(is));
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }
}
