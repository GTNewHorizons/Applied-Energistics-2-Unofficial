package appeng.crafting.v2;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.*;

import appeng.api.storage.data.IAEStack;
import appeng.core.AEConfig;
import appeng.crafting.v2.resolvers.*;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

import com.google.common.base.Throwables;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Walks down the tree of resolved crafting operations and (de)serializes them into a flat ByteBuf for network
 * transmission.
 */
public final class CraftingTreeSerializer {

    private static final Map<Class<? extends ITreeSerializable>, String> serializableKeys = new HashMap<>();
    private static final Map<String, MethodHandle> serializableConstructors = new HashMap<>();
    private final boolean reading;
    private final ByteBuf buffer;

    private ArrayList<ITreeSerializable> workStack = new ArrayList<>(32);

    /**
     * Registers a serializable type for the crafting tree.
     * 
     * @param id    A short, unique String identifying the type. Prefix with mod id to prevent conflicts.
     * @param klass The type to register
     */
    public static void registerSerializable(String id, Class<? extends ITreeSerializable> klass) {
        final MethodHandle constructor;
        try {
            constructor = MethodHandles.publicLookup()
                    .findConstructor(klass, MethodType.methodType(void.class, CraftingTreeSerializer.class))
                    .asType(MethodType.methodType(ITreeSerializable.class, CraftingTreeSerializer.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Invalid ITreeSerializable implementation, does not provide a public constructor taking in a CraftingTreeSerializer argument",
                    e);
        }
        if ((serializableKeys.put(klass, id) != null) || (serializableConstructors.put(id, constructor) != null)) {
            throw new IllegalArgumentException("Duplicate ITreeSerializable id: " + id);
        }
    }

    static {
        // modid:type, using empty modid for ae2 for compactness
        registerSerializable(":j", CraftingJobV2.class);
        registerSerializable(":r", CraftingRequest.class);
        registerSerializable(":re", CraftingRequest.UsedResolverEntry.class);
        registerSerializable(":tc", CraftableItemResolver.CraftFromPatternTask.class);
        registerSerializable(":te", EmitableItemResolver.EmitItemTask.class);
        registerSerializable(":tx", ExtractItemResolver.ExtractItemTask.class);
        registerSerializable(":ts", SimulateMissingItemResolver.ConjureItemTask.class);
    }

    /**
     * Creates a serializing instance
     */
    public CraftingTreeSerializer() {
        this.buffer = Unpooled.buffer(4096, AEConfig.instance.maxCraftingTreeVisualizationSize)
                .order(ByteOrder.LITTLE_ENDIAN);
        this.reading = false;
    }

    /**
     * Creates a deserializing instance
     * 
     * @param toDeserialize The buffer received to deserialize
     */
    public CraftingTreeSerializer(final ByteBuf toDeserialize) {
        toDeserialize.order(ByteOrder.LITTLE_ENDIAN);
        this.buffer = toDeserialize;
        this.reading = true;
    }

    public ByteBuf getBuffer() {
        return buffer;
    }

    public void writeSerializableAndQueueChildren(ITreeSerializable obj) throws IOException {
        final String key = serializableKeys.get(obj.getClass());
        if (key == null) {
            throw new IllegalArgumentException("Unregistered ITreeSerializable: " + obj.getClass());
        }
        ByteBufUtils.writeUTF8String(buffer, key);
        List<? extends ITreeSerializable> children = obj.serializeTree(this);
        ByteBufUtils.writeVarInt(buffer, children.size(), 8);
        for (int i = children.size(); i >= 0; i--) {
            workStack.add(children.get(i));
        }
    }

    public ITreeSerializable readSerializableAndQueueChildren() throws IOException {
        final String key = ByteBufUtils.readUTF8String(buffer);
        if (key == null) {
            throw new IllegalArgumentException("No key provided");
        }
        final MethodHandle constructor = serializableConstructors.get(key);
        if (constructor == null) {
            throw new IllegalArgumentException("No constructor for key " + key);
        }
        final ITreeSerializable value;
        try {
            value = (ITreeSerializable) constructor.invokeExact((CraftingTreeSerializer) this);
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
        int childCount = ByteBufUtils.readVarInt(buffer, 8);
        return value;
    }

    public void writeEnum(Enum<?> value) throws IOException {
        buffer.writeByte(value.ordinal());
    }

    public <T extends Enum<T>> T readEnum(Class<T> type) {
        final byte ordinal = buffer.readByte();
        return type.getEnumConstants()[ordinal];
    }

    private final byte ST_NULL = 0;
    private final byte ST_ITEM = 1;
    private final byte ST_FLUID = 2;

    public void writeStack(IAEStack<?> stack) throws IOException {
        if (stack == null) {
            buffer.writeByte(ST_NULL);
        } else if (stack instanceof AEItemStack) {
            buffer.writeByte(ST_ITEM);
        } else if (stack instanceof AEFluidStack) {
            buffer.writeByte(ST_FLUID);
        } else {
            throw new UnsupportedOperationException("Can't serialize a stack of type " + stack.getClass());
        }
        stack.writeToPacket(buffer);
    }

    public IAEStack<?> readStack() throws IOException {
        final byte stackType = buffer.readByte();
        switch (stackType) {
            case ST_NULL:
                return null;
            case ST_ITEM:
                return AEItemStack.loadItemStackFromPacket(buffer);
            case ST_FLUID:
                return AEFluidStack.loadFluidStackFromPacket(buffer);
            default:
                throw new UnsupportedOperationException("Unknown stack type " + stackType);
        }
    }
}
