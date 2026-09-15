package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Settings;
import appeng.api.config.WirelessToolGroupBy;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.PlayerSource;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.core.sync.packets.PacketWirelessToolData;
import appeng.helpers.IWirelessLink;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WirelessAnchor;
import appeng.helpers.WirelessKitCommand;
import appeng.helpers.WirelessKitCommand.PinType;
import appeng.helpers.WirelessKitCommand.SubCommand;
import appeng.helpers.WirelessKitCommand.WirelessKitCommands;
import appeng.helpers.WirelessToolDataObject;
import appeng.items.contents.WirelessKitObject;
import appeng.parts.networking.PartWirelessConnector;
import appeng.parts.networking.PartWirelessConnectorOuter;
import appeng.parts.networking.PartWirelessHub;
import appeng.parts.networking.PartWirelessHubOuter;
import appeng.tile.networking.TileWirelessConnector;
import appeng.tile.networking.TileWirelessHub;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;

public class ContainerWirelessKit extends AEBaseContainer implements IConfigManagerHost, IConfigurableObject {

    private final WirelessKitObject toolInv;
    private final IConfigManager clientCM;
    private IConfigManager serverCM;
    private IConfigManagerHost gui;
    private final ArrayList<WirelessToolDataObject> data = new ArrayList<>();
    private final boolean isAEStaffLoaded = Loader.isModLoaded("ae2stuff");

    public ContainerWirelessKit(final InventoryPlayer ip, final WirelessKitObject te) {
        super(ip, te);
        this.toolInv = te;

        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.WIRELESS_TOOL_GROUP_BY, WirelessToolGroupBy.Single);
        this.clientCM.registerSetting(Settings.WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);

        if (Platform.isServer()) {
            this.serverCM = te.getConfigManager();
        }

        bindPlayerInventory(ip, -1000, -1000);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            for (final Settings set : this.serverCM.getSettings()) {
                final Enum<?> sideLocal = this.serverCM.getSetting(set);
                final Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    this.clientCM.putSetting(set, sideLocal);
                    for (final Object crafter : this.crafters) {
                        try {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig(set.name(), sideLocal.name()),
                                    (EntityPlayerMP) crafter);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }
        }

        final ItemStack currentItem = this.getPlayerInv().getCurrentItem();

