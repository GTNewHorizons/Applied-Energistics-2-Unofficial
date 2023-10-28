/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync.packets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.container.implementations.ContainerNetworkTool;
import appeng.container.implementations.ContainerOreFilter;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.implementations.ContainerPriority;
import appeng.container.implementations.ContainerQuartzKnife;
import appeng.container.implementations.ContainerRenamer;
import appeng.container.implementations.ContainerSecurity;
import appeng.container.implementations.ContainerStorageBus;
import appeng.container.interfaces.ICraftingCPUSelectorContainer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IMouseWheelItem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketValueConfig extends AppEngPacket {

    private final String Name;
    private final String Value;

    // automatic.
    public PacketValueConfig(final ByteBuf stream) throws IOException {
        final DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(stream.array(), stream.readerIndex(), stream.readableBytes()));
        this.Name = dis.readUTF();
        this.Value = dis.readUTF();
        // dis.close();
    }

    // api
    public PacketValueConfig(final String name, final String value) throws IOException {
        this.Name = name;
        this.Value = value;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);
        dos.writeUTF(name);
        dos.writeUTF(value);
        // dos.close();

        data.writeBytes(bos.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (this.Name.equals("Item") && player.getHeldItem() != null
                && player.getHeldItem().getItem() instanceof IMouseWheelItem) {
            final ItemStack is = player.getHeldItem();
            final IMouseWheelItem si = (IMouseWheelItem) is.getItem();
            si.onWheel(is, this.Value.equals("WheelUp"));
        } else if (this.Name.equals("CPUTable.Cpu.Set") && c instanceof ICraftingCPUSelectorContainer) {
            final ICraftingCPUSelectorContainer qk = (ICraftingCPUSelectorContainer) c;
            qk.selectCPU(Integer.parseInt(this.Value));
        } else if (this.Name.equals("Terminal.Start") && c instanceof ContainerCraftConfirm) {
            final ContainerCraftConfirm qk = (ContainerCraftConfirm) c;
            qk.startJob();
        } else if (this.Name.equals("TileCrafting.Cancel") && c instanceof ContainerCraftingCPU) {
            final ContainerCraftingCPU qk = (ContainerCraftingCPU) c;
            qk.cancelCrafting();
        } else if (this.Name.equals("QuartzKnife.Name") && c instanceof ContainerQuartzKnife) {
            final ContainerQuartzKnife qk = (ContainerQuartzKnife) c;
            qk.setName(this.Value);
        } else if (this.Name.equals("QuartzKnife.ReName") && c instanceof ContainerRenamer) {
            final ContainerRenamer qk = (ContainerRenamer) c;
            qk.setNewName(this.Value);
        } else if (this.Name.equals("TileSecurity.ToggleOption") && c instanceof ContainerSecurity sc) {
            sc.toggleSetting(this.Value, player);
        } else if (this.Name.equals("PriorityHost.Priority") && c instanceof ContainerPriority pc) {
            pc.setPriority(Integer.parseInt(this.Value), player);
        } else if (this.Name.equals("OreFilter") && c instanceof ContainerOreFilter fc) {
            fc.setFilter(this.Value);
        } else if (this.Name.equals("LevelEmitter.Value") && c instanceof ContainerLevelEmitter lvc) {
            lvc.setLevel(Long.parseLong(this.Value), player);
        } else if (this.Name.startsWith("PatternTerminal.") && c instanceof ContainerPatternTerm) {
            final ContainerPatternTerm cpt = (ContainerPatternTerm) c;
            switch (this.Name) {
                case "PatternTerminal.CraftMode" -> cpt.getPatternTerminal().setCraftingRecipe(this.Value.equals("1"));
                case "PatternTerminal.Encode" -> {
                    if (this.Value.equals("2")) cpt.encodeAndMoveToInventory(false);
                    else if (this.Value.equals("6")) cpt.encodeAndMoveToInventory(true);
                    else cpt.encode();
                }
                case "PatternTerminal.Clear" -> cpt.clear();
                case "PatternTerminal.Substitute" -> cpt.getPatternTerminal().setSubstitution(this.Value.equals("1"));
                case "PatternTerminal.BeSubstitute" -> cpt.getPatternTerminal()
                        .setCanBeSubstitution(this.Value.equals("1"));
                case "PatternTerminal.Double" -> cpt.doubleStacks(Value.equals("1"));
            }
        } else if (this.Name.startsWith("PatternTerminalEx.") && c instanceof ContainerPatternTermEx) {
            final ContainerPatternTermEx cpt = (ContainerPatternTermEx) c;
            switch (this.Name) {
                case "PatternTerminalEx.Encode" -> {
                    if (this.Value.equals("2")) cpt.encodeAndMoveToInventory(false);
                    else if (this.Value.equals("6")) cpt.encodeAndMoveToInventory(true);
                    else cpt.encode();
                }
                case "PatternTerminalEx.Clear" -> cpt.clear();
                case "PatternTerminalEx.Substitute" -> cpt.getPatternTerminal().setSubstitution(this.Value.equals("1"));
                case "PatternTerminalEx.BeSubstitute" -> cpt.getPatternTerminal()
                        .setCanBeSubstitution(this.Value.equals("1"));
                case "PatternTerminalEx.Invert" -> cpt.getPatternTerminal().setInverted(Value.equals("1"));
                case "PatternTerminalEx.Double" -> cpt.doubleStacks(Value.equals("1"));
                case "PatternTerminalEx.ActivePage" -> cpt.getPatternTerminal().setActivePage(Integer.parseInt(Value));
            }
        } else if (this.Name.startsWith("StorageBus.") && c instanceof ContainerStorageBus) {
            final ContainerStorageBus ccw = (ContainerStorageBus) c;
            if (this.Name.equals("StorageBus.Action")) {
                if (this.Value.equals("Partition")) {
                    ccw.partition();
                } else if (this.Value.equals("Clear")) {
                    ccw.clear();
                }
            }
        } else if (this.Name.startsWith("CellWorkbench.") && c instanceof ContainerCellWorkbench) {
            final ContainerCellWorkbench ccw = (ContainerCellWorkbench) c;
            if (this.Name.equals("CellWorkbench.Action")) {
                switch (this.Value) {
                    case "CopyMode" -> ccw.nextWorkBenchCopyMode();
                    case "Partition" -> ccw.partition();
                    case "Clear" -> ccw.clear();
                }
            } else if (this.Name.equals("CellWorkbench.Fuzzy")) {
                ccw.setFuzzy(FuzzyMode.valueOf(this.Value));
            }
        } else if (c instanceof ContainerNetworkTool) {
            if (this.Name.equals("NetworkTool") && this.Value.equals("Toggle")) {
                ((ContainerNetworkTool) c).toggleFacadeMode();
            }
        } else if (c instanceof IConfigurableObject) {
            final IConfigManager cm = ((IConfigurableObject) c).getConfigManager();

            for (final Settings e : cm.getSettings()) {
                if (e.name().equals(this.Name)) {
                    final Enum<?> def = cm.getSetting(e);

                    try {
                        cm.putSetting(e, Enum.valueOf(def.getClass(), this.Value));
                    } catch (final IllegalArgumentException err) {
                        // :P
                    }

                    break;
                }
            }
        }
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (this.Name.equals("CustomName") && c instanceof AEBaseContainer) {
            ((AEBaseContainer) c).setCustomName(this.Value);
        } else if (this.Name.startsWith("SyncDat.")) {
            ((AEBaseContainer) c).stringSync(Integer.parseInt(this.Name.substring(8)), this.Value);
        } else if (this.Name.equals("CraftingStatus") && this.Value.equals("Clear")) {
            final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
            if (gs instanceof GuiCraftingCPU) {
                ((GuiCraftingCPU) gs).clearItems();
            }
        } else if (c instanceof IConfigurableObject) {
            final IConfigManager cm = ((IConfigurableObject) c).getConfigManager();

            for (final Settings e : cm.getSettings()) {
                if (e.name().equals(this.Name)) {
                    final Enum<?> def = cm.getSetting(e);

                    try {
                        cm.putSetting(e, Enum.valueOf(def.getClass(), this.Value));
                    } catch (final IllegalArgumentException err) {
                        // :P
                    }

                    break;
                }
            }
        }
    }
}
