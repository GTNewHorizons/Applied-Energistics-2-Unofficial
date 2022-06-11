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

package appeng.client.gui.implementations;


import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.Reflected;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;


public class GuiCraftAmount extends AEBaseGui
{
	private GuiTextField amountToCraft;
	private GuiTabButton originalGuiBtn;

	private GuiButton next;

	private GuiButton plus1;
	private GuiButton plus10;
	private GuiButton plus100;
	private GuiButton plus1000;
	private GuiButton minus1;
	private GuiButton minus10;
	private GuiButton minus100;
	private GuiButton minus1000;

	private GuiBridge originalGui;

	@Reflected
	public GuiCraftAmount( final InventoryPlayer inventoryPlayer, final ITerminalHost te )
	{
		super( new ContainerCraftAmount( inventoryPlayer, te ) );
	}

	@Override
    @SuppressWarnings( "unchecked" )
	public void initGui()
	{
		super.initGui();

		final int a = AEConfig.instance.craftItemsByStackAmounts( 0 );
		final int b = AEConfig.instance.craftItemsByStackAmounts( 1 );
		final int c = AEConfig.instance.craftItemsByStackAmounts( 2 );
		final int d = AEConfig.instance.craftItemsByStackAmounts( 3 );

		this.buttonList.add( this.plus1 = new GuiButton( 0, this.guiLeft + 20, this.guiTop + 26, 22, 20, "+" + a ) );
		this.buttonList.add( this.plus10 = new GuiButton( 0, this.guiLeft + 48, this.guiTop + 26, 28, 20, "+" + b ) );
		this.buttonList.add( this.plus100 = new GuiButton( 0, this.guiLeft + 82, this.guiTop + 26, 32, 20, "+" + c ) );
		this.buttonList.add( this.plus1000 = new GuiButton( 0, this.guiLeft + 120, this.guiTop + 26, 38, 20, "+" + d ) );

		this.buttonList.add( this.minus1 = new GuiButton( 0, this.guiLeft + 20, this.guiTop + 75, 22, 20, "-" + a ) );
		this.buttonList.add( this.minus10 = new GuiButton( 0, this.guiLeft + 48, this.guiTop + 75, 28, 20, "-" + b ) );
		this.buttonList.add( this.minus100 = new GuiButton( 0, this.guiLeft + 82, this.guiTop + 75, 32, 20, "-" + c ) );
		this.buttonList.add( this.minus1000 = new GuiButton( 0, this.guiLeft + 120, this.guiTop + 75, 38, 20, "-" + d ) );

		this.buttonList.add( this.next = new GuiButton( 0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal() ) );

		ItemStack myIcon = null;
		final Object target = ( (AEBaseContainer) this.inventorySlots ).getTarget();
		final IDefinitions definitions = AEApi.instance().definitions();
		final IParts parts = definitions.parts();

		if( target instanceof WirelessTerminalGuiObject )
		{
			for( final ItemStack wirelessTerminalStack : definitions.items().wirelessTerminal().maybeStack( 1 ).asSet() )
			{
				myIcon = wirelessTerminalStack;
			}

			this.originalGui = GuiBridge.GUI_WIRELESS_TERM;
		}

		if( target instanceof PartTerminal )
		{
			for( final ItemStack stack : parts.terminal().maybeStack( 1 ).asSet() )
			{
				myIcon = stack;
			}
			this.originalGui = GuiBridge.GUI_ME;
		}

		if( target instanceof PartCraftingTerminal )
		{
			for( final ItemStack stack : parts.craftingTerminal().maybeStack( 1 ).asSet() )
			{
				myIcon = stack;
			}
			this.originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
		}

		if( target instanceof PartPatternTerminal )
		{
			for( final ItemStack stack : parts.patternTerminal().maybeStack( 1 ).asSet() )
			{
				myIcon = stack;
			}
			this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
		}

		if( target instanceof PartPatternTerminalEx)
		{
			for( final ItemStack stack : parts.patternTerminalEx().maybeStack( 1 ).asSet() )
			{
				myIcon = stack;
			}
			this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
		}

		if( this.originalGui != null && myIcon != null )
		{
			this.buttonList.add( this.originalGuiBtn = new GuiTabButton( this.guiLeft + 154, this.guiTop, myIcon, myIcon.getDisplayName(), itemRender ) );
		}

		this.amountToCraft = new GuiTextField( this.fontRendererObj, this.guiLeft + 62, this.guiTop + 57, 59, this.fontRendererObj.FONT_HEIGHT);
		this.amountToCraft.setEnableBackgroundDrawing( false );
		this.amountToCraft.setMaxStringLength( 16 );
		this.amountToCraft.setTextColor( 0xFFFFFF );
		this.amountToCraft.setVisible( true );
		this.amountToCraft.setFocused( true );
		this.amountToCraft.setText( "1" );
		this.amountToCraft.setSelectionPos(0);
	}

