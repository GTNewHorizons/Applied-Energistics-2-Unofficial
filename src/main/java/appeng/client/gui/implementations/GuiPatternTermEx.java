package appeng.client.gui.implementations;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import java.io.IOException;

public class GuiPatternTermEx extends GuiMEMonitorable {
    private static final String SUBSITUTION_DISABLE = "0";
    private static final String SUBSITUTION_ENABLE = "1";

    private final ContainerPatternTermEx container;

    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;

    public GuiPatternTermEx(final InventoryPlayer inventoryPlayer, final ITerminalHost te )
    {
        super( inventoryPlayer, te, new ContainerPatternTermEx( inventoryPlayer, te ) );
        this.container = (ContainerPatternTermEx) this.inventorySlots;
        this.setReservedSpace( 81 );
    }

    @Override
    protected void actionPerformed( final GuiButton btn )
    {
        super.actionPerformed( btn );

        try
        {
            if( this.encodeBtn == btn )
            {
                NetworkHandler.instance.sendToServer( new PacketValueConfig( "PatternTerminalEx.Encode", "1" ) );
            }

            if( this.clearBtn == btn )
            {
                NetworkHandler.instance.sendToServer( new PacketValueConfig( "PatternTerminalEx.Clear", "1" ) );
            }

            if( this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn )
            {
                NetworkHandler.instance.sendToServer( new PacketValueConfig( "PatternTerminalEx.Substitute", this.substitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE ) );
            }
        }
        catch( final IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.substitutionsEnabledBtn = new GuiImgButton( this.guiLeft + 97, this.guiTop + this.ySize - 163, Settings.ACTIONS, ItemSubstitution.ENABLED );
        this.substitutionsEnabledBtn.setHalfSize( true );
        this.buttonList.add( this.substitutionsEnabledBtn );

        this.substitutionsDisabledBtn = new GuiImgButton( this.guiLeft + 97, this.guiTop + this.ySize - 163, Settings.ACTIONS, ItemSubstitution.DISABLED );
        this.substitutionsDisabledBtn.setHalfSize( true );
        this.buttonList.add( this.substitutionsDisabledBtn );

        this.clearBtn = new GuiImgButton( this.guiLeft + 87, this.guiTop + this.ySize - 163, Settings.ACTIONS, ActionItems.CLOSE );
        this.clearBtn.setHalfSize( true );
        this.buttonList.add( this.clearBtn );

        this.encodeBtn = new GuiImgButton( this.guiLeft + 147, this.guiTop + this.ySize - 142, Settings.ACTIONS, ActionItems.ENCODE );
        this.buttonList.add( this.encodeBtn );
    }

    @Override
    public void drawFG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
    {
        if( this.container.substitute )
        {
            this.substitutionsEnabledBtn.visible = true;
            this.substitutionsDisabledBtn.visible = false;
        }
        else
        {
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = true;
        }

        super.drawFG( offsetX, offsetY, mouseX, mouseY );
        this.fontRendererObj.drawString( GuiText.PatternTerminalEx.getLocal(), 8, this.ySize - 96 + 2 - this.getReservedSpace(), 4210752 );
    }

    @Override
    protected String getBackground()
    {
        return "guis/pattern3.png";
    }

    @Override
    protected void repositionSlot( final AppEngSlot s )
    {
        if( s.isPlayerSide() )
        {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 5;
        }
        else
        {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 3;
        }
    }
}
