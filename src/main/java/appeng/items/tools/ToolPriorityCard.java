package appeng.items.tools;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.PriorityCardMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.items.AEBaseItem;
import appeng.items.contents.PriorityCardObject;
import appeng.parts.AEBasePart;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;

public class ToolPriorityCard extends AEBaseItem implements IGuiItem {

    public ToolPriorityCard() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);

        lines.add(GuiText.PriorityCardTooltip.getLocal());

        GuiText mode = switch (getMode(stack)) {
            case EDIT -> GuiText.PriorityCardTooltipModeEdit;
            case VIEW -> GuiText.PriorityCardTooltipModeView;
            case SET -> GuiText.PriorityCardTooltipModeSet;
            case INC -> GuiText.PriorityCardTooltipModeInc;
            case DEC -> GuiText.PriorityCardTooltipModeDec;
        };
        lines.add(mode.getLocal());
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack it, final World w, final EntityPlayer p) {
        if (p.isSneaking()) {
            if (Platform.isServer()) {
                Platform.openGUI(p, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_PRIORITY_CARD);
            }
            p.swingItem();
        }
        return it;
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack is, final World world, final int x, final int y, final int z) {
        return new PriorityCardObject(is, x);
    }

    public static void handleUse(EntityPlayer player, AEBaseTile tile, ItemStack stack, ForgeDirection side) {
        if (Platform.isClient()) {
            return;
        }
        if (tile instanceof IPriorityHost iph && tile instanceof IActionHost iah) {
            handleUse(player, tile, iph, iah, stack, side);
        } else {
            player.addChatMessage(PlayerMessages.PriorityInvalidTarget.get());
        }
    }

    public static void handleUse(EntityPlayer player, AEBasePart part, ItemStack stack, ForgeDirection side) {
        if (Platform.isClient()) {
            return;
        }
        if (part instanceof IPriorityHost iph) {
            handleUse(player, part.getTile(), iph, part, stack, side);
        } else {
            player.addChatMessage(PlayerMessages.PriorityInvalidTarget.get());
        }
    }

    private static void handleUse(EntityPlayer player, TileEntity tile, IPriorityHost priorityHost,
            IActionHost actionHost, ItemStack stack, ForgeDirection side) {
        if (!securityCheck(actionHost, player)) {
            return;
        }

        switch (getMode(stack)) {
            case EDIT -> Platform.openGUI(player, tile, side, GuiBridge.GUI_PRIORITY);
            case VIEW -> player.addChatMessage(PlayerMessages.PriorityReadout.get(priorityHost.getPriority()));
            case SET -> {
                priorityHost.setPriority(getPriority(stack));
                player.addChatMessage(PlayerMessages.PriorityConfigured.get(priorityHost.getPriority()));
            }
            case INC -> {
                int priority = getPriority(stack);
                priorityHost.setPriority(priority);
                setPriority(stack, priority + 1);
                player.addChatMessage(PlayerMessages.PriorityConfigured.get(priorityHost.getPriority()));
            }
            case DEC -> {
                int priority = getPriority(stack);
                priorityHost.setPriority(priority);
                setPriority(stack, priority - 1);
                player.addChatMessage(PlayerMessages.PriorityConfigured.get(priorityHost.getPriority()));
            }
        }
    }

    private static boolean securityCheck(final IActionHost actionHost, final EntityPlayer player) {
        final IGridNode gn = actionHost.getActionableNode();
        if (gn != null) {
            final IGrid g = gn.getGrid();
            if (g != null) {
                final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                return sg.hasPermission(player, SecurityPermissions.BUILD);
            }
        }
        return false;
    }

    public static int getPriority(ItemStack stack) {
        if (stack.hasTagCompound()) {
            return stack.getTagCompound().getInteger("priority");
        } else {
            return 0;
        }
    }

    public static void setPriority(ItemStack stack, int priority) {
        Platform.openNbtData(stack).setInteger("priority", priority);
    }

    private static PriorityCardMode getMode(ItemStack stack) {
        // Setting is stored via ConfigManager and directly retrieved here
        NBTTagCompound tagCompound = Platform.openNbtData(stack);
        try {
            if (tagCompound.hasKey(Settings.PRIORITY_CARD_MODE.name())) {
                return PriorityCardMode.valueOf(tagCompound.getString(Settings.PRIORITY_CARD_MODE.name()));
            }
        } catch (final IllegalArgumentException e) {
            AELog.debug(e);
        }
        return PriorityCardMode.EDIT;
    }
}
