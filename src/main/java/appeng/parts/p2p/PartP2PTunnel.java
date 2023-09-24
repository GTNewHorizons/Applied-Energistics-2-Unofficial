/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.p2p;

import java.util.ArrayList;
import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.parts.PartItemStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.me.GridAccessException;
import appeng.me.cache.helpers.TunnelCollection;
import appeng.parts.PartBasicState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartP2PTunnel<T extends PartP2PTunnel> extends PartBasicState {

    private final TunnelCollection type = new TunnelCollection<T>(null, this.getClass());
    private boolean output;
    private long freq;

    public PartP2PTunnel(final ItemStack is) {
        super(is);
    }

    public TunnelCollection<T> getCollection(final Collection<PartP2PTunnel> collection,
            final Class<? extends PartP2PTunnel> c) {
        if (this.type.matches(c)) {
            this.type.setSource(collection);
            return this.type;
        }

        return null;
    }

    public T getInput() {
        if (this.getFrequency() == 0) {
            return null;
        }

        try {
            final PartP2PTunnel tunnel = this.getProxy().getP2P().getInput(this.getFrequency());
            if (this.getClass().isInstance(tunnel)) {
                return (T) tunnel;
            }
        } catch (final GridAccessException e) {
            // :P
        }
        return null;
    }

    public TunnelCollection<T> getOutputs() throws GridAccessException {
        if (this.getProxy().isActive()) {
            return (TunnelCollection<T>) this.getProxy().getP2P().getOutputs(this.getFrequency(), this.getClass());
        }
        return new TunnelCollection<>(new ArrayList<>(), this.getClass());
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(5, 5, 12, 11, 11, 13);
        bch.addBox(3, 3, 13, 13, 13, 14);
        bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(this.getTypeTexture());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderInventoryBox(renderer);

        rh.setTexture(
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.BlockP2PTunnel2.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.PartTunnelSides.getIcon());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderInventoryBox(renderer);
    }

    /**
     * @return If enabled it returns the icon of an AE quartz block, else vanilla quartz block icon
     */
    public IIcon getTypeTexture() {
        final Optional<Block> maybeBlock = AEApi.instance().definitions().blocks().quartz().maybeBlock();
        if (maybeBlock.isPresent()) {
            return maybeBlock.get().getIcon(0, 0);
        } else {
            return Blocks.quartz_block.getIcon(0, 0);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(this.getTypeTexture());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.BlockP2PTunnel2.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartTunnelSides.getIcon(),
                CableBusTextures.PartTunnelSides.getIcon());

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(3, 3, 13, 13, 13, 14);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(CableBusTextures.BlockP2PTunnel3.getIcon());

        rh.setBounds(6, 5, 12, 10, 11, 13);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(5, 6, 12, 11, 10, 13);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public ItemStack getItemStack(final PartItemStack type) {
        if (type == PartItemStack.World || type == PartItemStack.Network
                || type == PartItemStack.Wrench
                || type == PartItemStack.Pick) {
            return super.getItemStack(type);
        }

        final Optional<ItemStack> maybeMEStack = AEApi.instance().definitions().parts().p2PTunnelME().maybeStack(1);
        if (maybeMEStack.isPresent()) {
            return maybeMEStack.get();
        }

        return super.getItemStack(type);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.setOutput(data.getBoolean("output"));
        this.setFrequency(data.getLong("freq"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("output", this.isOutput());
        data.setLong("freq", this.getFrequency());
    }

    @Override
    public int cableConnectionRenderTo() {
        return 1;
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public abstract boolean onPartActivate(EntityPlayer player, Vec3 pos);

    protected void printConnectionInfo(EntityPlayer player) {
        if (isOutput()) {
            PartP2PTunnel input = getInput();
            if (input == null) player.addChatMessage(PlayerMessages.TunnelNotConnected.get());
            else {
                TileEntity t = input.getTile();
                player.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.TunnelInputIsAt.getName(),
                                t.xCoord,
                                t.yCoord,
                                t.zCoord));
            }
        } else {
            try {
                TunnelCollection<T> oo = getOutputs();
                if (oo.isEmpty()) player.addChatMessage(PlayerMessages.TunnelHasNoOutputs.get());
                else {
                    player.addChatMessage(PlayerMessages.TunnelOutputsAreAt.get());
                    for (PartP2PTunnel t : oo) {
                        TileEntity te = t.getTile();
                        if (te != null) player.addChatMessage(
                                new ChatComponentText("(" + te.xCoord + ", " + te.yCoord + ", " + te.zCoord + ")"));
                    }
                }
            } catch (GridAccessException ignored) {
                player.addChatMessage(PlayerMessages.TunnelNotConnected.get());
            }
        }
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final Vec3 pos) {
        final ItemStack is = player.inventory.getCurrentItem();
        if (is != null && is.getItem() instanceof IMemoryCard mc) {
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) return false;

            final NBTTagCompound data = new NBTTagCompound();

            long newFreq = this.getFrequency();
            final boolean wasOutput = this.isOutput();
            this.setOutput(false);

            if (wasOutput || this.getFrequency() == 0) {
                newFreq = System.currentTimeMillis();
            }

            try {
                this.getProxy().getP2P().updateFreq(this, newFreq);
            } catch (final GridAccessException e) {
                // :P
            }

            this.onTunnelConfigChange();

            final ItemStack p2pItem = this.getItemStack(PartItemStack.Wrench);
            final String type = p2pItem.getUnlocalizedName();

            p2pItem.writeToNBT(data);
            data.setLong("freq", this.getFrequency());
            if (hasCustomName()) data.setString("custom_name", getCustomName());

            mc.setMemoryCardContents(is, type + ".name", data);
            mc.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            return true;
        }
        return false;
    }

    public void onTunnelConfigChange() {}

    public void onTunnelNetworkChange() {}

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getBreakingTexture() {
        return CableBusTextures.BlockP2PTunnel2.getIcon();
    }

    protected void queueTunnelDrain(final PowerUnits unit, final double f) {
        final double ae_to_tax = unit.convertTo(PowerUnits.AE, f * AEConfig.TUNNEL_POWER_LOSS);

        try {
            this.getProxy().getEnergy().extractAEPower(ae_to_tax, Actionable.MODULATE, PowerMultiplier.ONE);
        } catch (final GridAccessException e) {
            // :P
        }
    }

    public long getFrequency() {
        return this.freq;
    }

    public void setFrequency(final long freq) {
        this.freq = freq;
    }

    public boolean isOutput() {
        return this.output;
    }

    void setOutput(final boolean output) {
        this.output = output;
    }

    @Override
    public void setCustomName(String name) {
        T i = getInput();
        if (i != null) {
            i.setCustomNameInternal(name);
            try {
                for (T o : getOutputs()) o.setCustomNameInternal(name);
            } catch (GridAccessException ignored) {}
        } else // let unlinked tunnel have a name
            super.setCustomName(name);
    }

    void setCustomNameInternal(String name) {
        super.setCustomName(name);
    }
}
