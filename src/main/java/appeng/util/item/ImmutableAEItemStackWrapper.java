package appeng.util.item;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;

public class ImmutableAEItemStackWrapper implements ImmutableItemStack {

    public IAEItemStack stack;

    public ImmutableAEItemStackWrapper() {}

    public ImmutableAEItemStackWrapper(IAEItemStack stack) {
        this.stack = stack;
    }

    public ImmutableAEItemStackWrapper set(IAEItemStack stack) {
        this.stack = stack;
        return this;
    }

    @Override
    public int getStackSize() {
        return Platform.longToInt(stack.getStackSize());
    }

    @Override
    public NBTTagCompound getTag() {
        return (AESharedNBT) stack.getTagCompound();
    }

    @Override
    public @NotNull Item getItem() {
        return stack.getItem();
    }

    @Override
    public int getItemMeta() {
        return stack.getItemDamage();
    }
}
