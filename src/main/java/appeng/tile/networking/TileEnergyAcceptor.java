/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.tile.networking;


import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.util.AECableType;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;


public class TileEnergyAcceptor extends AENetworkPowerTile
{

	private static final AppEngInternalInventory INTERNAL_INVENTORY = new AppEngInternalInventory( null, 0 );
	private final int[] sides = {};

	public TileEnergyAcceptor()
	{
		this.getProxy().setIdlePowerUsage( 0.0 );
		this.setInternalMaxPower( 8000 );
		this.setInternalPublicPowerStorage(true);
		this.setInternalPowerFlow(AccessRestriction.READ_WRITE);
	}

	@Override
	public void readFromNBT_AENetwork( final NBTTagCompound data )
	{
		this.setInternalCurrentPower( data.getDouble( "internalCurrentPower" ) );
		/**
		 * Does nothing here since the NBT tag in the parent is not needed anymore
		 */
	}

	@Override
	public void writeToNBT_AENetwork( final NBTTagCompound data )
	{
		data.setDouble( "internalCurrentPower", this.getInternalCurrentPower() );
		/**
		 * Does nothing here since the NBT tag in the parent is not needed anymore
		 */
	}

	@Override
	public AECableType getCableConnectionType( final ForgeDirection dir )
	{
		return AECableType.COVERED;
	}

	@Override
	protected double getFunnelPowerDemand( final double maxRequired )
	{
		try
		{
			final IEnergyGrid grid = this.getProxy().getEnergy();
			return grid.getEnergyDemand( maxRequired );
		}
		catch( final GridAccessException e )
		{
			return this.getInternalMaxPower();
		}
	}

	@Override
	protected double funnelPowerIntoStorage( final double power, final Actionable mode )
	{
		try
		{
			final IEnergyGrid grid = this.getProxy().getEnergy();
			final double leftOver = grid.injectPower( power, mode );
			if( mode == Actionable.SIMULATE )
			{
				return leftOver;
			}
			return 0.0;
		}
		catch( final GridAccessException e )
		{
			return super.funnelPowerIntoStorage( power, mode );
		}
	}
	@Override
	protected double extractAEPower( double amt, final Actionable mode )
	{
		double res = super.extractAEPower(amt, mode);
		try
		{
			if (getInternalCurrentPower() < getInternalMaxPower())
				this.getProxy().getGrid().postEvent(new MENetworkPowerStorage(this, MENetworkPowerStorage.PowerEventType.REQUEST_POWER));
		}
		catch( final GridAccessException ignored )
		{

		}
		return res;
	}

	@Override
	public IInventory getInternalInventory()
	{
		return INTERNAL_INVENTORY;
	}

	@Override
	public void onChangeInventory( final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added )
	{

	}

	@Override
	public int[] getAccessibleSlotsBySide( final ForgeDirection side )
	{
		return this.sides;
	}
}
