package appeng.items.tools;

import static appeng.api.config.AdvancedWirelessToolMode.Queueing;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessToolGroupBy;
import appeng.api.config.WirelessToolType;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.features.AEFeature;
import appeng.core.localization.WirelessMessages;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WireLessToolHelper;
import appeng.items.AEBaseItem;
import appeng.items.contents.SuperWirelessKitObject;
import appeng.tile.networking.TileWirelessBase;
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
        if (Platform.isClient()) {
            return is;
        }

        IConfigManager cm = getConfigManager(is);
        if (Platform.keyBindTab.isKeyDown(p)) {
            WireLessToolHelper.nextToolMode(p, cm);
            return is;
        }

        WirelessToolType mode = (WirelessToolType) cm.getSetting(Settings.WIRELESS_TOOL_TYPE);
        if (p.isSneaking() && Platform.keyBindLCtrl.isKeyDown(p)) {
            WireLessToolHelper.clearNBT(is, mode, p);
            return is;
        }

        if (p.isSneaking() && mode == WirelessToolType.Advanced) {
            WireLessToolHelper.nextConnectMode(cm, p);
            return is;
        }

        if (mode == WirelessToolType.Super) {
            Platform.openGUI(p, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_SUPER_WIRELESS_KIT);
            return is;
        }

        return is;
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer p, World w, int x, int y, int z, int side, float xOff,
            float yOff, float zOff) {
        if (Platform.isClient()) return true;

        WirelessToolType mode = (WirelessToolType) getConfigManager(is).getSetting(Settings.WIRELESS_TOOL_TYPE);
        TileEntity te = w.getTileEntity(x, y, z);

        if (!(te instanceof TileWirelessBase target)) {
            return false;
        }

        if (!WireLessToolHelper.hasBuildPermissions(target, p)) {
            p.addChatMessage(
                    new ChatComponentTranslation("item.appliedenergistics2.ToolSuperWirelessKit.security.player"));
            return false;
        }

        return switch (mode) {
            case Simple -> WireLessToolHelper.bindSimple(target, is, w, p);
            case Advanced -> WireLessToolHelper.bindAdvanced(target, is, w, p);
            case Super -> WireLessToolHelper.bindSuper(target, is, w, p);
        };
    }

    public static IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.WIRELESS_TOOL_TYPE, WirelessToolType.Simple);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY, SuperWirelessToolGroupBy.Single);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);
        out.registerSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE, Queueing);

        out.readFromNBT((NBTTagCompound) Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    protected void addCheckedInformation(ItemStack is, EntityPlayer player, final List<String> lines,
            boolean displayMoreInfo) {

        IConfigManager cm = getConfigManager(is);
        WirelessToolType currentMode = (WirelessToolType) cm.getSetting(Settings.WIRELESS_TOOL_TYPE);
        lines.add(WirelessMessages.mode.getLocal() + " " + currentMode.getLocal(EnumChatFormatting.YELLOW));

        lines.add(WirelessMessages.clear.getLocal());

        switch (currentMode) {
            case Simple -> {
                if (is.getTagCompound().getCompoundTag("simple").hasNoTags()) {
                    lines.add(WirelessMessages.mode_simple_empty.getLocal());
                } else {
                    DimensionalCoord dc = DimensionalCoord.readFromNBT(is.getTagCompound().getCompoundTag("simple"));

                    lines.add(WirelessMessages.bound.getLocal(dc.x, dc.y, dc.z));
                    lines.add(WirelessMessages.mode_simple_bound.getLocal());
                }
            }
            case Advanced -> {
                List<DimensionalCoord> dcl = DimensionalCoord
                        .readAsListFromNBT(is.getTagCompound().getCompoundTag("advanced"));
                String modeName = cm.getSetting(AdvancedWirelessToolMode.class).getMode();
                lines.add(WirelessMessages.valueOf("mode_advanced_" + modeName).getLocal(EnumChatFormatting.GREEN));

                if (dcl.isEmpty()) {
                    lines.add(
                            WirelessMessages.valueOf("mode_advanced_" + modeName + "_empty")
                                    .getLocal(EnumChatFormatting.BLUE));
                } else {
                    if (GuiScreen.isShiftKeyDown()) {
                        lines.add(
                                WirelessMessages.valueOf("mode_advanced_" + modeName + "_notempty")
                                        .getLocal(EnumChatFormatting.BLUE));
                        dcl.forEach(dc -> lines.add(String.format("%d,%d,%d", dc.x, dc.y, dc.z)));
                        return;
                    } else lines.add(
                            WirelessMessages.mode_advanced_next
                                    .getLocal(EnumChatFormatting.BLUE, dcl.get(0).x, dcl.get(0).y, dcl.get(0).z));
                }
                lines.add(WirelessMessages.mode_advanced_howToggle.getLocal(EnumChatFormatting.ITALIC));
                // lines.add(WirelessToolMessages.valueOf("mode_advanced_" + modeName + "_hubqols").getLocal());
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
