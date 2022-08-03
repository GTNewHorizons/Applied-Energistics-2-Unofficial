package appeng.client.gui.implementations;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerOreFilter;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.localization.GuiColors;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IOreFilterable;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileCellWorkbench;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.io.IOException;

public class GuiOreFilter extends AEBaseGui implements IDropToFillTextField
{
    private MEGuiTextField textField;

    public GuiOreFilter(InventoryPlayer ip, IOreFilterable obj)
    {
        super(new ContainerOreFilter(ip, obj));
        this.xSize = 256;

        this.textField = new MEGuiTextField(231, 12)
        {

			@Override
			public void onTextChange(final String oldText)
            {
                final String text = getText();

                if (!text.equals(oldText)) {
                    ((ContainerOreFilter) inventorySlots).setFilter(text);
                }
			}

		};

        this.textField.setMaxStringLength(120);
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.textField.x = this.guiLeft + 12;
        this.textField.y = this.guiTop + 35;
        this.textField.setFocused(true);

        ((ContainerOreFilter) this.inventorySlots).setTextField(this.textField);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY)
    {
        this.fontRendererObj.drawString( GuiText.OreFilterLabel.getLocal(), 12, 8, GuiColors.OreFilterLabel.getColor() );
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY)
    {
        this.bindTexture( "guis/renamer.png" );
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
    protected void keyTyped(final char character, final int key)
    {
        if (key == 28) {// Enter
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("OreFilter", this.textField.getText()));
            } catch (IOException e) {
                AELog.debug(e);
            }
            final Object target = ( (AEBaseContainer) this.inventorySlots ).getTarget();
            GuiBridge OriginalGui = null;
            if (target instanceof PartStorageBus)
                OriginalGui = GuiBridge.GUI_STORAGEBUS;
            else if (target instanceof PartSharedItemBus)
                OriginalGui = GuiBridge.GUI_BUS;
            else if (target instanceof TileCellWorkbench)
                OriginalGui = GuiBridge.GUI_CELL_WORKBENCH;

            if (OriginalGui != null)
                NetworkHandler.instance.sendToServer( new PacketSwitchGuis( OriginalGui ) );
            else
                this.mc.thePlayer.closeScreen();

        } else if (!this.textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
            
    }

    public boolean isOverTextField(final int mousex, final int mousey)
	{
		return textField.isMouseIn(mousex, mousey);
	}

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack)
	{
        final int[] ores = OreDictionary.getOreIDs(stack);

        if (ores.length > 0) {
            textField.setText(OreDictionary.getOreName(ores[0]));
        } else {
            textField.setText(displayName);
        }

	}

}
