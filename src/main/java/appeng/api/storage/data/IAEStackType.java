package appeng.api.storage.data;

import java.io.IOException;

import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

public interface IAEStackType<T extends IAEStack> {

    String getId();

    T loadStackFromNBT(NBTTagCompound tag);

    T loadStackFromByte(ByteBuf buffer) throws IOException;

    IItemList<T> createList();

    default IItemList<T> createPrimitiveList() {
        return this.createList();
    }
}
