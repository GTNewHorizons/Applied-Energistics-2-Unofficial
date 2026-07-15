package appeng.crafting.v2;

import io.netty.buffer.ByteBuf;

/**
 * Interface for objects in the crafting tree that can be serialized and deduplicated
 */
public interface IObjectSerializer<T> {

    String id();

    void write(ByteBuf buffer, T obj);

    T read(ByteBuf buffer);
}
