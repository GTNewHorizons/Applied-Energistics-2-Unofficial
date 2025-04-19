package appeng.items.tools;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessTool;
import appeng.api.config.SuperWirelessToolAdvanced;
import appeng.api.config.SuperWirelessToolGroupBy;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGridHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.items.AEBaseItem;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.tile.networking.TileWirelessConnector;
import appeng.util.ConfigManager;
import appeng.util.Platform;

public class ToolSuperWirelessKit extends AEBaseItem implements IGuiItem {

    public ToolSuperWirelessKit() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    @Override
    public void onUpdate(ItemStack is, World w, Entity e, int p_77663_4_, boolean p_77663_5_) {
        if (!is.hasTagCompound()) {
            setCleanNBT(is);
        }
    }

    private void setCleanNBT(ItemStack is) {
        NBTTagCompound newNBT = new NBTTagCompound();
        newNBT.setTag("simple", new NBTTagCompound());
        newNBT.setTag("advanced", new NBTTagCompound());

        NBTTagCompound newTag = new NBTTagCompound();
        newTag.setTag("pins", new NBTTagList());
        newTag.setTag("names", new NBTTagList());
        newTag.setTag("pos", new NBTTagCompound());
        newNBT.setTag("super", newTag);
        is.setTagCompound(newNBT);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World w, EntityPlayer p) {
        if (Platform.isServer()) {
            if (Platform.keyBindTab.isKeyDown(p)) {
                IConfigManager cm = getConfigManager(is);
                final Enum<?> newState = Platform.rotateEnum(
                        cm.getSetting(Settings.SUPER_WIRELESS_TOOL),
                        false,
                        Settings.SUPER_WIRELESS_TOOL.getPossibleValues());
                cm.putSetting(Settings.SUPER_WIRELESS_TOOL, newState);
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolSuperWirelessKit.set",
                                EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode."
                                                + newState.toString().toLowerCase(Locale.US))));
            } else {
                SuperWirelessTool mode = (SuperWirelessTool) getConfigManager(is)
                        .getSetting(Settings.SUPER_WIRELESS_TOOL);
                if (p.isSneaking() && Platform.keyBindLCtrl.isKeyDown(p)) {
                    switch (mode) {
                        case Simple -> {
                            is.getTagCompound().setTag("simple", new NBTTagCompound());
                        }
                        case Advanced -> {
                            is.getTagCompound().setTag("advanced", new NBTTagCompound());
                        }
                        case Super -> {
                            NBTTagCompound newTag = new NBTTagCompound();
                            newTag.setTag("pins", new NBTTagList());
                            newTag.setTag("names", new NBTTagList());
                            newTag.setTag("pos", new NBTTagCompound());
                            is.getTagCompound().setTag("super", newTag);
                        }
                    }
                    p.addChatMessage(
                            new ChatComponentTranslation(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.empty",
                                    EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                                            "item.appliedenergistics2.ToolSuperWirelessKit.mode."
                                                    + mode.toString().toLowerCase(Locale.US))));
                } else if (p.isSneaking() && mode == SuperWirelessTool.Advanced) {
                    IConfigManager cm = getConfigManager(is);
                    final Enum<?> newState = Platform.rotateEnum(
                            cm.getSetting(Settings.SUPER_WIRELESS_TOOL_ADVANCED),
                            false,
                            Settings.SUPER_WIRELESS_TOOL_ADVANCED.getPossibleValues());
                    cm.putSetting(Settings.SUPER_WIRELESS_TOOL_ADVANCED, newState);
                    p.addChatMessage(
                            new ChatComponentTranslation(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced."
                                            + newState.toString().toLowerCase(Locale.US)
                                            + ".activated"));
                } else if (mode == SuperWirelessTool.Super) {
                    Platform.openGUI(p, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_SUPER_WIRELESS_KIT);
                }
            }
        }
        return is;
    }

    public boolean performConnection(TileWirelessConnector wc, DimensionalCoord dc, EntityPlayer p) {
        if (wc.getLocation().getDimension() == dc.getDimension()) {
            if (wc.isHub() && wc.getFreeSlots() == 0) {
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.simple.bound.targethubfull"));
                return false;
            }
            if (wc.setupConnection(dc)) {
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolSuperWirelessKit.connected",
                                dc.x,
                                dc.y,
                                dc.z).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                return true;
            } else {
                p.addChatMessage(
                        new ChatComponentTranslation("item.appliedenergistics2.ToolSuperWirelessKit.failed")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            }

        } else {
            p.addChatMessage(new ChatComponentTranslation("item.appliedenergistics2.ToolSuperWirelessKit.dimension"));
        }
        return false;
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer p, World w, int x, int y, int z, int side, float xOff,
            float yOff, float zOff) {
        if (Platform.isServer()) {
            SuperWirelessTool mode = (SuperWirelessTool) getConfigManager(is).getSetting(Settings.SUPER_WIRELESS_TOOL);
            TileEntity te = w.getTileEntity(x, y, z);

            if (!(te instanceof IGridHost gh)) {
                return false;
            } else if (!((ISecurityGrid) gh.getGridNode(ForgeDirection.UNKNOWN).getGrid().getCache(ISecurityGrid.class))
                    .hasPermission(p, SecurityPermissions.BUILD)) {
                        p.addChatMessage(
                                new ChatComponentTranslation(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.security.player"));
                        return false;
                    }

            switch (mode) {
                case Simple -> {
                    if (te instanceof TileWirelessConnector wc) {
                        NBTTagCompound tag = is.getTagCompound().getCompoundTag("simple");
                        if (tag.hasNoTags()) {
                            DimensionalCoord dc = wc.getLocation();
                            dc.writeToNBT(tag);
                            is.getTagCompound().setTag("simple", tag);
                            p.addChatMessage(
                                    new ChatComponentTranslation(
                                            "item.appliedenergistics2.ToolSuperWirelessKit.bound",
                                            dc.x,
                                            dc.y,
                                            dc.z).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                        } else {
                            DimensionalCoord dc = DimensionalCoord.readFromNBT(tag);
                            if (performConnection(wc, dc, p)) {
                                is.getTagCompound().setTag("simple", new NBTTagCompound());
                                return true;
                            }
                        }
                    }
                }
                case Advanced -> {
                    if (te instanceof TileWirelessConnector wc) {
                        DimensionalCoord sdc = wc.getLocation();
                        List<DimensionalCoord> dcl = DimensionalCoord
                                .readAsListFromNBT(is.getTagCompound().getCompoundTag("advanced"));
                        SuperWirelessToolAdvanced mod = (SuperWirelessToolAdvanced) getConfigManager(is)
                                .getSetting(Settings.SUPER_WIRELESS_TOOL_ADVANCED);
                        if (mod == SuperWirelessToolAdvanced.Queueing) {
                            int j = 0;
                            for (DimensionalCoord sdcl : dcl) {
                                if (sdc.isEqual(sdcl)) {
                                    if (wc.isHub()) {
                                        if (j > wc.getFreeSlots()) {
                                            p.addChatMessage(
                                                    new ChatComponentTranslation(
                                                            "item.appliedenergistics2.ToolSuperWirelessKit.bound.advanced.filled")
                                                                    .setChatStyle(
                                                                            new ChatStyle()
                                                                                    .setColor(EnumChatFormatting.RED)));
                                            return false;
                                        }
                                    } else {
                                        p.addChatMessage(
                                                new ChatComponentTranslation(
                                                        "item.appliedenergistics2.ToolSuperWirelessKit.bound.advanced.filled")
                                                                .setChatStyle(
                                                                        new ChatStyle()
                                                                                .setColor(EnumChatFormatting.RED)));
                                        return false;
                                    }
                                }
                            }
                            if (Platform.keyBindLCtrl.isKeyDown(p) && wc.isHub()) {
                                int i = 0;
                                while (i < wc.getFreeSlots()) {
                                    dcl.add(new DimensionalCoord(te));
                                    i++;
                                }
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queueing.hub",
                                                i));
                            } else {
                                dcl.add(new DimensionalCoord(te));
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queued",
                                                x,
                                                y,
                                                z));
                            }
                            DimensionalCoord.writeListToNBT(is.getTagCompound().getCompoundTag("advanced"), dcl);
                            return true;
                        } else {
                            if (dcl.isEmpty()) {
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.noconnectors"));
                                return false;
                            }
                            DimensionalCoord dc = dcl.get(0);
                            if (wc.getLocation().getDimension() != dc.getDimension()) {
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.dimension"));
                                return false;
                            }
                            if (Platform.keyBindLCtrl.isKeyDown(p) && wc.isHub()) {
                                int i = 0;
                                while (wc.getFreeSlots() != 0 && !dcl.isEmpty()) {
                                    if (performConnection(wc, dcl.get(0), p)) {
                                        dcl.remove(0);
                                        i++;
                                    } else {
                                        break;
                                    }
                                }
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.binding.hub",
                                                i));
                            } else if (performConnection(wc, dc, p)) {
                                dcl.remove(0);
                            }
                            NBTTagCompound tag = new NBTTagCompound();
                            DimensionalCoord.writeListToNBT(tag, dcl);
                            is.getTagCompound().setTag("advanced", tag);
                        }
                    }
                }
                case Super -> {
                    IGridHost igh = (IGridHost) te;
                    if (!is.getTagCompound().hasKey("super")) {
                        NBTTagCompound newTag = new NBTTagCompound();
                        newTag.setTag("pins", new NBTTagList());
                        newTag.setTag("names", new NBTTagList());
                        newTag.setTag("pos", new NBTTagCompound());
                        is.getTagCompound().setTag("super", newTag);
                    }
                    NBTTagCompound tag = is.getTagCompound().getCompoundTag("super").getCompoundTag("pos");
                    List<DimensionalCoord> dcl = DimensionalCoord.readAsListFromNBT(tag);
                    for (int i = 0; i < dcl.size(); i++) {
                        DimensionalCoord dc = dcl.get(i);
                        TileEntity TempTe = w.getTileEntity(dc.x, dc.y, dc.z);
                        if (TempTe instanceof IGridHost igh1) {
                            if (igh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                                    == igh1.getGridNode(ForgeDirection.UNKNOWN).getGrid()) {
                                p.addChatMessage(
                                        new ChatComponentTranslation(
                                                "item.appliedenergistics2.ToolSuperWirelessKit.bound.super.failed",
                                                i).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                                return false;
                            }
                        }
                    }
                    p.addChatMessage(
                            new ChatComponentTranslation(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.bound.super",
                                    x,
                                    y,
                                    z,
                                    dcl.size() + 1).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                    dcl.add(new DimensionalCoord(te));
                    DimensionalCoord.writeListToNBT(tag, dcl);
                }
            }
        }
        return true;
    }

    public static IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.SUPER_WIRELESS_TOOL, SuperWirelessTool.Simple);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY, SuperWirelessToolGroupBy.Single);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_ADVANCED, SuperWirelessToolAdvanced.Queueing);

        out.readFromNBT((NBTTagCompound) Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    protected void addCheckedInformation(ItemStack is, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        SuperWirelessTool mode = (SuperWirelessTool) getConfigManager(is).getSetting(Settings.SUPER_WIRELESS_TOOL);
        lines.add(
                StatCollector.translateToLocal("item.appliedenergistics2.ToolSuperWirelessKit.mode") + " "
                        + EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal(
                                "item.appliedenergistics2.ToolSuperWirelessKit.mode."
                                        + mode.toString().toLowerCase(Locale.US)));
        lines.add(StatCollector.translateToLocal("item.appliedenergistics2.ToolSuperWirelessKit.clear"));
        switch (mode) {
            case Simple -> {
                if (is.getTagCompound().getCompoundTag("simple").hasNoTags()) {
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.simple.empty"));
                } else {
                    DimensionalCoord dc = DimensionalCoord.readFromNBT(is.getTagCompound().getCompoundTag("simple"));
                    lines.add(
                            StatCollector.translateToLocalFormatted(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.bound",
                                    dc.x,
                                    dc.y,
                                    dc.z));
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.simple.bound"));
                }
            }
            case Advanced -> {
                List<DimensionalCoord> dcl = DimensionalCoord
                        .readAsListFromNBT(is.getTagCompound().getCompoundTag("advanced"));
                lines.add(
                        StatCollector
                                .translateToLocal("item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.extra"));
                if (!dcl.isEmpty()) lines.add(
                        StatCollector.translateToLocalFormatted(
                                "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.next",
                                dcl.get(0).x,
                                dcl.get(0).y,
                                dcl.get(0).z));
                if (getConfigManager(is).getSetting(Settings.SUPER_WIRELESS_TOOL_ADVANCED)
                        == SuperWirelessToolAdvanced.Queueing) {
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queueing"));
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queueing.hubqols"));
                    if (dcl.isEmpty()) {
                        lines.add(
                                StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queueing.empty"));
                    } else {
                        lines.add(
                                StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.queueing.notempty"));
                        for (DimensionalCoord dc : dcl) {
                            lines.add(dc.x + "," + dc.y + "," + dc.z);
                        }
                    }
                } else {
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.binding"));
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.binding.hubqols"));
                    if (dcl.isEmpty()) {
                        lines.add(
                                StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.binding.empty"));
                    } else {
                        lines.add(
                                StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode.advanced.binding.notempty"));
                        for (DimensionalCoord dc : dcl) {
                            lines.add(dc.x + "," + dc.y + "," + dc.z);
                        }
                    }
                }
            }
            case Super -> {
                NBTTagCompound stash = is.getTagCompound().getCompoundTag("super");
                List<DimensionalCoord> dcl = DimensionalCoord.readAsListFromNBT((NBTTagCompound) stash.getTag("pos"));
                if (dcl.isEmpty()) {
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.super.networklistempty"));
                } else {
                    NBTTagList tagList = stash.getTagList("names", 10);
                    lines.add(
                            StatCollector.translateToLocal(
                                    "item.appliedenergistics2.ToolSuperWirelessKit.mode.super.networklist"));
                    for (int i = 0; i < dcl.size(); i++) {
                        DimensionalCoord dc = dcl.get(i);
                        String customName = "";
                        for (int j = 0; j < tagList.tagCount(); j++) {
                            NBTTagCompound tag = tagList.getCompoundTagAt(i);
                            if (tag.getInteger("network") == i && tag.hasKey("networkName")) {
                                customName = tag.getString("networkName");
                                break;
                            }
                        }
                        lines.add(
                                StatCollector.translateToLocalFormatted(
                                        "item.appliedenergistics2.ToolSuperWirelessKit.mode.super.network",
                                        customName.isEmpty() ? String.valueOf(i) : customName,
                                        dc.x,
                                        dc.y,
                                        dc.z));
                    }
                }
            }
        }
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack is, final World world, final int x, final int y, final int z) {
        return new SuperWirelessKitObject(is, world);
    }
}
