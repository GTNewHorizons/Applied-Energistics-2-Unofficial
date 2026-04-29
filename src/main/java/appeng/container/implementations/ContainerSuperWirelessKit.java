package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.bdew.ae2stuff.machines.wireless.TileWireless;
import net.bdew.lib.block.BlockRef;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableList;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessToolGroupBy;
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
import appeng.core.sync.packets.PacketSuperWirelessToolData;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.SuperWirelessKitCommand;
import appeng.helpers.SuperWirelessKitCommand.PinType;
import appeng.helpers.SuperWirelessKitCommand.SubCommand;
import appeng.helpers.SuperWirelessKitCommand.SuperWirelessKitCommands;
import appeng.helpers.SuperWirelessToolDataObject;
import appeng.helpers.WireLessToolHelper;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.tile.networking.TileWirelessBase;
import appeng.tile.networking.TileWirelessConnector;
import appeng.tile.networking.TileWirelessHub;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;

public class ContainerSuperWirelessKit extends AEBaseContainer implements IConfigManagerHost, IConfigurableObject {

    private final SuperWirelessKitObject toolInv;
    private final IConfigManager clientCM;
    private IConfigManager serverCM;
    private IConfigManagerHost gui;
    private final ArrayList<SuperWirelessToolDataObject> data = new ArrayList<>();
    private final boolean isAEStaffLoaded = Loader.isModLoaded("ae2stuff");

