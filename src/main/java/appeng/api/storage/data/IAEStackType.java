package appeng.api.storage.data;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.netty.buffer.ByteBuf;

public interface IAEStackType<T extends IAEStack> {

    String getId();

    T loadStackFromNBT(NBTTagCompound tag);

    T loadStackFromByte(ByteBuf buffer) throws IOException;

    IItemList<T> createList();

    default IItemList<T> createPrimitiveList() {
        return this.createList();
    }

    boolean isContainerItemForType(@NotNull ItemStack container);

    /**
     * @param container container item for this type
     * @return AEStack with correct stack size
     */
    @Nullable
    T getStackFromContainerItem(@NotNull ItemStack container);

    /**
     * mainly used for GT Fluid Display and TC4 aspect item
     * 
     * @param itemStack should not be container
     * @return AEStack
     */
    @Nullable
    T convertStackFromItem(@NotNull ItemStack itemStack);
}
