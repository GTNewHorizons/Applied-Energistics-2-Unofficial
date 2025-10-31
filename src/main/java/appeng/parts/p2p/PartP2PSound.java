/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.p2p;

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import appeng.api.implementations.tiles.ISoundP2PHandler;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.hooks.SoundEventHandler;
import appeng.me.GridAccessException;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartP2PSound extends PartP2PTunnelNormal<PartP2PSound> implements IGridTickable {

    private boolean alive = false;
    private ISoundP2PHandler customHandler;

    public PartP2PSound(final ItemStack is) {
        super(is);
    }

    @Override
    public void chanRender(final MENetworkChannelsChanged c) {
        this.onTunnelNetworkChange();
        super.chanRender(c);
    }

    @Override
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.onTunnelNetworkChange();
        super.powerRender(c);
    }

    @Override
    public void onNeighborChanged() {
        final TileEntity te = this.getTile();
        final World w = te.getWorldObj();
        final TileEntity neighbor = w.getTileEntity(
                te.xCoord + this.getSide().offsetX,
                te.yCoord + this.getSide().offsetY,
                te.zCoord + this.getSide().offsetZ);
        if (alive && !isOutput()) {
            SoundEventHandler.INSTANCE.activateP2P(this);
        }
        ISoundP2PHandler handler = Capabilities.getCapability(neighbor, ISoundP2PHandler.class);
        if (handler != null) {
            this.customHandler = handler;
            handler.onSoundP2PAttach(this);
        } else {
            this.customHandler = null;
        }
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        alive = true;
        onNeighborChanged();
    }

    @Override
    public void removeFromWorld() {
        if (customHandler != null) {
            customHandler.onSoundP2PDetach(this);
        }
        super.removeFromWorld();
        alive = false;
        SoundEventHandler.INSTANCE.deactivateP2P(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getTypeTexture() {
        return Blocks.noteblock.getBlockTextureFromSide(0);
    }

    @Override
    public void onTunnelConfigChange() {
        this.onTunnelNetworkChange();
    }

    @Override
    public void onTunnelNetworkChange() {
        this.onNeighborChanged();
        if (this.customHandler != null) {
            try {
                this.customHandler.onSoundP2POutputUpdate(this, this.getOutputs());
            } catch (GridAccessException e) {
                // could not access output list, skip the update notification
            }
        }
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.SoundTunnel.getMin(), TickRates.SoundTunnel.getMax(), false, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        this.onNeighborChanged(); // periodically, but rarely, check the neighbor
        return TickRateModulation.SLOWER;
    }

    public @Nullable ISoundP2PHandler getCustomHandler() {
        return this.customHandler;
    }

    /**
     * Invokes the given function for each active output linked to this P2P.
     */
    public void proxyCall(BiConsumer<DimensionalCoord, World> runAtOutput) {
        if (this.getProxy() == null || !this.getProxy().isActive() || this.isOutput()) {
            return;
        }
        if (this.customHandler != null && !this.customHandler.allowSoundProxying(this)) {
            return;
        }
        try {
            this.getOutputs().forEach(output -> {
                if (output.getTile() == null || output.getProxy() == null || !output.getProxy().isActive()) {
                    return;
                }
                final DimensionalCoord outputPos = output.getLocation();
                outputPos.add(output.getSide(), 1);
                runAtOutput.accept(outputPos, output.getTile().getWorldObj());
            });
        } catch (GridAccessException e) {
            // skip
        }
    }
}
