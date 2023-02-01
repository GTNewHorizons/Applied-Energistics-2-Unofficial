package appeng.items.storage;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.core.features.AEFeature;

import com.google.common.base.Optional;

public class ItemExtremeStorageCell extends ItemBasicStorageCell {

    protected final int totalTypes;

    @SuppressWarnings("Guava")
    public ItemExtremeStorageCell(String name, long bytes, int types, int perType, double drain) {
        super(Optional.of(name));
        this.setFeature(EnumSet.of(AEFeature.StorageCells));
        this.setMaxStackSize(1);
        this.totalBytes = bytes;
        this.perType = perType;
        this.totalTypes = types;
        this.idleDrain = drain;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return totalTypes;
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        return stack;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        return false;
    }

    @Override
    public ItemStack getContainerItem(final ItemStack itemStack) {
        return null;
    }

    @Override
    public boolean hasContainerItem(final ItemStack stack) {
        return false;
    }

}
