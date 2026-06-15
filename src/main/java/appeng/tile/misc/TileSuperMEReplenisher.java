package appeng.tile.misc;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.AEApi;
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
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class TileSuperMEReplenisher extends AENetworkTile
        implements IMEInventory<IAEStack<?>>, IIAEStackInventory, IPowerChannelState, IGridTickable {

    private final IAEStackList storage = new IAEStackList(true);
    private final IAEStackList out = new IAEStackList(true);
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
        Platform.readAEStackListNBT(this.storage, data.getTagList("storage", Constants.NBT.TAG_COMPOUND));
        Platform.readAEStackListNBT(this.out, data.getTagList("out", Constants.NBT.TAG_COMPOUND));

        this.countBytes();
        this.countUsedBytes();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public NBTTagCompound writeToNBTEvent(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.cells.writeToNBT(data, "cells");

        data.setDouble("threshold", this.threshold);
        data.setInteger("tickRate", this.tickRate);
        data.setTag("storage", Platform.writeAEStackListNBT(this.storage));
        data.setTag("out", Platform.writeAEStackListNBT(this.out));

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
            final IAEStack<?> stored = this.storage.findPrecise(config);

            final long configSize = config.getStackSize();
            final long storedSize = stored == null ? 0 : stored.getStackSize();

            if (((double) storedSize / configSize) < this.threshold) {
                final IAEStack<?> toRequest = config.copy();
                toRequest.setStackSize(configSize - storedSize);
                toRequest(toRequest, storage);
            } else if (storedSize > configSize) {
                final IAEStack<?> toReturn = config.copy();
                toReturn.setStackSize(storedSize - configSize);
                toReturn(toReturn, storage, this.storage);
            }
        }

        this.refund(this.out);

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
                        this.toReturn(tempStack, storage, this.storage);
                        return stored.getStackSize() > newConfigSize;
                    }
                } else {
                    this.doWork(storage);
                    return false;
                }
            } else {
                this.toReturn(currentConfig, storage, this.storage);
                final IAEStack<?> stored = this.get(currentConfig);
                return !(stored == null || stored.getStackSize() == 0);
            }
        } catch (final GridAccessException ignored) {}
        return true;
    }

    private IAEStack<?> get(final IAEStack<?> aes) {
        return this.storage.findPrecise(aes);
    }

    private void toRequest(final IAEStack<?> aes, final IStorageGrid storage) {
        final IMEMonitor monitor = storage.getMEMonitor(aes.getStackType());
        if (monitor == null) return;
        final IAEStack<?> notAllowed = this.injectItems(aes, Actionable.SIMULATE, this.storage);

        if (notAllowed != null) {
            final long requestSize = aes.getStackSize();
            final long notAllowedSize = notAllowed.getStackSize();
            aes.setStackSize(requestSize - notAllowedSize);
        }

        final IAEStack<?> extracted = monitor.extractItems(aes, Actionable.MODULATE, this.src);
        this.injectItems(extracted, Actionable.MODULATE, this.storage);
    }

    private void toReturn(final IAEStack<?> aes, final IStorageGrid storage, final IAEStackList target) {
        final IMEMonitor monitor = storage.getMEMonitor(aes.getStackType());
        if (monitor == null) return;
        final IAEStack<?> allowed = this.extractItems(aes, Actionable.MODULATE, target);
        if (allowed == null) return;
        final IAEStack<?> notInjected = monitor.injectItems(allowed, Actionable.MODULATE, this.src);
        this.injectItems(notInjected, Actionable.MODULATE, target);
    }

    private void refund(final IAEStackList fList) {
        try {
            final IStorageGrid storage = this.getProxy().getStorage();
            fList.forEach(listItem -> {
                final IMEMonitor monitor = storage.getMEMonitor(listItem.getStackType());
                if (monitor != null) {
                    final IAEStack<?> leftOver = monitor.injectItems(
                            this.extractItems(listItem, Actionable.MODULATE, fList),
                            Actionable.MODULATE,
                            this.src);
                    if (leftOver != null) this.injectItems(leftOver, Actionable.MODULATE, fList);
                }
            });
        } catch (final GridAccessException ignored) {}
    }

    public void fullRefund() {
        this.refund(this.storage);
        this.refund(this.out);
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
        final IAEStackType<?> outStackType = out.getStackType();
        this.storage.forEach(aes -> { if (aes.getStackType().equals(outStackType)) out.add(aes); });
        return out;
    }

    @Override
    public IAEStack<?> injectItems(IAEStack<?> input, Actionable type, BaseActionSource src) {
        return this.injectItems(input, type, this.out);
    }

    private IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final IAEStackList target) {
        if (input == null) return null;

        final long freeBytes = this.totalBytes - this.usedBytes;
        if (freeBytes == 0) return input;

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
            if (freeBytes >= needBytes) return null;
            else {
                final IAEStack<?> notAllowed = input.copy();
                notAllowed.setStackSize(input.getStackSize() - ((freeBytes * typeWeight)) + unusedCount);
                return notAllowed;
            }
        } else {
            this.status = 1;
            this.markForUpdate();

            if (freeBytes >= needBytes) {
                if (!this.unlimited) {
                    this.unusedCount.put(stackType, newUnusedCount);
                    this.usedBytes += needBytes;
                }

                target.add(input);
                return null;
            } else {
                final IAEStack<?> notAllowed = input.copy();
                final IAEStack<?> allowed = input.copy();

                if (!this.unlimited) {
                    final long newNeedBytes = freeBytes * typeWeight;
                    notAllowed.setStackSize(input.getStackSize() - (newNeedBytes + unusedCount));
                    allowed.setStackSize(input.getStackSize() - notAllowed.getStackSize());

                    this.usedBytes += freeBytes;
                    this.unusedCount.put(stackType, 0);
                }

                target.add(allowed);
                return notAllowed;
            }
        }
    }

    @Override
    public IAEStack<?> extractItems(IAEStack<?> request, Actionable mode, BaseActionSource src) {
        return this.extractItems(request, mode, this.storage);
    }

    private IAEStack<?> extractItems(final IAEStack<?> request, final Actionable mode, final IAEStackList target) {
        if (request == null) return null;

        final IAEStackType<?> stackType = request.getStackType();
        final IAEStack<?> stack = target.findPrecise(request);
        if (stack == null) return null;

        final long stackSize = stack.getStackSize();
        if (stackSize <= 0) return null;

        long requestSize = request.getStackSize();
        final IAEStack<?> ret = request.copy();

        if (stackSize < requestSize) {
            ret.setStackSize(stackSize);
            requestSize = stackSize;
        }

        if (mode == Actionable.MODULATE) {
            this.status = -1;
            this.markForUpdate();

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

        final Object2DoubleOpenHashMap<IAEStackType<?>> unusedCount = new Object2DoubleOpenHashMap<>();
        this.storage.forEach(aes -> {
            final IAEStackType<?> stackType = aes.getStackType();
            unusedCount.put(stackType, unusedCount.getOrDefault(stackType, 0) + aes.getStackSize());
        });

        this.out.forEach(aes -> {
            final IAEStackType<?> stackType = aes.getStackType();
            unusedCount.put(stackType, unusedCount.getOrDefault(stackType, 0) + aes.getStackSize());
        });

        for (IAEStackType<?> stackType : AEStackTypeRegistry.getAllTypes()) {
            final int typeWeight = stackType.getAmountPerByte();
            final double count = unusedCount.getOrDefault(stackType, 0);
            this.unusedCount.put(stackType, (int) count % typeWeight);
            this.usedBytes += (long) Math.ceil(count / typeWeight);
        }
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

        this.zeroVoid(drops);
    }

    private void zeroVoid(final IAEStackList fList, final List<ItemStack> drops) {
        final ItemStack container = AEApi.instance().definitions().items().itemMEStackPacket().maybeStack(1).get();
        fList.forEach(listItem -> {
            final ItemStack is = container.copy();
            Platform.writeStackNBT(listItem, ItemStackNBT.get(is));
            drops.add(is);
        });
    }

    public void zeroVoid(final List<ItemStack> drops) {
        this.zeroVoid(this.storage, drops);
        this.zeroVoid(this.out, drops);
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
        return new ContainerSuperMEReplenisher.Stored(this.storage);
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
