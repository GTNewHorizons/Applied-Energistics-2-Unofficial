package appeng.tile.misc;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerSuperMEReplenisher;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class TileSuperMEReplenisher extends AENetworkTile
        implements IMEInventory<IAEStack<?>>, IIAEStackInventory, IPowerChannelState, IGridTickable {

    private final Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();
    private final Map<IAEStackType<?>, IItemList> out = new IdentityHashMap<>();
    private final IAEStackInventory config = new IAEStackInventory(this, 3 * 9, StorageName.CONFIG) {

        @Override
        public void putAEStackInSlot(int n, IAEStack<?> aes) {
            if (haveSame(n, aes)) return;
            if (Platform.isServer() && TileSuperMEReplenisher.this.onConfigChange(n, aes)) return;
            super.putAEStackInSlot(n, aes);
        }
    };

    private final Object2IntOpenHashMap<IAEStackType<?>> unusedCount = new Object2IntOpenHashMap<>();

    private long totalBytes = 0;
    private long usedBytes = 0;

    private int tickRate = 120;
    private double threshold = 0.5;

    private boolean unlimited = false;
    private boolean isPowered = false;

    private int status = 0;

    private final BaseActionSource src = new MachineSource(this);

    private final AppEngInternalInventory cells = new AppEngInternalInventory(null, 6) {

        @Override
        public ItemStack decrStackSize(int slot, int qty) {
            TileSuperMEReplenisher.this.removeCell(this.getStackInSlot(slot));
            return super.decrStackSize(slot, qty);
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack newItemStack) {
            TileSuperMEReplenisher.this.removeCell(this.getStackInSlot(slot));
            TileSuperMEReplenisher.this.addCell(newItemStack);
            super.setInventorySlotContents(slot, newItemStack);
        }

        @Override
        public void markDirty() {
            TileSuperMEReplenisher.this.markDirty();
        }
    };

    public TileSuperMEReplenisher() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(4.0);

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.lists.put(type, type.createPrimitiveList());
            this.out.put(type, type.createPrimitiveList());
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(this.tickRate, this.tickRate, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int TicksSinceLastCall) {
        try {
            if (this.getProxy().isActive()) return this.doWork(this.getProxy().getStorage());
        } catch (final GridAccessException ignored) {}
        return TickRateModulation.SAME;
    }

    private void updateTickRate() {
        try {
            this.getProxy().getTick().updateTickRate(this.getGridNode(ForgeDirection.UNKNOWN));
        } catch (Exception ignored) {}
    }

    @TileEvent(TileEventType.TICK)
    public void tick() {
        if (Platform.isServer() && this.status != 0) {
            this.status = 0;
            this.markForUpdate();
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileSuperMEReplenisher(final ByteBuf data) {
        final boolean oldPower = this.isPowered;
        final int oldStatus = this.status;
        this.isPowered = data.readBoolean();
        this.status = data.readInt();
        return this.isPowered != oldPower || this.status != oldStatus;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSuperMEReplenisher(final ByteBuf data) {
        data.writeBoolean(isActive());
        data.writeInt(this.status);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBTEvent(NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
        this.cells.readFromNBT(data, "cells");

        if (data.hasKey("threshold")) this.threshold = data.getDouble("threshold");

        if (data.hasKey("tickRate")) this.setTickRate(data.getInteger("tickRate"));

        final NBTTagCompound listTag = data.getCompoundTag("lists");
        this.lists.forEach((type, list) -> {
            final IItemList l = type.createPrimitiveList();
            if (listTag.hasKey(type.getId())) {
                final NBTTagList tagList = listTag.getTagList(type.getId(), Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < tagList.tagCount(); i++) {
                    l.add(Platform.readStackNBT(tagList.getCompoundTagAt(i)));
                }
            }
            this.lists.put(type, l);
        });

        final NBTTagCompound outTag = data.getCompoundTag("out");
        this.out.forEach((type, list) -> {
            final IItemList l = type.createPrimitiveList();
            if (outTag.hasKey(type.getId())) {
                final NBTTagList tagList = outTag.getTagList(type.getId(), Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < tagList.tagCount(); i++) {
                    l.add(Platform.readStackNBT(tagList.getCompoundTagAt(i)));
                }
            }
            this.out.put(type, l);
        });

        this.countBytes();
        this.countUsedBytes();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public NBTTagCompound writeToNBTEvent(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.cells.writeToNBT(data, "cells");

        data.setDouble("threshold", this.threshold);
        data.setInteger("tickRate", this.tickRate);

        final NBTTagCompound listsTag = new NBTTagCompound();
        this.lists.forEach((type, list) -> {
            final NBTTagList tagList = new NBTTagList();
            list.forEach(o -> {
                final NBTTagCompound tag = new NBTTagCompound();
                tagList.appendTag(Platform.writeStackNBT((IAEStack<?>) o, tag));
            });
            listsTag.setTag(type.getId(), tagList);
        });
        data.setTag("lists", listsTag);

        final NBTTagCompound outTag = new NBTTagCompound();
        this.out.forEach((type, list) -> {
            final NBTTagList tagList = new NBTTagList();
            list.forEach(o -> {
                final NBTTagCompound tag = new NBTTagCompound();
                tagList.appendTag(Platform.writeStackNBT((IAEStack<?>) o, tag));
            });
            outTag.setTag(type.getId(), tagList);
        });
        data.setTag("out", outTag);

        return data;
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange p) {
        this.updatePowerState();
    }

    @MENetworkEventSubscribe
    public final void bootingRender(final MENetworkBootingStatusChange c) {
        this.updatePowerState();
    }

    private TickRateModulation doWork(final IStorageGrid storage) {
        for (int i = 0; i < this.config.getSizeInventory(); i++) {
            final IAEStack<?> config = this.config.getAEStackInSlot(i);
            if (config == null) continue;
            final IAEStackType<?> type = config.getStackType();
            final IAEStack<?> stored = this.lists.get(type).findPrecise(config);

            final long configSize = config.getStackSize();
            final long storedSize = stored == null ? 0 : stored.getStackSize();

            if (((double) storedSize / configSize) < this.threshold) {
                final IAEStack<?> toRequest = config.copy();
                toRequest.setStackSize(configSize - storedSize);
                toRequest(toRequest, type, storage);
            } else if (storedSize > configSize) {
                final IAEStack<?> toReturn = config.copy();
                toReturn.setStackSize(storedSize - configSize);
                toReturn(toReturn, type, storage, this.lists);
            }
        }

        this.flash(this.out);

        return TickRateModulation.SAME;
    }

    private boolean haveSame(final int n, final IAEStack<?> aes) {
        for (int i = 0; i < this.config.getSizeInventory(); i++) {
            if (i == n) continue;
            final IAEStack<?> config = this.config.getAEStackInSlot(i);
            if (config == null) continue;
            if (config.equals(aes)) return true;
        }

        return false;
    }

    private boolean onConfigChange(final int slots, final IAEStack<?> newStack) {
        try {
            final IStorageGrid storage = this.getProxy().getStorage();
            final IAEStack<?> currentConfig = this.config.getAEStackInSlot(slots);
            if (currentConfig == null) return false;
            if (currentConfig.equals(newStack)) {
                final long currentConfigSize = currentConfig.getStackSize();
                final long newConfigSize = newStack.getStackSize();

                if (currentConfigSize > newConfigSize) {
                    final IAEStack<?> stored = this.get(currentConfig);
                    if (stored == null) return false;
                    final long storedSize = stored.getStackSize();
                    if (storedSize > newConfigSize) {
                        final long newSize = storedSize - newConfigSize;
                        final IAEStack<?> tempStack = stored.copy();
                        tempStack.setStackSize(newSize);
                        this.toReturn(tempStack, tempStack.getStackType(), storage, this.lists);
                        return stored.getStackSize() > newConfigSize;
                    }
                } else {
                    this.doWork(storage);
                    return false;
                }
            } else {
                this.toReturn(currentConfig, currentConfig.getStackType(), storage, this.lists);
                final IAEStack<?> stored = this.get(currentConfig);
                return !(stored == null || stored.getStackSize() == 0);
            }
        } catch (final GridAccessException ignored) {}
        return true;
    }

    private IAEStack<?> get(final IAEStack<?> aes) {
        return this.lists.get(aes.getStackType()).findPrecise(aes);
    }

    private void toRequest(final IAEStack<?> aes, final IAEStackType<?> type, final IStorageGrid storage) {
        final IMEMonitor monitor = storage.getMEMonitor(type);
        if (monitor == null) return;
        final IAEStack<?> notAllowed = this.injectItems(aes, Actionable.SIMULATE, this.lists);

        if (notAllowed != null) {
            final long requestSize = aes.getStackSize();
            final long notAllowedSize = notAllowed.getStackSize();
            aes.setStackSize(requestSize - notAllowedSize);
        }

        final IAEStack<?> extracted = monitor.extractItems(aes, Actionable.MODULATE, this.src);
        this.injectItems(extracted, Actionable.MODULATE, this.lists);
    }

    private void toReturn(final IAEStack<?> ais, final IAEStackType<?> type, final IStorageGrid storage,
            final Map<IAEStackType<?>, IItemList> target) {
        final IMEMonitor monitor = storage.getMEMonitor(type);
        if (monitor == null) return;
        final IAEStack<?> allowed = this.extractItems(ais, Actionable.MODULATE, target);
        if (allowed == null) return;
        final IAEStack<?> notInjected = monitor.injectItems(allowed, Actionable.MODULATE, this.src);
        this.injectItems(notInjected, Actionable.MODULATE, target);
    }

    private void flash(Map<IAEStackType<?>, IItemList> fList) {
        try {
            final IStorageGrid storage = this.getProxy().getStorage();
            fList.forEach((stackType, list) -> {
                final IMEMonitor monitor = storage.getMEMonitor(stackType);
                if (monitor != null) list.forEach(
                        listItem -> monitor.injectItems(
                                this.extractItems((IAEStack<?>) listItem, Actionable.MODULATE, fList),
                                Actionable.MODULATE,
                                this.src));

            });
        } catch (final GridAccessException ignored) {}
    }

    public void fullRefund() {
        this.flash(this.lists);
        this.flash(this.out);
    }

    private long getCellBytes(final ItemStack is) {
        if (is != null && is.getItem() instanceof IStorageCell baseCell) {
            return baseCell.getBytesLong(is);
        }

        return 0;
    }

    private void removeCell(final ItemStack is) {
        final long newBytes = this.getCellBytes(is);
        if (newBytes == Long.MAX_VALUE / 16) this.unlimited = false;
        this.totalBytes -= newBytes;
        this.updatePowerDraw();
    }

    private void addCell(final ItemStack is) {
        final long newBytes = this.getCellBytes(is);
        if (newBytes == Long.MAX_VALUE / 16) this.unlimited = true;
        this.totalBytes += newBytes;
        this.updatePowerDraw();
    }

    private void updatePowerDraw() {
        final double powerDraw;
        if (this.unlimited) powerDraw = 1;
        else powerDraw = Math.sqrt(Math.pow(this.totalBytes, 0.576D));
        this.getProxy().setIdlePowerUsage(powerDraw);
    }

    private void updatePowerState() {
        boolean newState = false;
        try {
            newState = this.getProxy().isActive()
                    && this.getProxy().getEnergy().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                            > 0.0001;
        } catch (final GridAccessException ignored) {}
        if (newState != this.isPowered) {
            this.isPowered = newState;
            this.markForUpdate();
        }
    }

    @Override
    public IItemList<IAEStack<?>> getAvailableItems(IItemList<IAEStack<?>> out, int iteration) {
        final IItemList<?> list = this.lists.get(out.getStackType());
        list.forEach(out::add);
        return out;
    }

    @Override
    public IAEStack<?> injectItems(IAEStack<?> input, Actionable type, BaseActionSource src) {
        return this.injectItems(input, type, this.out);
    }

    private IAEStack<?> injectItems(IAEStack<?> input, Actionable type, Map<IAEStackType<?>, IItemList> target) {
        if (input == null) return null;

        final long freeBytes = this.totalBytes - this.usedBytes;
        if (freeBytes == 0) return input;

        this.status = 1;
        this.markForUpdate();

        final IAEStackType<?> stackType = input.getStackType();
        final int typeWeight = stackType.getAmountPerByte();
        final long stackSize = input.getStackSize();
        final int unusedCount = this.unusedCount.getOrDefault(stackType, 0);
        final int freeUnusedCount = unusedCount == 0 ? 0 : typeWeight - unusedCount;

        final long needBytes;
        final int newUnusedCount;
        if (stackSize > freeUnusedCount) {
            final long toCountSize = stackSize - freeUnusedCount;
            needBytes = (long) Math.ceil((double) toCountSize / typeWeight);
            newUnusedCount = (int) toCountSize % typeWeight;
        } else {
            needBytes = 0;
            newUnusedCount = (int) (unusedCount + stackSize);
        }

        if (type == Actionable.SIMULATE) {
            if (freeBytes > needBytes) return null;
            else {
                final IAEStack<?> allowed = input.copy();
                allowed.setStackSize((freeBytes * typeWeight) + unusedCount);
                return allowed;
            }
        } else {
            if (freeBytes > needBytes) {
                if (!this.unlimited) {
                    this.unusedCount.put(stackType, newUnusedCount);
                    this.usedBytes += needBytes;
                }

                target.get(stackType).add(input);
                return null;
            } else {
                final IAEStack<?> allowed = input.copy();

                if (!this.unlimited) {
                    final long newNeedBytes = freeBytes * typeWeight;
                    allowed.setStackSize(newNeedBytes + unusedCount);

                    this.usedBytes += newNeedBytes;
                    this.unusedCount.put(stackType, 0);
                }

                target.get(stackType).add(allowed);
                return allowed;
            }
        }
    }

    @Override
    public IAEStack<?> extractItems(IAEStack<?> request, Actionable mode, BaseActionSource src) {
        return this.extractItems(request, mode, this.lists);
    }

    private IAEStack<?> extractItems(IAEStack<?> request, Actionable mode, Map<IAEStackType<?>, IItemList> target) {
        if (request == null) return null;

        final IAEStackType<?> stackType = request.getStackType();
        final IAEStack<?> stack = target.get(stackType).findPrecise(request);
        if (stack == null) return null;

        final long stackSize = stack.getStackSize();
        if (stackSize <= 0) return null;

        this.status = -1;
        this.markForUpdate();

        long requestSize = request.getStackSize();
        final IAEStack<?> ret = request.copy();

        if (stackSize < requestSize) {
            ret.setStackSize(stackSize);
            requestSize = stackSize;
        }

        if (mode == Actionable.MODULATE) {
            stack.decStackSize(requestSize);
            if (!this.unlimited) {
                final int typeWeight = stackType.getAmountPerByte();
                final int unusedCount = this.unusedCount.getOrDefault(stackType, 0);

                final long needBytes;
                final int newUnusedCount;
                if (requestSize > unusedCount) {
                    final long toCountSize = requestSize - unusedCount;
                    final int rest = (int) (toCountSize % typeWeight);
                    newUnusedCount = rest == 0 ? 0 : typeWeight - rest;
                    needBytes = toCountSize / typeWeight + (unusedCount != 0 && newUnusedCount == 0 ? 1 : 0);
                } else {
                    newUnusedCount = (int) (unusedCount - requestSize);
                    needBytes = newUnusedCount == 0 ? 1 : 0;
                }

                this.usedBytes -= needBytes;
                this.unusedCount.put(stackType, newUnusedCount);
            }
        }

        return ret;
    }

    private void countUsedBytes() {
        this.usedBytes = 0;
        this.lists.forEach((stackType, list) -> {
            final int typeWeight = stackType.getAmountPerByte();
            AtomicLong unusedCount = new AtomicLong();
            list.forEach(listItem -> {
                final long stackSize = ((IAEStack<?>) listItem).getStackSize();
                this.usedBytes += stackSize / typeWeight;
                unusedCount.addAndGet(stackSize % typeWeight);
            });

            this.unusedCount.put(stackType, (int) unusedCount.get() % typeWeight);
            this.usedBytes += (long) Math.ceil((double) unusedCount.get() / typeWeight);
        });
    }

    private void countBytes() {
        this.totalBytes = 0;
        for (int i = 0; i < this.cells.getSizeInventory(); i++) {
            this.totalBytes += this.getCellBytes(this.cells.getStackInSlot(i));
        }

        if (this.totalBytes >= Long.MAX_VALUE / 16) this.unlimited = true;
    }

    @Override
    public void saveAEStackInv() {
        this.markDirty();
    }

    @Override
    public void getDrops(World w, int x, int y, int z, List<ItemStack> drops) {
        this.fullRefund();
        for (int i = 0; i < this.cells.getSizeInventory(); i++) {
            final ItemStack cell = this.cells.getStackInSlot(i);
            if (cell != null) drops.add(cell);
        }
        super.getDrops(w, x, y, z, drops);
    }

    @Override
    public boolean isPowered() {
        return this.isPowered;
    }

    @Override
    public boolean isActive() {
        return this.isPowered;
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) return this.config;
        return null;
    }

    public ContainerSuperMEReplenisher.Stored getStorage() {
        return new ContainerSuperMEReplenisher.Stored(this.lists);
    }

    public AppEngInternalInventory getCellInventory() {
        return this.cells;
    }

    public long getTotalBytes() {
        return this.totalBytes;
    }

    public long getUsedBytes() {
        if (this.unlimited) this.countUsedBytes();
        return this.usedBytes;
    }

    public int getStatus() {
        return this.status;
    }

    public void setTickRate(final int tickRate) {
        this.tickRate = tickRate;
        this.updateTickRate();
    }

    public int getTickRate() {
        return this.tickRate;
    }

    public void setThreshold(final double threshold) {
        this.threshold = threshold;
    }

    public double getThreshold() {
        return this.threshold;
    }
}
