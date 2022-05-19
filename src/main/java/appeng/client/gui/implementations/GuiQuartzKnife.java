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


import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerQuartzKnife;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.items.contents.QuartzKnifeObj;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import java.io.IOException;


public class GuiQuartzKnife extends AEBaseGui implements IDropToFillTextField
{

	private MEGuiTextField textField;

	public GuiQuartzKnife( final InventoryPlayer inventoryPlayer, final QuartzKnifeObj te )
	{
		super( new ContainerQuartzKnife( inventoryPlayer, te ) );
		this.ySize = 184;

        this.textField = new MEGuiTextField(90, 12)
		{

			@Override
			public void onTextChange(final String oldText)
			{
				try {
					final String Out = getText();
					( (ContainerQuartzKnife) inventorySlots ).setName( Out );
					NetworkHandler.instance.sendToServer( new PacketValueConfig( "QuartzKnife.Name", Out ) );
				} catch (final IOException e) {
					AELog.debug( e );
				}
			}

		};
        this.textField.setMaxStringLength(32);
	}

	@Override
	public void initGui()
	{
		super.initGui();

		this.textField.x = this.guiLeft + 21;
        this.textField.y = this.guiTop + 30;
		this.textField.setFocused( true );
	}

	@Override
	public void drawFG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.fontRendererObj.drawString( this.getGuiDisplayName( GuiText.QuartzCuttingKnife.getLocal() ), 8, 6, 4210752 );
		this.fontRendererObj.drawString( GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752 );
	}

	@Override
	public void drawBG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.bindTexture( "guis/quartzknife.png" );
		this.drawTexturedModalRect( offsetX, offsetY, 0, 0, this.xSize, this.ySize );
		this.textField.drawTextBox();
	}

	@Override
	protected void mouseClicked( final int xCoord, final int yCoord, final int btn )
    {
		this.textField.mouseClicked( xCoord, yCoord, btn );
		super.mouseClicked( xCoord, yCoord, btn );
	}

	@Override
	protected void keyTyped( final char character, final int key )
	{
		if (!this.textField.textboxKeyTyped(character, key)) {
			super.keyTyped(character, key);
		}
	}

    public boolean isOverTextField(final int mousex, final int mousey)
	{
		return textField.isMouseIn(mousex, mousey);
	}

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack)
	{
		textField.setText(displayName);
	}

}
