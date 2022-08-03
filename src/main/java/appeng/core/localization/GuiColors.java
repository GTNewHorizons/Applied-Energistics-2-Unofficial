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

package appeng.core.localization;

import appeng.core.AELog;
import net.minecraft.util.StatCollector;

public enum GuiColors
{	
	//ARGB Colors: Name and default value
	SearchboxFocused			(0x6E000000),
	SearchboxUnfocused			(0x00000000),
	ItemSlotOverlayUnpowered	(0x66111111),
	ItemSlotOverlayInvalid		(0x66ff6666),
	CraftConfirmMissingItem		(0x1AFF0000),
	CraftingCPUActive			(0x5A45A021),
	CraftingCPUScheduled		(0x5AFFF7AA),
	InterfaceTerminalMatch		(0x2A00FF00),
	
	//RGB Colors: Name and default value
	SearchboxFont			(0xFFFFFF),
	CraftingOverviewFont	(0x606060),
	;

	private final String root;
	private final int color;

	GuiColors()
	{
		this.root = "gui.color.appliedenergistics2";
		this.color = 0x000000;
	}

	GuiColors( final int hex )
	{
		this.root = "gui.color.appliedenergistics2";
		this.color = hex;
	}

	public int getColor()
	{
		String hex = StatCollector.translateToLocal( this.getUnlocalized() );
		long color = this.color;

		try
		{	
			hex = !hex.contains("0x") ? hex.replace(hex, "0x" + hex) : hex;
			color = Long.decode(hex);
		}

		catch ( final NumberFormatException e )
		{
			AELog.warn( "Couldn't format color correctly for: " + this.root);
		}

		return (int) color;
	}
	
	public String getUnlocalized()
	{
		return this.root + '.' + this.toString();
	}
}