        if (currentItem != this.toolInv.getItemStack()) {
            if (currentItem != null) {
                if (Platform.isSameItem(this.toolInv.getItemStack(), currentItem)) {
                    this.getPlayerInv()
                            .setInventorySlotContents(this.getPlayerInv().currentItem, this.toolInv.getItemStack());
                } else {
                    this.setValidContainer(false);
                }
            } else {
                this.setValidContainer(false);
            }
        }
    }

    @Override
    public Object getTarget() {
        return this;
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    @Override
    public void addCraftingToCrafters(ICrafting p_75132_1_) {
        super.addCraftingToCrafters(p_75132_1_);
        updateData();
    }

    public void updateData() {
        final NBTTagCompound stash = toolInv.getItemStack().getTagCompound()
                .getCompoundTag(WireLessToolHelper.NbtSuper);
        final List<WirelessAnchor> dcl = WirelessAnchor
                .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));

        World w = toolInv.getWorld();

        data.clear();

        for (final WirelessAnchor network : dcl) {
            final DimensionalCoord pos = network.getCoord();
            if (w.provider.dimensionId == network.getDimension()
                    && w.getTileEntity(pos.x, pos.y, pos.z) instanceof IGridHost gh) {
                final IGrid grid = gridOf(gh, network.getSide());
                if (grid == null) continue;
                for (final IWirelessLink wc : connectorsIn(grid)) {
                    data.add(wc.getDataForTool(network));
                }

                for (final IWirelessLink wc : hubsIn(grid)) {
                    data.add(wc.getDataForTool(network));
                }

            }
        }

        final NBTTagCompound nbtData = new NBTTagCompound();
        nbtData.setTag(
                WireLessToolHelper.NbtSuperPins,
                stash.getTagList(WireLessToolHelper.NbtSuperPins, NBT.TAG_COMPOUND));
        nbtData.setTag(
                WireLessToolHelper.NbtSuperNames,
                stash.getTagList(WireLessToolHelper.NbtSuperNames, NBT.TAG_COMPOUND));

        if (!nbtData.hasNoTags()) {
            for (ICrafting crafter : this.crafters) {
                final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                try {
                    NetworkHandler.instance.sendTo(new PacketWirelessToolData(nbtData, this.data), emp);
                } catch (IOException ignored) {}
            }
        }
    }

    public void processCommand(WirelessKitCommand command) {
        World w = toolInv.getWorld();
        NBTTagCompound stash = toolInv.getItemStack().getTagCompound().getCompoundTag(WireLessToolHelper.NbtSuper);
        switch (command.command) {
            case RENAME_SINGLE, RENAME_GROUP -> {
                switch (command.subCommand.groupBy) {
                    case SINGLE -> {
                        final IWirelessLink twc = WireLessToolHelper.resolveLink(command.subCommand.coord, w);
                        if (twc != null) {
                            twc.setCustomName(command.name);
                        }
                    }

                    case COLOR, NETWORK -> {
                        boolean noData = true;
                        final boolean isColor = command.subCommand.groupBy == PinType.COLOR;
                        final NBTTagList names = stash.getTagList(WireLessToolHelper.NbtSuperNames, 10);
                        for (int i = 0; i < names.tagCount(); i++) {
                            final NBTTagCompound name = names.getCompoundTagAt(i);

                            if (!command.subCommand.networkPos
                                    .equals(WirelessAnchor.readFromNBT(name.getCompoundTag("network"))))
                                continue;

                            if (isColor) {
                                if (name.hasKey("color")
                                        && name.getInteger("color") == command.subCommand.color.ordinal()) {
                                    name.setString("colorName", command.name);
                                    noData = false;
                                    break;
                                }
                            } else {
                                if (!name.hasKey("color")) {
                                    name.setString("networkName", command.name);
                                    noData = false;
                                    break;
                                }
                            }
                        }

                        if (noData) {
                            final NBTTagCompound pin = new NBTTagCompound();
                            final NBTTagCompound network = new NBTTagCompound();
                            command.subCommand.networkPos.writeToNBT(network);
                            pin.setTag("network", network);

                            if (isColor) {
                                pin.setInteger("color", command.subCommand.color.ordinal());
                                pin.setString("colorName", command.name);
                            } else {
                                pin.setString("networkName", command.name);

                            }

                            names.appendTag(pin);
                        }
                        stash.setTag("names", names);
                    }
                }

                updateData();
            }

            case PIN -> {
                final NBTTagList tgl = stash.getTagList(WireLessToolHelper.NbtSuperPins, 10);
                for (int i = 0; i < tgl.tagCount(); i++) {
                    final NBTTagCompound tag = tgl.getCompoundTagAt(i);
                    final boolean isColor = command.subCommand.groupBy == PinType.COLOR;

                    switch (command.subCommand.groupBy) {
                        case SINGLE -> {
                            if (WirelessAnchor.readFromNBT(tag.getCompoundTag("coord"))
                                    .equals(command.subCommand.coord)) {
                                tgl.removeTag(i);
                                return;
                            }
                        }
                        case NETWORK, COLOR -> {
                            if (WirelessAnchor.readFromNBT(tag.getCompoundTag("network"))
                                    .equals(command.subCommand.networkPos)) {

                                if (isColor && tgl.getCompoundTagAt(i).getInteger("color")
                                        != command.subCommand.color.ordinal())
                                    continue;

                                tgl.removeTag(i);
                                return;
                            }
                        }
                    }
                }

                if (command.pin) {
                    final NBTTagCompound pin = new NBTTagCompound();
                    final boolean isColor = command.subCommand.groupBy == PinType.COLOR;

                    switch (command.subCommand.groupBy) {
                        case SINGLE -> {
                            final NBTTagCompound coord = new NBTTagCompound();
                            command.subCommand.coord.writeToNBT(coord);
                            pin.setTag("coord", coord);
                        }

                        case NETWORK, COLOR -> {
                            final NBTTagCompound networkPos = new NBTTagCompound();
                            command.subCommand.networkPos.writeToNBT(networkPos);
                            pin.setTag("network", networkPos);

                            if (isColor) pin.setInteger("color", command.subCommand.color.ordinal());
                        }
                    }

                    pin.setInteger("type", command.subCommand.groupBy.ordinal());
                    tgl.appendTag(pin);
                }
            }
            case DELETE -> {
                final NBTTagList pins = stash.getTagList(WireLessToolHelper.NbtSuperPins, 10);

                // remove pin
                for (int i = 0; i < pins.tagCount(); i++) {
                    final WirelessAnchor network = WirelessAnchor
                            .readFromNBT(pins.getCompoundTagAt(i).getCompoundTag("network"));
                    if (network.equals(command.networkPos)) {
                        pins.removeTag(i);
                    }
                }

                final List<WirelessAnchor> networks = WirelessAnchor
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                networks.removeIf(network -> command.networkPos.equals(network));

                final NBTTagCompound tag = new NBTTagCompound();
                WirelessAnchor.writeListToNBT(tag, networks);
                stash.setTag(WireLessToolHelper.NbtSuperPos, tag);

                updateData();
            }
            case RECOLOR -> {
                for (final SubCommand subCommand : command.toBindRow) {
                    switch (subCommand.groupBy) {
                        case SINGLE -> {
                            final IWirelessLink tw = WireLessToolHelper.resolveLink(subCommand.coord, w);
                            if (tw != null) {
                                if (!WireLessToolHelper
                                        .securityCheck(tw, new PlayerSource(this.getPlayerInv().player, null)))
                                    continue;
                                if (command.color != null) tw.recolourLink(command.color, this.getPlayerInv().player);
                                else tw.madChameleonRecolor();
                            }
                        }

                        case NETWORK, COLOR -> {
                            final boolean isColor = subCommand.groupBy == PinType.COLOR;
                            for (WirelessToolDataObject sd : data) {
                                if (!subCommand.networkPos.equals(sd.network)) continue;

                                final IWirelessLink tw = WireLessToolHelper.resolveLink(sd.cord, w);
                                if (tw == null) continue;

                                if (!WireLessToolHelper
                                        .securityCheck(tw, new PlayerSource(this.getPlayerInv().player, null)))
                                    continue;

                                if (isColor) if (sd.color != subCommand.color) continue;
                                if (command.color != null) {
                                    tw.recolourLink(command.color, this.getPlayerInv().player);
                                } else {
                                    tw.madChameleonRecolor();
                                }
                            }
                        }
                    }
                }

                updateData();
            }
            case BIND -> {
                final List<IWirelessLink> twToBind = this.fletchConnectors(false, command.toBindRow, w);
                final List<IWirelessLink> twTarget = this.fletchConnectors(false, command.targetRow, w);

                WireLessToolHelper.bindRows(twToBind, twTarget, this.getPlayerInv().player);

                // Check if network was absorbed after bind and delete it if
                final List<WirelessAnchor> networks = WirelessAnchor
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                final ArrayList<IGrid> gList = new ArrayList<>();
                for (WirelessAnchor dc : networks) {
                    final DimensionalCoord pos = dc.getCoord();
                    if (w.getTileEntity(pos.x, pos.y, pos.z) instanceof IGridHost gh) {
                        final IGrid newG = gridOf(gh, dc.getSide());
                        if (newG != null) {
                            if (gList.contains(newG)) {
                                final WirelessKitCommand nextCommand = new WirelessKitCommand(
                                        WirelessKitCommands.DELETE);
                                nextCommand.setNetworkPos(dc);
                                processCommand(nextCommand);
                            } else {
                                gList.add(newG);
                            }
                        }
                    } else {
                        final WirelessKitCommand nextCommand = new WirelessKitCommand(WirelessKitCommands.DELETE);
                        nextCommand.setNetworkPos(dc);
                        processCommand(nextCommand);
                    }
                }

                updateData();
            }
            case UNBIND -> {
                final List<WirelessAnchor> networks = WirelessAnchor
                        .readAsListFromNBT((NBTTagCompound) stash.getTag(WireLessToolHelper.NbtSuperPos));
                final ArrayList<IWirelessLink> unbounded = this.fletchConnectors(true, command.toBindRow, w);

                for (final IWirelessLink tw : unbounded) {
                    boolean newNetwork = true;
                    for (final WirelessAnchor dc : networks) {
                        final DimensionalCoord pos = dc.getCoord();
                        if (w.getTileEntity(pos.x, pos.y, pos.z) instanceof IGridHost gh) {
                            try {
                                final IGrid grid = gridOf(gh, dc.getSide());
                                if (tw.getLinkProxy().getGrid().equals(grid)) {
                                    newNetwork = false;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (newNetwork) networks.add(tw.getAnchor());
                }
                final NBTTagCompound tag = new NBTTagCompound();
                WirelessAnchor.writeListToNBT(tag, networks);
                stash.setTag(WireLessToolHelper.NbtSuperPos, tag);

                updateData();
            }
            default -> {}
        }
    }

    private ArrayList<IWirelessLink> fletchConnectors(final boolean unbind,
            final ArrayList<WirelessKitCommand.SubCommand> list, final World w) {
        final ArrayList<IWirelessLink> connectors = new ArrayList<>();
        for (final SubCommand subCommand : list) {
            switch (subCommand.groupBy) {
                case SINGLE -> {
                    final IWirelessLink wc = WireLessToolHelper.resolveLink(subCommand.coord, w);
                    if (wc != null) {
                        if (unbind) {
                            connectors.addAll(wc.getConnectedLinks());
                            WireLessToolHelper.breakConnection(wc, new PlayerSource(this.getPlayerInv().player, null));
                        }
                        connectors.add(wc);
                    }
                }
                case NETWORK, COLOR -> {
                    final DimensionalCoord network = subCommand.networkPos.getCoord();
                    final ForgeDirection networkSide = subCommand.networkPos.getSide();
                    final boolean isColor = subCommand.groupBy == PinType.COLOR;;
                    if (w.getTileEntity(network.x, network.y, network.z) instanceof IGridHost gh) {
                        if (subCommand.includeConnectors) {
                            for (final IWirelessLink wc : connectorsIn(gridOf(gh, networkSide))) {
                                if (!wc.isLinked()) {
                                    if (isColor && wc.getColor() != subCommand.color) continue;
                                    if (unbind) {
                                        connectors.addAll(wc.getConnectedLinks());
                                        WireLessToolHelper.breakConnection(
                                                wc,
                                                new PlayerSource(this.getPlayerInv().player, null));
                                    }
                                    connectors.add(wc);
                                }
                            }
                        }

                        if (subCommand.includeHubs) {
                            for (final IWirelessLink wc : hubsIn(gridOf(gh, networkSide))) {
                                if (wc.getFreeSlots() > 0 || unbind) {
                                    if (isColor && wc.getColor() != subCommand.color) continue;
                                    if (unbind) {
                                        connectors.addAll(wc.getConnectedLinks());
                                        WireLessToolHelper.breakConnection(
                                                wc,
                                                new PlayerSource(this.getPlayerInv().player, null));
                                    }
                                    connectors.add(wc);
                                }
                            }
                        }
                    }
                }
            }
        }

        return connectors;
    }

    private static IGrid gridOf(final IGridHost host, final ForgeDirection side) {
        final IGridNode node = host.getGridNode(side);
        return node == null ? null : node.getGrid();
    }

    private static List<IWirelessLink> connectorsIn(final IGrid grid) {
        return machinesIn(
                grid,
                TileWirelessConnector.class,
                PartWirelessConnector.class,
                PartWirelessConnectorOuter.class);
    }

    private static List<IWirelessLink> hubsIn(final IGrid grid) {
        return machinesIn(grid, TileWirelessHub.class, PartWirelessHub.class, PartWirelessHubOuter.class);
    }

    @SafeVarargs
    private static List<IWirelessLink> machinesIn(final IGrid grid, final Class<? extends IGridHost>... classes) {
        final List<IWirelessLink> found = new ArrayList<>();
        if (grid == null) return found;

        // an outer fixture owns two nodes sharing one machine; only the link-carrying node counts
        final Set<IWirelessLink> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final Class<? extends IGridHost> clazz : classes) {
            for (final IGridNode gn : grid.getMachines(clazz)) {
                if (gn.getMachine() instanceof IWirelessLink link && link.getLinkProxy().getNode() == gn
                        && seen.add(link))
                    found.add(link);
            }
        }
        return found;
    }
}
