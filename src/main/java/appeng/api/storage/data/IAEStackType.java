package appeng.api.storage.data;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

public interface IAEStackType<T extends IAEStack> {

    String getId();

    T loadStackFromNBT(NBTTagCompound tag);

    T loadStackFromByte(ByteBuf buffer) throws IOException;

    IItemList<T> createList();

    default IItemList<T> createPrimitiveList() {
        return this.createList();
    }

    @Range(from = 1, to = Integer.MAX_VALUE)
    int getAmountPerUnit();

    boolean isContainerItemForType(@Nullable ItemStack container);

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

    /**
     * @param container container item for this type
     * @param stack     type for capacity. For containers that only accept certain types.
     * @return capacity for stack in param
     */
    long getContainerItemCapacity(@NotNull ItemStack container, @NotNull T stack);

    /**
     * @param stack to drain with amount
     * @return drained container and drained amount, or null if not drained
     */
    @NotNull
    ObjectLongPair<ItemStack> drainStackFromContainer(@NotNull ItemStack container, @NotNull T stack);

    /**
     * @param container filled container
     * @return empty container
     */
    @Nullable
    ItemStack clearFilledContainer(@NotNull ItemStack container);

    /**
     * @return filled container and amount
     */
    @NotNull
    ObjectLongPair<ItemStack> fillContainer(@NotNull ItemStack container, @NotNull T stack);
}