    public ContainerSuperWirelessKit(final InventoryPlayer ip, final SuperWirelessKitObject te) {
        super(ip, te);
        this.toolInv = te;

        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY, SuperWirelessToolGroupBy.Single);
        this.clientCM.registerSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);

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
        final List<DimensionalCoord> dcl = DimensionalCoord
                .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));

        World w = toolInv.getWorld();

        data.clear();

        for (final DimensionalCoord network : dcl) {
            if (w.provider.dimensionId == network.getDimension()
                    && w.getTileEntity(network.x, network.y, network.z) instanceof IGridHost gh) {
                final IGrid grid = gh.getGridNode(ForgeDirection.UNKNOWN).getGrid();
                if (grid == null) continue;
                for (IGridNode gn : grid.getMachines(TileWirelessConnector.class)) {
                    TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                    data.add(wc.getDataForTool(network));
                }

                for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        .getMachines(TileWirelessHub.class)) {
                    TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
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
                    NetworkHandler.instance.sendTo(new PacketSuperWirelessToolData(nbtData, this.data), emp);
                } catch (IOException ignored) {}
            }
        }
    }

    public void processCommand(SuperWirelessKitCommand command) {
        World w = toolInv.getWorld();
        NBTTagCompound stash = toolInv.getItemStack().getTagCompound().getCompoundTag(WireLessToolHelper.NbtSuper);
        switch (command.command) {
            case RENAME_SINGLE, RENAME_GROUP -> {
                switch (command.subCommand.groupBy) {
                    case SINGLE -> {
                        final DimensionalCoord coord = command.subCommand.coord;
                        if (w.getTileEntity(coord.x, coord.y, coord.z) instanceof TileWirelessBase twc) {
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
                                    .equals(DimensionalCoord.readFromNBT(name.getCompoundTag("network"))))
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
                            if (DimensionalCoord.readFromNBT(tag.getCompoundTag("coord"))
                                    .equals(command.subCommand.coord)) {
                                tgl.removeTag(i);
                                return;
                            }
                        }
                        case NETWORK, COLOR -> {
                            if (DimensionalCoord.readFromNBT(tag.getCompoundTag("network"))
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
                    final DimensionalCoord network = DimensionalCoord
                            .readFromNBT(pins.getCompoundTagAt(i).getCompoundTag("network"));
                    if (network.equals(command.networkPos)) {
                        pins.removeTag(i);
                    }
                }

                final List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                networks.removeIf(network -> command.networkPos.equals(network));

                final NBTTagCompound tag = new NBTTagCompound();
                DimensionalCoord.writeListToNBT(tag, networks);
                stash.setTag(WireLessToolHelper.NbtSuperPos, tag);

                updateData();
            }
            case RECOLOR -> {
                for (final SubCommand subCommand : command.toBindRow) {
                    switch (subCommand.groupBy) {
                        case SINGLE -> {
                            if (w.getTileEntity(
                                    subCommand.coord.x,
                                    subCommand.coord.y,
                                    subCommand.coord.z) instanceof TileWirelessBase tw) {
                                if (!WireLessToolHelper
                                        .securityCheck(tw, new PlayerSource(this.getPlayerInv().player, null)))
                                    continue;
                                if (subCommand.coord != null)
                                    tw.recolourBlock(ForgeDirection.UNKNOWN, command.color, this.getPlayerInv().player);
                                else tw.madChameleonRecolor();
                            }
                        }

                        case NETWORK, COLOR -> {
                            final boolean isColor = subCommand.groupBy == PinType.COLOR;
                            for (SuperWirelessToolDataObject sd : data) {
                                if (!subCommand.networkPos.equals(sd.network)) continue;

                                if (!(w.getTileEntity(sd.cord.x, sd.cord.y, sd.cord.z) instanceof TileWirelessBase tw))
                                    continue;

                                if (!WireLessToolHelper
                                        .securityCheck(tw, new PlayerSource(this.getPlayerInv().player, null)))
                                    continue;

                                if (isColor) if (sd.color != subCommand.color) continue;
                                if (subCommand.color != null) {
                                    tw.recolourBlock(ForgeDirection.UNKNOWN, command.color, this.getPlayerInv().player);
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
                final List<TileWirelessBase> twToBind = this.fletchConnectors(false, command.toBindRow, w);
                final List<TileWirelessBase> twTarget = this.fletchConnectors(false, command.targetRow, w);

                WireLessToolHelper.bindRows(twToBind, twTarget, this.getPlayerInv().player);

                // Check if network was absorbed after bind and delete it if
                final List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                final ArrayList<IGrid> gList = new ArrayList<>();
                for (DimensionalCoord dc : networks) {
                    if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                        final IGrid newG = gh.getGridNode(ForgeDirection.UNKNOWN).getGrid();
                        if (newG != null) {
                            if (gList.contains(newG)) {
                                final SuperWirelessKitCommand nextCommand = new SuperWirelessKitCommand(
                                        SuperWirelessKitCommands.DELETE);
                                nextCommand.setNetworkPos(dc);
                                processCommand(nextCommand);
                            } else {
                                gList.add(newG);
                            }
                        }
                    } else {
                        final SuperWirelessKitCommand nextCommand = new SuperWirelessKitCommand(
                                SuperWirelessKitCommands.DELETE);
                        nextCommand.setNetworkPos(dc);
                        processCommand(nextCommand);
                    }
                }

                updateData();
            }
            case UNBIND -> {
                final List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT((NBTTagCompound) stash.getTag(WireLessToolHelper.NbtSuperPos));
                final ArrayList<TileWirelessBase> unbounded = this.fletchConnectors(true, command.toBindRow, w);

                for (final TileWirelessBase tw : unbounded) {
                    boolean newNetwork = true;
                    for (final DimensionalCoord dc : networks) {
                        if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                            try {
                                final IGrid grid = gh.getGridNode(ForgeDirection.UNKNOWN).getGrid();
                                if (tw.getProxy().getGrid().equals(grid)) {
                                    newNetwork = false;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (newNetwork) networks.add(tw.getLocation());
                }
                final NBTTagCompound tag = new NBTTagCompound();
                DimensionalCoord.writeListToNBT(tag, networks);
                stash.setTag(WireLessToolHelper.NbtSuperPos, tag);

                updateData();
            }
            case AE2STUFF_REPLACE -> {
                if (!isAEStaffLoaded) return;

                final List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                for (DimensionalCoord dc : networks) {
                    IGridHost gh = w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost ghInstance ? ghInstance
                            : null;
                    if (gh == null) return;

                    Set<SuperWirelessToolDataObject> dataSet = new HashSet<>();

                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                            .getMachines(TileWireless.class)) {
                        TileWireless wc = (TileWireless) gn.getMachine();
                        DimensionalCoord targetDC = null;

                        if (wc.link().value().isDefined()) {
                            BlockRef temp = wc.link().value().get();
                            targetDC = new DimensionalCoord(w, temp.x(), temp.y(), temp.z());
                        }

                        SuperWirelessToolDataObject data = new SuperWirelessToolDataObject(
                                null,
                                wc.hasCustomName() ? wc.getCustomName() : null,
                                wc.getLocation(),
                                targetDC != null,
                                targetDC == null ? ImmutableList.of() : ImmutableList.of(targetDC),
                                wc.getColor(),
                                -1,
                                wc.isHub(),
                                -1);
                        dataSet.add(data);
                    }

                    for (SuperWirelessToolDataObject data : dataSet) {
                        w.setBlockToAir(data.cord.x, data.cord.y, data.cord.z);
                    }

                    for (SuperWirelessToolDataObject data : dataSet) {
                        if (data.isHub) {
                            w.setBlock(
                                    data.cord.x,
                                    data.cord.y,
                                    data.cord.z,
                                    AEApi.instance().definitions().blocks().wirelessHub().maybeBlock().get());
                        } else {
                            w.setBlock(
                                    data.cord.x,
                                    data.cord.y,
                                    data.cord.z,
                                    AEApi.instance().definitions().blocks().wirelessConnector().maybeBlock().get());
                        }

                        if (w.getTileEntity(data.cord.x, data.cord.y, data.cord.z) instanceof TileWirelessBase newCon) {
                            if (data.customName != null) newCon.setCustomName(data.customName);
                            newCon.recolourBlock(ForgeDirection.UNKNOWN, data.color, null);
                        }
                    }

                    for (SuperWirelessToolDataObject data : dataSet) {
                        if (w.getTileEntity(data.cord.x, data.cord.y, data.cord.z) instanceof TileWirelessBase newCon) {
                            for (final DimensionalCoord targetDc : data.targets) {
                                if (!(w.getTileEntity(targetDc.x, targetDc.y, targetDc.z) instanceof TileWirelessBase))
                                    continue;
                                newCon.injectConnection(targetDc);
                            }
                        }
                    }
                }

            }
            default -> {}
        }
    }

    private ArrayList<TileWirelessBase> fletchConnectors(final boolean unbind,
            final ArrayList<SuperWirelessKitCommand.SubCommand> list, final World w) {
        final ArrayList<TileWirelessBase> connectors = new ArrayList<>();
        for (final SubCommand subCommand : list) {
            switch (subCommand.groupBy) {
                case SINGLE -> {
                    if (w.getTileEntity(
                            subCommand.coord.x,
                            subCommand.coord.y,
                            subCommand.coord.z) instanceof TileWirelessBase wc) {
                        if (unbind) {
                            connectors.addAll(wc.getConnectedTiles());
                            WireLessToolHelper.breakConnection(wc, new PlayerSource(this.getPlayerInv().player, null));
                        }
                        connectors.add(wc);
                    }
                }
                case NETWORK, COLOR -> {
                    final DimensionalCoord network = subCommand.networkPos;
                    final boolean isColor = subCommand.groupBy == PinType.COLOR;;
                    if (w.getTileEntity(network.x, network.y, network.z) instanceof IGridHost gh) {
                        if (subCommand.includeConnectors) {
                            for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                    .getMachines(TileWirelessBase.class)) {
                                TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                                if (!wc.isLinked()) {
                                    if (isColor && wc.getColor() != subCommand.color) continue;
                                    if (unbind) {
                                        connectors.addAll(wc.getConnectedTiles());
                                        WireLessToolHelper.breakConnection(
                                                wc,
                                                new PlayerSource(this.getPlayerInv().player, null));
                                    }
                                    connectors.add(wc);
                                }
                            }
                        }

                        if (subCommand.includeHubs) {
                            for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                    .getMachines(TileWirelessHub.class)) {
                                TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
                                if (wc.getFreeSlots() > 0 || unbind) {
                                    if (isColor && wc.getColor() != subCommand.color) continue;
                                    if (unbind) {
                                        connectors.addAll(wc.getConnectedTiles());
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
}