	@Override
	public void drawFG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.fontRendererObj.drawString( GuiText.SelectAmount.getLocal(), 8, 6, 4210752 );
	}

	@Override
	public void drawBG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.next.displayString = isShiftKeyDown() ? GuiText.Start.getLocal() : GuiText.Next.getLocal();

		this.bindTexture( "guis/craftAmt.png" );
		this.drawTexturedModalRect( offsetX, offsetY, 0, 0, this.xSize, this.ySize );

		try
		{
            String out = this.amountToCraft.getText();

            double resultD = Calculator.conversion( out );
            int resultI;

            if( resultD <= 0 || Double.isNaN( resultD ) ) {
                resultI = 0;
            }
            else {
                resultI = (int) ArithHelper.round(resultD, 0);
            }

			this.next.enabled = resultI > 0;
		}
		catch( final NumberFormatException e )
		{
			this.next.enabled = false;
		}

		this.amountToCraft.drawTextBox();
	}

	@Override
	protected void keyTyped( final char character, final int key )
	{
		if( !this.checkHotbarKeys( key ) )
		{
			if( key == 28 )
			{
				this.actionPerformed( this.next );
			}
            this.amountToCraft.textboxKeyTyped( character, key );
			super.keyTyped( character, key );
		}
	}

	@Override
	protected void actionPerformed( final GuiButton btn )
	{
		super.actionPerformed( btn );

		try
		{

			if( btn == this.originalGuiBtn )
			{
				NetworkHandler.instance.sendToServer( new PacketSwitchGuis( this.originalGui ) );
			}

			if( btn == this.next && btn.enabled )
			{
                double resultD = Calculator.conversion( this.amountToCraft.getText() );
                int resultI;

                if( resultD <= 0 || Double.isNaN( resultD ) ) {
                    resultI = 1;
                }
                else {
                    resultI = (int) ArithHelper.round(resultD, 0);
                }

				NetworkHandler.instance.sendToServer( new PacketCraftRequest( resultI, isShiftKeyDown() ) );
			}
		}
		catch( final NumberFormatException e )
		{
			// nope..
			this.amountToCraft.setText( "1" );
		}

		final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
		final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100 || btn == this.minus1000;

		if( isPlus || isMinus )
		{
			this.addQty( this.getQty( btn ) );
		}
	}

	private void addQty( final int i )
	{
		try
		{
			String out = this.amountToCraft.getText();

            double resultD = Calculator.conversion( out );
            int resultI;

            if( resultD <= 0 || Double.isNaN( resultD ) ) {
                resultI = 0;
            }
            else {
                resultI = (int) ArithHelper.round(resultD, 0);
            }

            if (resultI == 1 && i > 0) {
                resultI = 0;
            }

            resultI += i;
            if( resultI < 1 ) {
                resultI = 1;
            }

            out = Integer.toString( resultI );

			this.amountToCraft.setText( out );
		}
		catch( final NumberFormatException e )
		{
			// :P
		}
	}

	protected String getBackground()
	{
		return "guis/craftAmt.png";
	}
}
