package appeng.crafting.v2;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import com.google.common.base.Throwables;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AEConfig;
import appeng.crafting.v2.resolvers.CraftableItemResolver;
import appeng.crafting.v2.resolvers.EmitableItemResolver;
import appeng.crafting.v2.resolvers.ExtractItemResolver;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver.IgnoreMissingItemTask;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
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
    private final World world;
    private final boolean reading;
    private final ByteBuf buffer;

    private ArrayList<JobFn> workStack = new ArrayList<>(32);

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
                    .findConstructor(
                            klass,
                            MethodType.methodType(void.class, CraftingTreeSerializer.class, ITreeSerializable.class))
                    .asType(
                            MethodType.methodType(
                                    ITreeSerializable.class,
                                    CraftingTreeSerializer.class,
                                    ITreeSerializable.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Invalid ITreeSerializable implementation, does not provide a public constructor with a signature of (CraftingTreeSerializer serializer, ITreeSerializable parent)",
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
        registerSerializable(":tcr", CraftableItemResolver.RequestAndPerCraftAmount.class);
        registerSerializable(":te", EmitableItemResolver.EmitItemTask.class);
        registerSerializable(":tx", ExtractItemResolver.ExtractItemTask.class);
        registerSerializable(":ts", SimulateMissingItemResolver.ConjureItemTask.class);
        registerSerializable(":tp", IgnoreMissingItemTask.class);
    }

    /**
     * Creates a serializing instance
     *
     * @param world The world of the AE system in which the tree is serialized
     */
    public CraftingTreeSerializer(final World world) {
        this.buffer = Unpooled.buffer(4096, AEConfig.instance.maxCraftingTreeVisualizationSize)
                .order(ByteOrder.LITTLE_ENDIAN);
        this.reading = false;
        this.world = world;
    }

    /**
     * Creates a deserializing instance
     *
     * @param world         The world to deserialize the tree in
     * @param toDeserialize The buffer received to deserialize
     */
    public CraftingTreeSerializer(final World world, final ByteBuf toDeserialize) {
        toDeserialize.order(ByteOrder.LITTLE_ENDIAN);
        this.buffer = toDeserialize;
        this.reading = true;
        this.world = world;
    }

    public ByteBuf getBuffer() {
        return buffer;
    }

    public void writeSerializableAndQueueChildren(ITreeSerializable obj) throws IOException {
        final String key = serializableKeys.get(obj.getClass());
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Unregistered ITreeSerializable: " + obj.getClass());
        }
        ByteBufUtils.writeUTF8String(buffer, key);
        List<? extends ITreeSerializable> children = obj.serializeTree(this);
        ByteBufUtils.writeVarInt(buffer, children.size(), 5);
        for (int i = children.size() - 1; i >= 0; i--) {
            final ITreeSerializable child = children.get(i);
            workStack.add(() -> writeSerializableAndQueueChildren(child));
        }
    }

    // Special-case this class to run the task even if we fail at deserialization, to partially fill children where
    // possible.
    private static class ChildListPopulatorJob implements JobFn {

        public final ITreeSerializable value;
        public final ArrayList<ITreeSerializable> childList;

        private ChildListPopulatorJob(ITreeSerializable value, ArrayList<ITreeSerializable> childList) {
            this.value = value;
            this.childList = childList;
        }

        @Override
        public void run() throws IOException {
            value.loadChildren(childList);
        }
    }

    public ITreeSerializable readSerializableAndQueueChildren(ITreeSerializable parent) throws IOException {
        final String key = ByteBufUtils.readUTF8String(buffer);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("No key provided");
        }
        final MethodHandle constructor = serializableConstructors.get(key);
        if (constructor == null) {
            throw new IllegalArgumentException("No constructor for key " + key);
        }
        final ITreeSerializable value;
        try {
            value = (ITreeSerializable) constructor.invokeExact((CraftingTreeSerializer) this, parent);
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
        int childCount = ByteBufUtils.readVarInt(buffer, 5);
        final ArrayList<ITreeSerializable> childList = new ArrayList<>(childCount);
        workStack.add(new ChildListPopulatorJob(value, childList));
        final ITreeSerializable childParent = value.getSerializationParent();
        for (int i = 0; i < childCount; i++) {
            workStack.add(() -> childList.add(readSerializableAndQueueChildren(childParent)));
        }
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
        return switch (stackType) {
            case ST_NULL -> null;
            case ST_ITEM -> AEItemStack.loadItemStackFromPacket(buffer);
            case ST_FLUID -> AEFluidStack.loadFluidStackFromPacket(buffer);
            default -> throw new UnsupportedOperationException("Unknown stack type " + stackType);
        };
    }

    public void writeItemStack(IAEItemStack stack) throws IOException {
        stack.writeToPacket(buffer);
    }

    public IAEItemStack readItemStack() throws IOException {
        return AEItemStack.loadItemStackFromPacket(buffer);
    }

    public void writePattern(ICraftingPatternDetails pattern) throws IOException {
        writeItemStack(AEItemStack.create(pattern.getPattern()));
    }

    @SuppressWarnings("unchecked")
    public ICraftingPatternDetails readPattern() throws IOException {
        IAEItemStack stack = readItemStack();
        if (stack != null && stack.getItem() instanceof ICraftingPatternItem) {
            return ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack.getItemStack(), world);
        }
        throw new UnsupportedOperationException("Illegal pattern type " + stack);
    }

    @FunctionalInterface
    public interface JobFn {

        void run() throws IOException;
    }

    @FunctionalInterface
    public interface SerializingFn<T> {

        void accept(T elem) throws IOException;
    }

    @FunctionalInterface
    public interface DeserializingFn<T> {

        T get() throws IOException;
    }

    public <T> void writeArray(T[] array, SerializingFn<T> elementWriter) throws IOException {
        if (array == null) {
            ByteBufUtils.writeVarInt(buffer, 0, 5);
            return;
        }
        ByteBufUtils.writeVarInt(buffer, array.length, 5);
        for (T elem : array) {
            elementWriter.accept(elem);
        }
    }

    public <T> void writeList(List<T> array, SerializingFn<T> elementWriter) throws IOException {
        if (array == null) {
            ByteBufUtils.writeVarInt(buffer, 0, 5);
            return;
        }
        ByteBufUtils.writeVarInt(buffer, array.size(), 5);
        for (T elem : array) {
            elementWriter.accept(elem);
        }
    }

    /**
     * @param template     An array of size 0 of type T, needed to generate the correct type after generic erasure
     * @param elementMaker Deserializer function for each element
     * @return The deserialized array
     * @param <T> Element type of the array
     */
    public <T> T[] readArray(T[] template, DeserializingFn<T> elementMaker) throws IOException {
        int len = ByteBufUtils.readVarInt(buffer, 5);
        if (len == 0) {
            return template;
        }
        T[] ret = Arrays.copyOf(template, len);
        for (int i = 0; i < len; i++) {
            ret[i] = elementMaker.get();
        }
        return ret;
    }

    /**
     * @param target       The list to append the deserialized elements to
     * @param elementMaker Deserializer function for each element
     * @param <T>          Element type of the list
     */
    public <T> void readList(List<T> target, DeserializingFn<T> elementMaker) throws IOException {
        int len = ByteBufUtils.readVarInt(buffer, 5);
        if (len == 0) {
            return;
        }
        for (int i = 0; i < len; i++) {
            target.add(elementMaker.get());
        }
    }

    public boolean hasWork() {
        return !workStack.isEmpty();
    }

    public void doWork() {
        if (workStack.isEmpty()) {
            return;
        }
        JobFn job = workStack.get(workStack.size() - 1);
        workStack.remove(workStack.size() - 1);
        try {
            job.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempt to fill in the gaps in a partially deserialized tree
     */
    public void doBestEffortWork() {
        if (workStack.isEmpty()) {
            return;
        }
        for (int i = workStack.size() - 1; i >= 0; i--) {
            JobFn job = workStack.get(i);
            if (job instanceof ChildListPopulatorJob) {
                try {
                    job.run();
                } catch (Exception e) {
                    // ignore, we are already in an error case
                }
            }
        }
        workStack.clear();
    }

    public World getWorld() {
        return world;
    }
}
