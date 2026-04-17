package appeng.container.implementations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

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
    private IGrid network;
    private CraftingCPUCluster monitor;
    private String cpuName = "";

    @GuiSync(0)
    public long elapsed = -1;

    @GuiSync(1)
    public int allow = 0;

    @GuiSync(2)
    public boolean cachedSuspend;

    private boolean pendingVisualClear = true;
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
        if (cpu == this.monitor) {
            return;
        }

        this.detachMonitor();
        this.pendingVisualClear = true;

        if (cpu instanceof CraftingCPUCluster cluster) {
            this.monitor = cluster;
            this.cpuName = cpu.getName();
            this.changedStacks.resetStatus();
            this.monitor.getModernListOfItem(this.changedStacks, CraftingItemList.ALL);
            this.monitor.addListener(this, null);
            this.elapsed = 0;
            this.allow = this.monitor.getCraftingAllowMode().ordinal();
            return;
        }

        this.monitor = null;
        this.cpuName = "";
        this.elapsed = -1;
        this.sendVisualClearPacket();
    }

    private void detachMonitor() {
        if (this.monitor != null) {
            this.monitor.removeListener(this);
        }
    }

    public void cancelCrafting() {
        if (this.monitor != null) {
            this.monitor.cancel();
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
        final NBTTagCompound followData = CraftingCpuServerSyncBuilder
                .buildFollowingPlayersNbt(playersFollowingCurrentCraft);

        for (final Object crafter : this.crafters) {
            if (crafter instanceof EntityPlayerMP player) {
                try {
                    NetworkHandler.instance.sendTo(new PacketCompressedNBT(followData), player);
                } catch (final IOException ignored) {}
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && this.monitor != null) {
            try {
                this.cachedSuspend = this.monitor.isSuspended();
                this.elapsed = this.monitor.getElapsedTime();
                final int remainingOperations = this.monitor.getRemainingOperations();

                if (this.pendingVisualClear || !this.changedStacks.isEmpty()
                        || remainingOperations != this.lastSentRemainingOperations) {
                    final PacketCraftingCpuUpdate visualEntriesPacket = new PacketCraftingCpuUpdate(
                            CraftingCpuServerSyncBuilder.buildVisualEntryUpdates(this.monitor, this.changedStacks),
                            this.pendingVisualClear,
                            remainingOperations);
                    final PacketCompressedNBT followPacket = new PacketCompressedNBT(
                            CraftingCpuServerSyncBuilder
                                    .buildFollowingPlayersNbt(this.getPlayersFollowingCurrentCraft()));

                    for (final Object crafter : this.crafters) {
                        if (crafter instanceof EntityPlayerMP player) {
                            NetworkHandler.instance.sendTo(visualEntriesPacket, player);
                            NetworkHandler.instance.sendTo(followPacket, player);
                        }
                    }

                    this.changedStacks.resetStatus();
                    this.pendingVisualClear = false;
                    this.lastSentRemainingOperations = remainingOperations;
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

    public CraftingCPUCluster getMonitor() {
        return this.monitor;
    }

    IGrid getNetwork() {
        return this.network;
    }

    public void togglePlayerFollowStatus(final String name) {
        if (this.monitor != null) {
            this.monitor.togglePlayerFollowStatus(name);
        }
    }

    public List<String> getPlayersFollowingCurrentCraft() {
        return this.monitor == null ? null : this.monitor.getPlayersFollowingCurrentCraft();
    }

    public void changeAllowMode(final String msg) {
        if (this.monitor != null) {
            final CraftingAllow newAllowMode = CraftingAllow.values()[Integer.parseInt(msg)].next();
            this.monitor.changeCraftingAllowMode(newAllowMode);
            this.allow = newAllowMode.ordinal();
        }
    }

    public CraftingAllow getAllowMode() {
        return this.monitor == null ? null : this.monitor.getCraftingAllowMode();
    }

    public void suspendCrafting() {
        if (this.monitor != null) {
            this.cachedSuspend = !this.cachedSuspend;
            this.monitor.setSuspended(this.cachedSuspend);
        }
    }
}
