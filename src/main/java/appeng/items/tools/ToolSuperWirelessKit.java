package appeng.items.tools;

import static appeng.api.config.AdvancedWirelessToolMode.Queueing;
import static appeng.api.config.AdvancedWirelessToolMode.QueueingLine;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.Settings;
import appeng.api.config.SuperWirelessToolGroupBy;
import appeng.api.config.WirelessToolType;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGridHost;
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
        if (!is.hasTagCompound()) WireLessToolHelper.newNBT(is, this.getIdentity());
    }

    public WirelessToolType getIdentity() {
        return WirelessToolType.Super;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World w, EntityPlayer p) {
        if (Platform.isClient()) {
            return is;
        }

        final IConfigManager cm = getConfigManager(is);
        if (Platform.keyBindTab.isKeyDown(p)) {
            WireLessToolHelper.nextToolMode(p, cm);
            return is;
        }

        final WirelessToolType mode = this.getIdentity() == WirelessToolType.Super ? WireLessToolHelper.getMode(is)
                : this.getIdentity();
        if (p.isSneaking() && (Platform.keyBindLCtrl.isKeyDown(p) || this.getIdentity() == WirelessToolType.Simple)) {
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

        final WirelessToolType mode = (WirelessToolType) getConfigManager(is).getSetting(Settings.WIRELESS_TOOL_TYPE);
        final TileEntity te = w.getTileEntity(x, y, z);

        if (mode == WirelessToolType.Super && te instanceof IGridHost)
            return WireLessToolHelper.bindSuper(te, is, w, p);

        if (!(te instanceof TileWirelessBase target)) return false;

        return switch (mode) {
            case Simple -> WireLessToolHelper.bindSimple(target, is, w, p);
            case Advanced -> WireLessToolHelper.bindAdvanced(target, is, w, p);
            default -> false;
        };
    }

    public static IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = ItemStackNBT.get(target);
            manager.writeToNBT(data);
        });

        final ToolSuperWirelessKit tool = (ToolSuperWirelessKit) target.getItem();

        out.registerSetting(Settings.WIRELESS_TOOL_TYPE, tool.getIdentity());
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_GROUP_BY, SuperWirelessToolGroupBy.Single);
        out.registerSetting(Settings.SUPER_WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);
        out.registerSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE, Queueing);

        out.readFromNBT(ItemStackNBT.get(target));
        return out;
    }

    @Override
    protected void addCheckedInformation(ItemStack is, EntityPlayer player, final List<String> lines,
            boolean displayMoreInfo) {
        final IConfigManager cm = getConfigManager(is);
        final WirelessToolType currentMode = cm.getSetting(WirelessToolType.class);

        if (this.getIdentity() == WirelessToolType.Super) {
            lines.add(WirelessMessages.Mode.getLocal(currentMode.getLocal()));
            lines.add(WirelessMessages.ModeToggle.getLocal());
            lines.add(WirelessMessages.SuperClear.getLocal());
        }

        final NBTTagCompound tag = ItemStackNBT.get(is);

        switch (currentMode) {
            case Simple -> {
                if (tag.getCompoundTag(WireLessToolHelper.NbtSimple).hasNoTags()) {
                    lines.add(WirelessMessages.SimpleEmpty.getLocal());
                } else {
                    final DimensionalCoord dc = DimensionalCoord
                            .readFromNBT(tag.getCompoundTag(WireLessToolHelper.NbtSimple));
                    lines.add(WirelessMessages.SimpleBounded.getLocal(dc.getGuiTextShortNoDim()));
                    lines.add(WirelessMessages.SimpleBound.getLocal());
                }
            }
            case Advanced -> {
                final AdvancedWirelessToolMode mode = cm.getSetting(AdvancedWirelessToolMode.class);
                lines.add(WirelessMessages.AdvancedActivated.getLocal(mode.getLocal()));

                switch (mode) {
                    case Queueing, Binding -> {
                        final List<DimensionalCoord> dcl = DimensionalCoord
                                .readAsListFromNBT(tag.getCompoundTag(WireLessToolHelper.NbtAdvanced));
                        if (dcl.isEmpty()) {
                            if (mode == Queueing) lines.add(WirelessMessages.AdvancedQueueEmpty.getLocal());
                            else lines.add(WirelessMessages.AdvancedBindingEmpty.getLocal());
                        } else {
                            if (GuiScreen.isShiftKeyDown()) {
                                if (mode == Queueing) lines.add(WirelessMessages.AdvancedQueueNotEmpty.getLocal());
                                else lines.add(WirelessMessages.AdvancedBindingNotEmpty.getLocal());
                                dcl.forEach(dc -> lines.add(dc.getGuiTextShort()));
                                return;
                            } else lines.add(WirelessMessages.AdvancedNext.getLocal(dcl.get(0).getGuiTextShortNoDim()));
                            lines.add(WirelessMessages.Clear.getLocal());
                        }

                        if (mode == Queueing) lines.add(WirelessMessages.AdvancedQueueingHubQol.getLocal());
                        else lines.add(WirelessMessages.AdvancedBindingHubQol.getLocal());
                    }

                    case QueueingLine, BindingLine -> {
                        final NBTTagCompound line;
                        if (mode == QueueingLine) line = tag.getCompoundTag(WireLessToolHelper.NbtAdvancedLineQueue);
                        else line = tag.getCompoundTag(WireLessToolHelper.NbtAdvancedLineBinding);

                        if (!line.hasKey(WireLessToolHelper.NbtAdvanced1StPoint))
                            lines.add(WirelessMessages.AdvancedLineEmpty1st.getLocal());
                        else {
                            lines.add(
                                    WirelessMessages.AdvancedLine1st.getLocal(
                                            DimensionalCoord
                                                    .readFromNBT(
                                                            line.getCompoundTag(WireLessToolHelper.NbtAdvanced1StPoint))
                                                    .getGuiTextShort()));

                            if (!line.hasKey(WireLessToolHelper.NbtAdvanced2ndPoint))
                                lines.add(WirelessMessages.AdvancedLineEmpty2nd.getLocal());
                            else {
                                lines.add(
                                        WirelessMessages.AdvancedLine2nd.getLocal(
                                                DimensionalCoord.readFromNBT(
                                                        line.getCompoundTag(WireLessToolHelper.NbtAdvanced2ndPoint))
                                                        .getGuiTextShort()));
                                lines.add(WirelessMessages.AdvancedQueueingLineNotEmpty.getLocal());
                            }
                            lines.add(WirelessMessages.Clear.getLocal());
                        }
                    }
                }

                lines.add(WirelessMessages.AdvancedHowToggle.getLocal(EnumChatFormatting.ITALIC));
            }
            case Super -> {
                final NBTTagCompound stash = tag.getCompoundTag(WireLessToolHelper.NbtSuper);
                final List<DimensionalCoord> dcl = DimensionalCoord
                        .readAsListFromNBT(stash.getCompoundTag(WireLessToolHelper.NbtSuperPos));
                if (dcl.isEmpty()) lines.add(WirelessMessages.SuperNetworkListEmpty.getLocal());
                else {
                    lines.add(WirelessMessages.SuperNetworkList.getLocal());
                    final NBTTagList tagNames = stash.getTagList(WireLessToolHelper.NbtSuperNames, NBT.TAG_COMPOUND);
                    for (int i = 0; i < tagNames.tagCount(); i++) {
                        final NBTTagCompound tagName = tagNames.getCompoundTagAt(i);
                        final DimensionalCoord netCoord = DimensionalCoord.readFromNBT(tagName);
                        String customName = "";

                        if (tagName.hasKey("networkName")) {
                            for (final DimensionalCoord dc : dcl) {
                                if (dc.equals(netCoord)) {
                                    customName = tagName.getString("networkName");
                                    break;
                                }
                            }
                        }

                        lines.add(
                                WirelessMessages.SuperNetwork
                                        .getLocal(customName + " ", netCoord.getGuiTextShortNoDim()));
                    }
                    lines.add(WirelessMessages.Clear.getLocal());
                }
            }
        }
    }

    @Override
    public IGuiItemObject getGuiObject(ItemStack is, World world, EntityPlayer player, int x, int y, int z) {
        return new SuperWirelessKitObject(is, world);
    }
}
