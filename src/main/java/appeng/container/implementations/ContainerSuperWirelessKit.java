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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessToolGroupBy;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSuperWirelessToolData;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.SuperWirelessToolDataObject;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.tile.networking.TileWirelessBase;
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
        super(ip, null, null);
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
        NBTTagCompound stash = toolInv.getItemStack().getTagCompound().getCompoundTag("super");
        List<DimensionalCoord> dcl = DimensionalCoord.readAsListFromNBT((NBTTagCompound) stash.getTag("pos"));

        World w = toolInv.getWorld();

        data.clear();

        for (int i = 0; i < dcl.size(); i++) {
            if (w.provider.dimensionId == dcl.get(i).getDimension()
                    && w.getTileEntity(dcl.get(i).x, dcl.get(i).y, dcl.get(i).z) instanceof IGridHost gh) {
                for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        .getMachines(TileWirelessBase.class)) { // TODO: Check if getMachines work
                    TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                    data.add(wc.getDataForTool(i));
                }

                for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        .getMachines(TileWirelessHub.class)) {
                    TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
                    data.add(wc.getDataForTool(i));
                }

            }
        }

        NBTTagCompound nbtData = new NBTTagCompound();
        SuperWirelessToolDataObject.writeToNBTasList(data, nbtData);
        nbtData.setTag("pins", stash.getTagList("pins", 10));
        nbtData.setTag("names", stash.getTagList("names", 10));

        if (!nbtData.hasNoTags()) {
            for (ICrafting crafter : this.crafters) {
                final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                try {
                    NetworkHandler.instance.sendTo(new PacketSuperWirelessToolData(nbtData), emp);
                } catch (IOException ignored) {}
            }
        }
    }

    public void processCommand(NBTTagCompound command) {
        World w = toolInv.getWorld();
        NBTTagCompound stash = toolInv.getItemStack().getTagCompound().getCompoundTag("super");
        switch (command.getString("command")) {
            case "renameSingle" -> {
                DimensionalCoord cord = DimensionalCoord.readFromNBT(command.getCompoundTag("cord"));
                TileEntity te = w.getTileEntity(cord.x, cord.y, cord.z);
                if (te instanceof TileWirelessBase twc) {
                    twc.setCustomName(command.getString("name"));
                }
            }
            case "renameGroup" -> {
                String newName = command.getString("name");
                int network = command.getInteger("network");
                boolean isByColor = command.hasKey("color");
                int color = command.getInteger("color");

                NBTTagList names = stash.getTagList("names", 10);
                boolean noData = true;
                for (int i = 0; i < names.tagCount(); i++) {
                    NBTTagCompound name = names.getCompoundTagAt(i);
                    if (isByColor) {
                        if (name.hasKey("network") && network == name.getInteger("network")
                                && name.hasKey("color")
                                && name.getInteger("color") == color) {
                            name.setString("colorName", newName);
                            noData = false;
                            break;
                        }
                    } else {
                        if (!name.hasKey("color") && name.hasKey("network") && network == name.getInteger("network")) {
                            name.setString("networkName", newName);
                            noData = false;
                            break;
                        }
                    }
                }
                if (noData) {
                    if (command.hasKey("color")) {
                        NBTTagCompound name = new NBTTagCompound();
                        name.setInteger("network", network);
                        name.setInteger("color", color);
                        name.setString("colorName", newName);
                        names.appendTag(name);
                    } else {
                        NBTTagCompound pin = new NBTTagCompound();
                        pin.setInteger("network", network);
                        pin.setString("networkName", newName);
                        names.appendTag(pin);
                    }
                }
                stash.setTag("names", names);
                updateData();
            }
            case "pin" -> {
                int network = command.getInteger("network");
                int color = command.getInteger("color");
                NBTTagCompound cord = command.getCompoundTag("cord");
                String type = command.getString("type");
                boolean pinMode = command.getBoolean("pin");
                NBTTagList tgl = stash.getTagList("pins", 10);

                command.removeTag("command");
                command.removeTag("pin");

                boolean noTag = true;
                for (int i = 0; i < tgl.tagCount(); i++) {
                    boolean toBreak = false;
                    switch (type) {
                        case "single" -> {
                            if (pinMode) toBreak = true;
                            if (tgl.getCompoundTagAt(i).getCompoundTag("cord").equals(cord)) {
                                tgl.removeTag(i);
                                toBreak = true;
                            }
                        }
                        case "network" -> {
                            if (tgl.getCompoundTagAt(i).getInteger("network") == network) {
                                if (pinMode) {
                                    tgl.func_150304_a(i, command);
                                    noTag = false;
                                } else {
                                    tgl.removeTag(i);
                                }
                                toBreak = true;
                            }
                        }
                        case "color" -> {
                            if (tgl.getCompoundTagAt(i).getInteger("network") == network
                                    && tgl.getCompoundTagAt(i).getInteger("color") == color) {
                                if (pinMode) {
                                    tgl.func_150304_a(i, command);
                                    noTag = false;
                                } else {
                                    tgl.removeTag(i);
                                }
                                toBreak = true;
                            }
                        }
                    }
                    if (toBreak) break;
                }

                if (noTag && pinMode) {
                    tgl.appendTag(command);
                }

            }
            case "delete" -> {
                NBTTagList pins = stash.getTagList("pins", 10);
                int network = command.getInteger("network");

                // remove pin
                for (int i = 0; i < pins.tagCount(); i++) {
                    int nbt_network = pins.getCompoundTagAt(i).getInteger("network");
                    if (nbt_network == network) {
                        pins.removeTag(i);
                    }
                }

                // move pins data according to new subsequence
                for (int i = 0; i < pins.tagCount(); i++) {
                    int nbt_network = pins.getCompoundTagAt(i).getInteger("network");
                    if (nbt_network > network) {
                        pins.getCompoundTagAt(i).setInteger("network", nbt_network - 1);
                    }
                }

                // move names data according to new subsequence
                NBTTagList names = stash.getTagList("names", 10);
                for (int i = 0; i < names.tagCount(); i++) {
                    int nbt_network = names.getCompoundTagAt(i).getInteger("network");
                    if (nbt_network == network) {
                        names.removeTag(i);
                    }
                }

                // move network list for keep subsequence
                for (int i = 0; i < names.tagCount(); i++) {
                    int nbt_network = names.getCompoundTagAt(i).getInteger("network");
                    if (nbt_network > network) {
                        names.getCompoundTagAt(i).setInteger("network", nbt_network - 1);
                    }
                }

                NBTTagCompound pos = stash.getCompoundTag("pos");
                List<DimensionalCoord> dcl = DimensionalCoord.readAsListFromNBT(pos);
                for (int i = 0; i < dcl.size(); i++) {
                    if (i == network) {
                        dcl.remove(i);
                        break;
                    }
                }

                NBTTagCompound newPos = new NBTTagCompound();
                DimensionalCoord.writeListToNBT(newPos, dcl);
                stash.setTag("pos", newPos);
                updateData();
            }
            case "recolor" -> {
                AEColor color = command.getInteger("color") != -1 ? AEColor.values()[command.getInteger("color")]
                        : null;
                if (command.hasKey("cords")) {
                    List<DimensionalCoord> dc = DimensionalCoord
                            .readAsListFromNBT((NBTTagCompound) command.getTag("cords"));
                    for (DimensionalCoord sdc : dc) {
                        TileEntity te = w.getTileEntity(sdc.x, sdc.y, sdc.z);
                        if (te instanceof TileWirelessBase tw) {
                            if (color != null) {
                                tw.recolourBlock(ForgeDirection.UNKNOWN, color, this.getPlayerInv().player);
                            } else {
                                tw.madChameleonRecolor();
                            }
                        }
                    }
                }

                if (command.hasKey("group")) {
                    NBTTagList tagList = command.getTagList("group", 10);
                    int[] networksCache = new int[tagList.tagCount()];
                    AEColor[] colorsCache = new AEColor[tagList.tagCount()];
                    for (int i = 0; i < tagList.tagCount(); i++) {
                        NBTTagCompound tag = tagList.getCompoundTagAt(i);
                        networksCache[i] = tag.getInteger("network");
                        if (tag.hasKey("color")) {
                            colorsCache[i] = AEColor.values()[tag.getInteger("color")];
                        }
                    }

                    for (SuperWirelessToolDataObject sd : data) {
                        for (int i = 0; i < networksCache.length; i++) {
                            if (networksCache[i] == sd.network) {
                                if (colorsCache[i] != null) {
                                    if (sd.color == colorsCache[i]) {
                                        TileEntity te = w.getTileEntity(sd.cord.x, sd.cord.y, sd.cord.z);
                                        if (te instanceof TileWirelessBase tw) {
                                            if (color != null) {
                                                tw.recolourBlock(
                                                        ForgeDirection.UNKNOWN,
                                                        color,
                                                        this.getPlayerInv().player);
                                            } else {
                                                tw.madChameleonRecolor();
                                            }
                                        }
                                    }
                                } else {
                                    TileEntity te = w.getTileEntity(sd.cord.x, sd.cord.y, sd.cord.z);
                                    if (te instanceof TileWirelessBase tw) {
                                        if (color != null) {
                                            tw.recolourBlock(ForgeDirection.UNKNOWN, color, this.getPlayerInv().player);
                                        } else {
                                            tw.madChameleonRecolor();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                updateData();
            }
            case "bind" -> {
                List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT((NBTTagCompound) stash.getTag("pos"));

                NBTTagList toBind = command.getTagList("toBind", 10);
                ArrayList<TileWirelessBase> twToBind = new ArrayList<>();
                for (int i = 0; i < toBind.tagCount(); i++) {
                    NBTTagCompound tag = toBind.getCompoundTagAt(i);
                    if (tag.hasKey("cord")) {
                        DimensionalCoord dc = DimensionalCoord.readFromNBT(tag.getCompoundTag("cord"));
                        if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof TileWirelessBase wc) {
                            twToBind.add(wc);
                        }
                    } else {
                        int network = tag.getInteger("network");
                        AEColor color = tag.hasKey("color") ? AEColor.values()[tag.getInteger("color")] : null;
                        if (networks.size() >= network) {
                            DimensionalCoord dc = networks.get(network);
                            if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                                if (!tag.hasKey("incCon")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessBase.class)) {
                                        TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                                        if (!wc.isLinked()) {
                                            if (color != null) {
                                                if (wc.getColor() == color) {
                                                    twToBind.add(wc);
                                                }
                                            } else {
                                                twToBind.add(wc);
                                            }
                                        }
                                    }
                                }
                                if (!tag.hasKey("incHub")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessHub.class)) {
                                        TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
                                        if (wc.getFreeSlots() > 0) {
                                            if (color != null) {
                                                if (wc.getColor() == color) {
                                                    twToBind.add(wc);
                                                }
                                            } else {
                                                twToBind.add(wc);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                NBTTagList target = command.getTagList("target", 10);
                ArrayList<TileWirelessBase> twTarget = new ArrayList<>();
                for (int i = 0; i < target.tagCount(); i++) {
                    NBTTagCompound tag = target.getCompoundTagAt(i);
                    if (tag.hasKey("cord")) {
                        DimensionalCoord dc = DimensionalCoord.readFromNBT(tag.getCompoundTag("cord"));
                        if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof TileWirelessBase wc) {
                            twTarget.add(wc);
                        }
                    } else {
                        int network = tag.getInteger("network");
                        AEColor color = tag.hasKey("color") ? AEColor.values()[tag.getInteger("color")] : null;
                        if (networks.size() >= network) {
                            DimensionalCoord dc = networks.get(network);
                            if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                                if (!tag.hasKey("incCon")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessBase.class)) {
                                        TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                                        if (!wc.isLinked()) {
                                            if (color != null) {
                                                if (wc.getColor() == color) {
                                                    twTarget.add(wc);
                                                }
                                            } else {
                                                twTarget.add(wc);
                                            }
                                        }
                                    }
                                }
                                if (!tag.hasKey("incHub")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessHub.class)) {
                                        TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
                                        if (wc.getFreeSlots() > 0) {
                                            if (color != null) {
                                                if (wc.getColor() == color) {
                                                    twTarget.add(wc);
                                                }
                                            } else {
                                                twTarget.add(wc);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                int i = 0;
                int ii = 0;
                while (twToBind.size() > i && twToBind.size() > ii) {
                    while (twTarget.get(ii).getFreeSlots() > 0) {
                        if (twToBind.get(i).doLink(twTarget.get(ii))) {
                            i++;
                            if (!(twToBind.size() > i)) {
                                break;
                            }
                        } else {
                            ii++;
                        }
                    }
                    ii++;
                }

                // Check if network was absorbed after bind and delete it if
                ArrayList<IGrid> gList = new ArrayList<>();
                int deletedCounter = 0;
                for (int j = 0; j < networks.size(); j++) {
                    DimensionalCoord dc = networks.get(j);
                    if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                        IGrid newG = gh.getGridNode(ForgeDirection.UNKNOWN).getGrid();
                        if (newG != null) {
                            if (gList.contains(newG)) {
                                NBTTagCompound tag = new NBTTagCompound();
                                tag.setString("command", "delete");
                                tag.setInteger("network", j - deletedCounter);
                                processCommand(tag);
                                deletedCounter++;
                            } else {
                                gList.add(newG);
                            }
                        }
                    } else {
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setString("command", "delete");
                        tag.setInteger("network", j - deletedCounter);
                        processCommand(tag);
                        deletedCounter++;
                    }
                }

                updateData();
            }
            case "unbind" -> {
                List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT((NBTTagCompound) stash.getTag("pos"));

                NBTTagList toBind = command.getTagList("toBind", 10);
                for (int i = 0; i < toBind.tagCount(); i++) {
                    NBTTagCompound tag = toBind.getCompoundTagAt(i);
                    if (tag.hasKey("cord")) {
                        DimensionalCoord dc = DimensionalCoord.readFromNBT(tag.getCompoundTag("cord"));
                        if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof TileWirelessBase wc) {
                            wc.doUnlink();
                        }
                    } else {
                        int network = tag.getInteger("network");
                        AEColor color = tag.hasKey("color") ? AEColor.values()[tag.getInteger("color")] : null;
                        if (networks.size() >= network) {
                            DimensionalCoord dc = networks.get(network);
                            if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost gh) {
                                if (!tag.hasKey("incCon")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessBase.class)) {
                                        TileWirelessBase wc = (TileWirelessBase) gn.getMachine();
                                        if (color != null) {
                                            if (wc.getColor() == color) {
                                                wc.doUnlink();
                                            }
                                        } else {
                                            wc.doUnlink();
                                        }
                                    }
                                }
                                if (!tag.hasKey("incHub")) {
                                    for (IGridNode gn : gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                            .getMachines(TileWirelessHub.class)) {
                                        TileWirelessHub wc = (TileWirelessHub) gn.getMachine();
                                        if (color != null) {
                                            if (wc.getColor() == color) {
                                                wc.doUnlink();
                                            }
                                        } else {
                                            wc.doUnlink();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                updateData();
            }
            case "ae2stuff_convert" -> {
                if (!isAEStaffLoaded) return;

                List<DimensionalCoord> networks = DimensionalCoord
                        .readAsListFromNBT((NBTTagCompound) stash.getTag("pos"));
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
                                -1,
                                wc.hasCustomName() ? wc.getCustomName() : null,
                                wc.getLocation(),
                                targetDC != null,
                                targetDC,
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

                        TileEntity te = w.getTileEntity(data.cord.x, data.cord.y, data.cord.z);
                        if (te instanceof TileWirelessBase newCon) {
                            if (data.customName != null) newCon.setCustomName(data.customName);
                            if (data.isConnected) {
                                // newCon.injectTarget(data.targetCord); TODO
                            }
                            newCon.recolourBlock(ForgeDirection.UNKNOWN, data.color, null);
                        }
                    }
                }

            }
            default -> {}
        }
    }
}
