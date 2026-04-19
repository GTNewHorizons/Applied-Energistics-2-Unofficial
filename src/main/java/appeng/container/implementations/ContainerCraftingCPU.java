package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import appeng.api.AEApi;
import appeng.api.config.CraftingAllow;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.core.sync.packets.PacketCraftingCpuUpdate;
import appeng.helpers.ICustomNameObject;
import appeng.me.cluster.IAEMultiBlock;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;

public class ContainerCraftingCPU extends AEBaseContainer
        implements IMEMonitorHandlerReceiver<IAEStack<?>>, ICustomNameObject {

    private final IItemList<IAEStack<?>> changedStacks = AEApi.instance().storage().createAEStackList();
    private final IGrid network;
    private CraftingCPUCluster cpu;
    private String cpuName = "";

    @GuiSync(0)
    public long elapsed = -1;

    @GuiSync(1)
    public CraftingAllow allow = CraftingAllow.ALLOW_ALL;

    @GuiSync(2)
    public boolean cachedSuspend;

    private boolean pendingVisualClear = true;
    private boolean pendingFollowSync = true;
    private int lastSentRemainingOperations = Integer.MIN_VALUE;

    public ContainerCraftingCPU(final InventoryPlayer inventoryPlayer, final Object target) {
        super(inventoryPlayer, target);

        this.network = this.resolveNetwork(target);
        if (target instanceof TileCraftingTile) {
            this.setCPU((ICraftingCPU) ((IAEMultiBlock) target).getCluster());
        }

        if (this.network == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    private IGrid resolveNetwork(final Object target) {
        if (!(target instanceof IGridHost host)) {
            return null;
        }

        final IGrid unknownSideNetwork = this.resolveNetwork(host, ForgeDirection.UNKNOWN);
        if (unknownSideNetwork != null) {
            return unknownSideNetwork;
        }

        for (final ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            final IGrid sideNetwork = this.resolveNetwork(host, direction);
            if (sideNetwork != null) {
                return sideNetwork;
            }
        }

        return null;
    }

    private IGrid resolveNetwork(final IGridHost host, final ForgeDirection direction) {
        final IGridNode node = host.getGridNode(direction);
        return node == null ? null : node.getGrid();
    }

    protected void setCPU(final ICraftingCPU cpu) {
        if (cpu == this.cpu) {
            return;
        }

        this.detachMonitor();
        this.pendingVisualClear = true;
        this.pendingFollowSync = true;

        if (cpu instanceof CraftingCPUCluster cluster) {
            this.cpu = cluster;
            this.cpuName = cpu.getName();
            this.changedStacks.resetStatus();
            this.cpu.getModernListOfItem(this.changedStacks, CraftingItemList.ALL);
            this.cpu.addListener(this, null);
            this.elapsed = 0;
            this.allow = this.cpu.getCraftingAllowMode();
            return;
        }

        this.cpu = null;
        this.cpuName = "";
        this.elapsed = -1;
        this.allow = CraftingAllow.ALLOW_ALL;
        this.cachedSuspend = false;
        this.sendVisualClearPacket();
    }

    private void detachMonitor() {
        if (this.cpu != null) {
            this.cpu.removeListener(this);
        }
    }

    public void cancelCrafting() {
        if (this.cpu != null) {
            this.cpu.cancel();
        }
        this.elapsed = -1;
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting crafter) {
        super.removeCraftingFromCrafters(crafter);

        if (this.crafters.isEmpty()) {
            this.detachMonitor();
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        this.detachMonitor();
    }

    public void sendUpdateFollowPacket(final List<String> playersFollowingCurrentCraft) {
        final NBTTagCompound followData = buildFollowingPlayersNbt(playersFollowingCurrentCraft);
        this.sendCompressedNbtToCrafters(followData);
        this.pendingFollowSync = false;
    }

    private static NBTTagCompound buildFollowingPlayersNbt(final List<String> playersFollowingCurrentCraft) {
        final NBTTagCompound result = new NBTTagCompound();
        final NBTTagList tagList = new NBTTagList();

        if (playersFollowingCurrentCraft != null) {
            for (final String name : playersFollowingCurrentCraft) {
                tagList.appendTag(new NBTTagString(name));
            }
        }

        result.setTag("playNameList", tagList);
        return result;
    }

    private static List<CraftingCpuEntry> buildVisualEntryUpdates(@NotNull final CraftingCPUCluster monitor,
            final Iterable<IAEStack<?>> changedStacks) {
        final List<CraftingCpuEntry> updates = new ArrayList<>();
        for (final IAEStack<?> stack : changedStacks) {
            final IAEStack<?> normalizedStack = CraftingCpuEntry.normalizeStack(stack);
            final long storedAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.STORAGE);
            final long activeAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.ACTIVE);
            final long pendingAmount = monitor.getStackAmount(normalizedStack, CraftingItemList.PENDING);
            updates.add(
                    new CraftingCpuEntry(
                            normalizedStack,
                            storedAmount,
                            activeAmount,
                            pendingAmount,
                            monitor.getScheduledReason(normalizedStack)));
        }
        return updates;
    }

    private void sendCompressedNbtToCrafters(final NBTTagCompound data) {
        for (final Object crafter : this.crafters) {
            if (crafter instanceof EntityPlayerMP player) {
                try {
                    NetworkHandler.instance.sendTo(new PacketCompressedNBT(data), player);
                } catch (final IOException ignored) {}
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && this.cpu != null) {
            try {
                this.cachedSuspend = this.cpu.isSuspended();
                this.elapsed = this.cpu.getElapsedTime();
                final int remainingOperations = this.cpu.getRemainingOperations();

                if (this.pendingVisualClear || !this.changedStacks.isEmpty()
                        || remainingOperations != this.lastSentRemainingOperations) {
                    final PacketCraftingCpuUpdate visualEntriesPacket = new PacketCraftingCpuUpdate(
                            buildVisualEntryUpdates(this.cpu, this.changedStacks),
                            this.pendingVisualClear,
                            remainingOperations);

                    for (final Object crafter : this.crafters) {
                        if (crafter instanceof EntityPlayerMP player) {
                            NetworkHandler.instance.sendTo(visualEntriesPacket, player);
                        }
                    }

                    this.changedStacks.resetStatus();
                    this.pendingVisualClear = false;
                    this.lastSentRemainingOperations = remainingOperations;
                }

                if (this.pendingFollowSync) {
                    this.sendUpdateFollowPacket(this.getPlayersFollowingCurrentCraft());
                }
            } catch (final IOException ignored) {}
        }

        super.detectAndSendChanges();
    }

    private void sendVisualClearPacket() {
        try {
            final PacketCraftingCpuUpdate clearPacket = new PacketCraftingCpuUpdate(Collections.emptyList(), true, 0);
            for (final Object crafter : this.crafters) {
                if (crafter instanceof EntityPlayerMP player) {
                    NetworkHandler.instance.sendTo(clearPacket, player);
                }
            }
            this.pendingVisualClear = false;
            this.lastSentRemainingOperations = 0;
            this.sendCompressedNbtToCrafters(buildFollowingPlayersNbt(Collections.emptyList()));
            this.pendingFollowSync = false;
        } catch (final IOException ignored) {}
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
            final BaseActionSource actionSource) {
        for (IAEStack<?> stack : change) {
            stack = stack.copy();
            stack.setStackSize(1);
            this.changedStacks.add(stack);
        }
    }

    @Override
    public void onListUpdate() {}

    @Override
    public String getCustomName() {
        return this.cpuName;
    }

    @Override
    public boolean hasCustomName() {
        return this.cpuName != null && !this.cpuName.isEmpty();
    }

    @Override
    public void setCustomName(final String name) {
        this.cpuName = name == null ? "" : name;
    }

    public long getElapsedTime() {
        return this.elapsed;
    }

    public CraftingCPUCluster getCpu() {
        return this.cpu;
    }

    IGrid getNetwork() {
        return this.network;
    }

    public void togglePlayerFollowStatus(final String name) {
        if (this.cpu != null) {
            this.cpu.togglePlayerFollowStatus(name);
        }
    }

    public List<String> getPlayersFollowingCurrentCraft() {
        return this.cpu == null ? null : this.cpu.getPlayersFollowingCurrentCraft();
    }

    public void changeAllowMode(final String msg) {
        if (this.cpu != null) {
            final CraftingAllow newAllowMode = CraftingAllow.values()[Integer.parseInt(msg)].next();
            this.cpu.changeCraftingAllowMode(newAllowMode);
            this.allow = newAllowMode;
        }
    }

    public CraftingAllow getAllowMode() {
        return this.cpu == null ? null : this.cpu.getCraftingAllowMode();
    }

    public void suspendCrafting() {
        if (this.cpu != null) {
            this.cachedSuspend = !this.cachedSuspend;
            this.cpu.setSuspended(this.cachedSuspend);
        }
    }
}
