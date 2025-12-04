package appeng.util.item;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;

public class AEItemStackType implements IAEStackType<IAEItemStack> {

    public static final AEItemStackType ITEM_STACK_TYPE = new AEItemStackType();
    public static final String ITEM_STACK_ID = "item";

    @Override
    public String getId() {
        return ITEM_STACK_ID;
    }

    @Override
    public IAEItemStack loadStackFromNBT(NBTTagCompound tag) {
        return AEItemStack.loadItemStackFromNBT(tag);
    }

    @Override
    public IAEItemStack loadStackFromByte(ByteBuf buffer) throws IOException {
        return AEItemStack.loadItemStackFromPacket(buffer);
    }

    @Override
    public IItemList<IAEItemStack> createList() {
        return AEApi.instance().storage().createItemList();
    }

    @Override
    public IItemList<IAEItemStack> createPrimitiveList() {
        return AEApi.instance().storage().createPrimitiveItemList();
    }

    @Override
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        return false;
    }

    @Override
    public @Nullable IAEItemStack getStackFromContainerItem(@NotNull ItemStack container) {
        return null;
    }

    @Override
    public @Nullable IAEItemStack convertStackFromItem(@NotNull ItemStack itemStack) {
        return null;
    }

    @Override
    public long drainStackFromContainer(@NotNull ItemStack container, @NotNull IAEItemStack stack) {
        return 0;
    }

    @Override
    public @Nullable ItemStack clearFilledContainer(@NotNull ItemStack container) {
        return null;
    }

    @Override
    public @NotNull ObjectIntPair<ItemStack> fillContainer(@NotNull ItemStack container, @NotNull IAEItemStack stack) {
        return new ObjectIntImmutablePair<>(null, 0);
    }
}
