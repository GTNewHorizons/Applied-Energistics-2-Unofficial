package appeng.container.implementations;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import appeng.api.config.SecurityPermissions;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.core.sync.GuiBridge;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class ContainerTunnelPatternRenamer extends ContainerPatternValueAmount {

    public final ActionHandler<String> renameAction;

    private IAEItemStack original;
    private IPatternTerminal sourceTerminal;
    private IInventory sourceInventory;

    public ContainerTunnelPatternRenamer(final InventoryPlayer ip, final ITerminalHost host) {
        super(ip, host);
        this.renameAction = this.syncRegistrar().actionC2S("rename", StreamCodecs.string())
                .onServerAction(name -> this.rename((EntityPlayerMP) ip.player, name));
    }

    public static void open(final EntityPlayerMP player, final ContainerPatternTerm container) {
        if (player.openContainer != container) return;
        final Slot slot = container.getEncodedPatternSlot();
        final IAEItemStack pattern = AEItemStack.create(slot.getStack());
        final ContainerOpenContext context = container.getOpenContext();
        if (context == null || pattern == null
                || ItemTunnelPattern.getTunnelUuid(pattern.getItemStack()) == null
                || !container.canRenameTunnelPattern(player)
                || player.inventory.getItemStack() != null)
            return;

        final PrimaryGui primary = container.createPrimaryGui();
        Platform.openGUI(player, context.getTile(), context.getSide(), GuiBridge.GUI_TUNNEL_PATTERN_RENAMER);

        if (player.openContainer instanceof ContainerTunnelPatternRenamer renamer) {
            renamer.original = pattern.copy();
            renamer.sourceTerminal = container.getPatternTerminal();
            renamer.sourceInventory = slot.inventory;
            renamer.setPrimaryGui(primary);
            renamer.updateVirtualSlot(StorageName.NONE, slot.getSlotIndex(), pattern.copy());
            renamer.detectAndSendChanges();
        }
    }

    private void rename(final EntityPlayerMP player, final String name) {
        final PrimaryGui primary = this.getPrimaryGui();
        if (this.original == null || primary == null
                || player != this.getInventoryPlayer().player
                || !this.canInteractWith(player)
                || !this.hasAccess(SecurityPermissions.CRAFT, false))
            return;

        primary.open(player);
        if (!(player.openContainer instanceof ContainerPatternTerm container)
                || container.getPatternTerminal() != this.sourceTerminal
                || container.getEncodedPatternSlot().inventory != this.sourceInventory
                || !container.canRenameTunnelPattern(player))
            return;

        TunnelPatternRenaming.renameSlot(container.getEncodedPatternSlot(), this.original, name, player);
        container.detectAndSendChanges();
    }
}
