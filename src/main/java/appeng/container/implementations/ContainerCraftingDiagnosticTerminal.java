package appeng.container.implementations;

import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.DiagnosticSortButton;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingDiagnosticsUpdate;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;

public class ContainerCraftingDiagnosticTerminal extends AEBaseContainer {

    private static final int FULL_SYNC_INTERVAL = 20;

    private final ITerminalHost host;
    private IGrid network;
    private String searchText = "";
    private long lastSentRevision = Long.MIN_VALUE;
    private String lastSentSearchText = "";
    private int lastSentSortMode = Integer.MIN_VALUE;
    private boolean lastSentAscending = false;
    private int syncDelay = FULL_SYNC_INTERVAL;

    @GuiSync(0)
    public int sortMode = CraftingGridCache.DiagnosticSortMode.CUMULATIVE_TIME.ordinal();

    @GuiSync(1)
    public boolean ascending = false;

    public ContainerCraftingDiagnosticTerminal(final InventoryPlayer ip, final ITerminalHost host) {
        super(ip, host);
        this.host = host;
        this.bindPlayerInventory(ip, 0, -31);
        this.syncSortSettingsFromHost();

        if (host instanceof IGridHost gridHost) {
            this.findNode(gridHost, ForgeDirection.UNKNOWN);
            for (final ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                this.findNode(gridHost, direction);
            }
        }

        if (this.network == null && Platform.isServer()) {
            this.setValidContainer(false);
        }
    }

    private void findNode(final IGridHost host, final ForgeDirection direction) {
        if (this.network != null) {
            return;
        }

        final IGridNode node = host.getGridNode(direction);
        if (node != null) {
            this.network = node.getGrid();
        }
    }

    private CraftingGridCache getCraftingCache() {
        if (this.network == null) {
            return null;
        }

        final ICraftingGrid craftingGrid = this.network.getCache(ICraftingGrid.class);
        return craftingGrid instanceof CraftingGridCache cache ? cache : null;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.syncSortSettingsFromHost();
            this.syncDelay++;
            final CraftingGridCache cache = this.getCraftingCache();
            if (cache != null) {
                final long revision = cache.getDiagnosticsRevision();
                if (this.syncDelay >= FULL_SYNC_INTERVAL || revision != this.lastSentRevision
                        || this.sortMode != this.lastSentSortMode
                        || this.ascending != this.lastSentAscending
                        || !Objects.equals(this.searchText, this.lastSentSearchText)) {
                    this.sendRows(cache, revision);
                }
            }
        }

        super.detectAndSendChanges();
    }

    private void sendRows(final CraftingGridCache cache, final long revision) {
        final List<CraftingGridCache.DiagnosticRowView> rows = cache.createDiagnosticRows(
                this.searchText,
                CraftingGridCache.DiagnosticSortMode.values()[this.sortMode],
                this.ascending);

        for (final Object crafter : this.crafters) {
            if (crafter instanceof EntityPlayerMP player) {
                NetworkHandler.instance.sendTo(new PacketCraftingDiagnosticsUpdate(rows), player);
            }
        }

        this.lastSentRevision = revision;
        this.lastSentSearchText = this.searchText;
        this.lastSentSortMode = this.sortMode;
        this.lastSentAscending = this.ascending;
        this.syncDelay = 0;
    }

    private void syncSortSettingsFromHost() {
        final IConfigManager configManager = this.host.getConfigManager();
        this.setSortMode(this.toSortMode((DiagnosticSortButton) configManager.getSetting(Settings.DIAGNOSTIC_SORT_BY)));
        this.setAscending(configManager.getSetting(Settings.SORT_DIRECTION) == SortDir.ASCENDING);
    }

    private int toSortMode(final DiagnosticSortButton sortButton) {
        return switch (sortButton) {
            case NAME -> CraftingGridCache.DiagnosticSortMode.NAME.ordinal();
            case QTY -> CraftingGridCache.DiagnosticSortMode.CRAFTED.ordinal();
            case TIME -> CraftingGridCache.DiagnosticSortMode.CUMULATIVE_TIME.ordinal();
            case AVG_PER_SECOND -> CraftingGridCache.DiagnosticSortMode.AVG_PER_SECOND.ordinal();
        };
    }

    public void setSearchText(final String searchText) {
        final String normalized = searchText == null ? "" : searchText;
        if (!Objects.equals(this.searchText, normalized)) {
            this.searchText = normalized;
            this.syncDelay = FULL_SYNC_INTERVAL;
        }
    }

    public void setSortMode(final int sortMode) {
        if (sortMode < 0 || sortMode >= CraftingGridCache.DiagnosticSortMode.values().length) {
            return;
        }

        if (this.sortMode != sortMode) {
            this.sortMode = sortMode;
            this.syncDelay = FULL_SYNC_INTERVAL;
        }
    }

    public void setAscending(final boolean ascending) {
        if (this.ascending != ascending) {
            this.ascending = ascending;
            this.syncDelay = FULL_SYNC_INTERVAL;
        }
    }

    public void clearDiagnostics() {
        final CraftingGridCache cache = this.getCraftingCache();
        if (cache != null) {
            cache.clearDiagnosticStats();
            this.syncDelay = FULL_SYNC_INTERVAL;
        }
    }

    public void clearDiagnostics(final IAEStack<?> stack) {
        if (stack == null) {
            this.clearDiagnostics();
            return;
        }

        final CraftingGridCache cache = this.getCraftingCache();
        if (cache != null) {
            cache.clearDiagnosticStats(stack);
            this.syncDelay = FULL_SYNC_INTERVAL;
        }
    }
}
