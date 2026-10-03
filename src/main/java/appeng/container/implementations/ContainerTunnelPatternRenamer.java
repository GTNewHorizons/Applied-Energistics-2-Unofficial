package appeng.container.implementations;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.TunnelPatternRenamerHost;
import appeng.container.slot.SlotFake;
import appeng.core.sync.GuiBridge;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class ContainerTunnelPatternRenamer extends ContainerPatternValueAmount {

    private IAEItemStack original;
    private IInventory sourceInventory;
    private int sourceSlot;

    public ContainerTunnelPatternRenamer(final InventoryPlayer ip, final TunnelPatternRenamerHost host) {
        super(ip, host.getAnchor());
    }

    public static void openForSlot(final EntityPlayerMP player, final AEBaseContainer container, final int slotIndex) {
        if (slotIndex < 0 || slotIndex >= container.inventorySlots.size()) return;
        final Slot slot = container.getSlot(slotIndex);
        if (slot instanceof SlotFake || !slot.canTakeStack(player)) return;
        open(player, container, slot, AEItemStack.create(slot.getStack()));
    }

    public static void openForStoredPattern(final EntityPlayerMP player, final ContainerMEMonitorable container) {
        if (container.getTargetStack() instanceof IAEItemStack pattern) {
            open(player, container, null, pattern.copy().setStackSize(1));
        }
    }

    private static void open(final EntityPlayerMP player, final AEBaseContainer container, final Slot slot,
            final IAEItemStack pattern) {
        final ContainerOpenContext context = container.getOpenContext();
        if (context == null || pattern == null
                || ItemTunnelPattern.getTunnelUuid(pattern.getItemStack()) == null
                || !container.canInteractWith(player)
                || player.inventory.getItemStack() != null)
            return;

        final PrimaryGui primary = container.createPrimaryGui();
        Platform.openGUI(
                player,
                context.getTile(),
                context.getSide(),
                GuiBridge.GUI_TUNNEL_PATTERN_RENAMER,
                container.getTargetSlotIndex());

        if (player.openContainer instanceof ContainerTunnelPatternRenamer renamer) {
            renamer.original = pattern.copy();
            renamer.sourceInventory = slot == null ? null : slot.inventory;
            renamer.sourceSlot = slot == null ? -1 : slot.getSlotIndex();
            renamer.setPrimaryGui(primary);
            renamer.updateVirtualSlot(StorageName.NONE, renamer.sourceSlot, pattern.copy());
            renamer.detectAndSendChanges();
        }
    }

    public void rename(final EntityPlayerMP player, final String name) {
        final PrimaryGui primary = this.getPrimaryGui();
        if (this.original == null || primary == null || !this.canInteractWith(player)) return;

        primary.open(player);
        if (!(player.openContainer instanceof AEBaseContainer container) || !container.canInteractWith(player)) return;

        if (this.sourceInventory == null && container instanceof ContainerMEMonitorable monitorable) {
            TunnelPatternRenaming.renameStored(
                    monitorable.getPowerSource(),
                    monitorable.getItemMonitor(),
                    this.original,
                    name,
                    monitorable.getActionSource(),
                    remainder -> Platform.addToPlayerInvOrDrop(player, remainder.getItemStack()));
        } else if (this.sourceInventory != null) {
            TunnelPatternRenaming.renameSlot(
                    container.inventorySlots,
                    this.sourceInventory,
                    this.sourceSlot,
                    this.original,
                    name,
                    player);
            container.detectAndSendChanges();
        }
    }
}
