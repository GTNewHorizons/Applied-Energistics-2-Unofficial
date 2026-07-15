package appeng.crafting.v2;

import static appeng.util.Platform.readStackByte;
import static appeng.util.Platform.writeStackByte;

import appeng.api.storage.data.IAEStack;
import io.netty.buffer.ByteBuf;

public class ObjectAEStackSerializer<T extends IAEStack<?>> implements IObjectSerializer<T> {

    @Override
    public String id() {
        return ":s";
    }

    @Override
    public void write(ByteBuf buffer, T stack) {
        writeStackByte(stack, buffer);
    }

    @Override
    public T read(ByteBuf buffer) {
        return (T) readStackByte(buffer);
    }
}
