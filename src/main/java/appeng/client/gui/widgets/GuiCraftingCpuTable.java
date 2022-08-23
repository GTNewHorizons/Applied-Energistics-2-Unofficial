package appeng.client.gui.widgets;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class GuiCraftingCpuTable
{
    public AEBaseGui parent;

    public static final int CPU_TABLE_WIDTH = 94;
    public static final int CPU_TABLE_HEIGHT = 164;
    public static final int CPU_TABLE_SLOT_XOFF = 100;
    public static final int CPU_TABLE_SLOT_YOFF = 0;
    public static final int CPU_TABLE_SLOT_WIDTH = 67;
    public static final int CPU_TABLE_SLOT_HEIGHT = 23;

    private final GuiScrollbar cpuScrollbar;

    private String selectedCPUName = "";
    private final IntSupplier selectedCpuSerialProvider;
    private final Supplier<List<CraftingCPUStatus>> cpuListProvider;

    public GuiCraftingCpuTable( AEBaseGui parent, IntSupplier selectedCpuSerialProvider, Supplier<List<CraftingCPUStatus>> cpuListProvider )
    {
        this.parent = parent;
        this.cpuScrollbar = new GuiScrollbar();
        this.cpuScrollbar.setLeft( -16 );
        this.cpuScrollbar.setTop( 19 );
        this.cpuScrollbar.setWidth( 12 );
        this.cpuScrollbar.setHeight( 137 );

        this.selectedCpuSerialProvider = selectedCpuSerialProvider;
        this.cpuListProvider = cpuListProvider;
    }

    public String getSelectedCPUName()
    {
        return selectedCPUName;
    }

    public void drawScreen( final int mouseX, final int mouseY, final float btn )
    {
        final List<CraftingCPUStatus> cpus = cpuListProvider.get();
        final int selectedCpuSerial = selectedCpuSerialProvider.getAsInt();

        this.selectedCPUName = null;
        this.cpuScrollbar.setRange( 0, Integer.max( 0, cpus.size() - 6 ), 1 );
        for( CraftingCPUStatus cpu : cpus )
        {
            if( cpu.getSerial() == selectedCpuSerial )
            {
                this.selectedCPUName = cpu.getName();
            }
        }
    }

    public void drawFG( int offsetX, int offsetY, int mouseX, int mouseY )
    {
        if( this.cpuScrollbar != null )
        {
            this.cpuScrollbar.draw( parent );
        }
        final List<CraftingCPUStatus> cpus = cpuListProvider.get();
        final int selectedCpuSerial = selectedCpuSerialProvider.getAsInt();
        final int firstCpu = this.cpuScrollbar.getCurrentScroll();
        CraftingCPUStatus hoveredCpu = hitCpu( mouseX, mouseY );
        {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            for( int i = firstCpu; i < firstCpu + 6; i++ )
            {
                if( i < 0 || i >= cpus.size() )
                {
                    continue;
                }
                CraftingCPUStatus cpu = cpus.get( i );
                if( cpu == null )
                {
                    continue;
                }
                int x = -CPU_TABLE_WIDTH + 9;
                int y = 19 + ( i - firstCpu ) * CPU_TABLE_SLOT_HEIGHT;
                if( cpu.getSerial() == selectedCpuSerial )
                {
                    GL11.glColor4f( 0.0F, 0.8352F, 1.0F, 1.0F );
                } else if( hoveredCpu != null && hoveredCpu.getSerial() == cpu.getSerial() )
                {
                    GL11.glColor4f( 0.65F, 0.9F, 1.0F, 1.0F );
                } else
                {
                    GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );
                }
                parent.bindTexture( "guis/cpu_selector.png" );
                parent.drawTexturedModalRect( x, y, CPU_TABLE_SLOT_XOFF, CPU_TABLE_SLOT_YOFF, CPU_TABLE_SLOT_WIDTH, CPU_TABLE_SLOT_HEIGHT );
                GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );

                String name = cpu.getName();
                if( name == null || name.isEmpty() )
                {
                    name = GuiText.CPUs.getLocal() + " #" + cpu.getSerial();
                }
                if( name.length() > 12 )
                {
                    name = name.substring( 0, 11 ) + "..";
                }
                GL11.glPushMatrix();
                GL11.glTranslatef( x + 3, y + 3, 0 );
                GL11.glScalef( 0.8f, 0.8f, 1.0f );
                font.drawString( name, 0, 0, GuiColors.CraftingStatusCPUName.getColor() );
                GL11.glPopMatrix();

                GL11.glPushMatrix();
                GL11.glTranslatef( x + 3, y + 11, 0 );
                final IAEItemStack craftingStack = cpu.getCrafting();
                if( craftingStack != null )
                {
                    final int iconIndex = 16 * 11 + 2;
                    parent.bindTexture( "guis/states.png" );
                    final int uv_y = iconIndex / 16;
                    final int uv_x = iconIndex - uv_y * 16;

                    GL11.glScalef( 0.5f, 0.5f, 1.0f );
                    GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );
                    parent.drawTexturedModalRect( 0, 0, uv_x * 16, uv_y * 16, 16, 16 );
                    GL11.glTranslatef( 18.0f, 2.0f, 0.0f );
                    String amount = Long.toString( craftingStack.getStackSize() );
                    if( amount.length() > 5 )
                    {
                        amount = amount.substring( 0, 5 ) + "..";
                    }
                    GL11.glScalef( 1.5f, 1.5f, 1.0f );
                    font.drawString( amount, 0, 0, GuiColors.CraftingStatusCPUAmount.getColor() );
                    GL11.glPopMatrix();
                    GL11.glPushMatrix();
                    GL11.glTranslatef( x + CPU_TABLE_SLOT_WIDTH - 19, y + 3, 0 );
                    parent.drawItem( 0, 0, craftingStack.getItemStack() );
                } else
                {
                    final int iconIndex = 16 * 4 + 3;
                    parent.bindTexture( "guis/states.png" );
                    final int uv_y = iconIndex / 16;
                    final int uv_x = iconIndex - uv_y * 16;

                    GL11.glScalef( 0.5f, 0.5f, 1.0f );
                    GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );
                    parent.drawTexturedModalRect( 0, 0, uv_x * 16, uv_y * 16, 16, 16 );
                    GL11.glTranslatef( 18.0f, 2.0f, 0.0f );
                    GL11.glScalef( 1.5f, 1.5f, 1.0f );
                    font.drawString( cpu.formatStorage(), 0, 0, GuiColors.CraftingStatusCPUStorage.getColor() );
                }
                GL11.glPopMatrix();
            }
            GL11.glColor4f( 1.0F, 1.0F, 1.0F, 1.0F );
        }
        if( hoveredCpu != null )
        {
            StringBuilder tooltip = new StringBuilder();
            String name = hoveredCpu.getName();
            if( name != null && !name.isEmpty() )
            {
                tooltip.append( name );
                tooltip.append( '\n' );
            } else
            {
                tooltip.append( GuiText.CPUs.getLocal() );
                tooltip.append( " #" );
                tooltip.append( hoveredCpu.getSerial() );
                tooltip.append( '\n' );
            }
            IAEItemStack crafting = hoveredCpu.getCrafting();
            if( crafting != null && crafting.getStackSize() > 0 )
            {
                tooltip.append( GuiText.Crafting.getLocal() );
                tooltip.append( ": " );
                tooltip.append( crafting.getStackSize() );
                tooltip.append( ' ' );
                tooltip.append( crafting.getItemStack().getDisplayName() );
                tooltip.append( '\n' );
                tooltip.append( hoveredCpu.getRemainingItems() );
                tooltip.append( " / " );
                tooltip.append( hoveredCpu.getTotalItems() );
                tooltip.append( '\n' );
            }
            if( hoveredCpu.getStorage() > 0 )
            {
                tooltip.append( GuiText.Bytes.getLocal() );
                tooltip.append( ": " );
                tooltip.append( hoveredCpu.formatStorage() );
                tooltip.append( '\n' );
            }
            if( hoveredCpu.getCoprocessors() > 0 )
            {
                tooltip.append( GuiText.CoProcessors.getLocal() );
                tooltip.append( ": " );
                tooltip.append( hoveredCpu.getCoprocessors() );
                tooltip.append( '\n' );
            }
            if( tooltip.length() > 0 )
            {
                parent.drawTooltip( mouseX - offsetX, mouseY - offsetY, 0, tooltip.toString() );
            }
        }
    }

    public void drawBG( int offsetX, int offsetY, int mouseX, int mouseY )
    {
        parent.bindTexture( "guis/cpu_selector.png" );
        parent.drawTexturedModalRect( offsetX - CPU_TABLE_WIDTH, offsetY, 0, 0, CPU_TABLE_WIDTH, CPU_TABLE_HEIGHT );
    }

    /**
     * Tests if a cpu button is under the cursor. Subtract guiLeft, guiTop from x, y before calling
     */
    public CraftingCPUStatus hitCpu( int x, int y )
    {
        x -= -CPU_TABLE_WIDTH;
        if( !( x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT ) )
        {
            return null;
        }
        int scrollOffset = this.cpuScrollbar != null ? this.cpuScrollbar.getCurrentScroll() : 0;
        int cpuId = scrollOffset + ( y - 19 ) / CPU_TABLE_SLOT_HEIGHT;
        List<CraftingCPUStatus> cpus = cpuListProvider.get();
        return ( cpuId >= 0 && cpuId < cpus.size() ) ? cpus.get( cpuId ) : null;
    }

    /**
     * Subtract guiLeft, guiTop from x, y before calling
     */
    public void mouseClicked( int xCoord, int yCoord, int btn )
    {
        if( cpuScrollbar != null )
        {
            cpuScrollbar.click( parent, xCoord, yCoord );
        }
    }

    /**
     * @return True if event was handled
     */
    public boolean handleMouseInput( int guiLeft, int guiTop )
    {
        int x = Mouse.getEventX() * parent.width / parent.mc.displayWidth;
        int y = parent.height - Mouse.getEventY() * parent.height / parent.mc.displayHeight - 1;
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        int dwheel = Mouse.getEventDWheel();
        if( x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT )
        {
            if( this.cpuScrollbar != null && dwheel != 0 )
            {
                this.cpuScrollbar.wheel( dwheel );
                return true;
            }
        }
        return false;
    }

    public boolean hideItemPanelSlot( int x, int y, int w, int h )
    {
        x += CPU_TABLE_WIDTH;
        boolean xInside =
                ( x >= 0 && x < CPU_TABLE_SLOT_WIDTH + 9 )
                        || ( x + w >= 0 && x + w < CPU_TABLE_SLOT_WIDTH + 9 )
                        || ( x <= 0 && x + w >= CPU_TABLE_SLOT_WIDTH + 9 );
        boolean yInside =
                ( y >= 0 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT )
                        || ( y + h >= 0 && y + h < 19 + 6 * CPU_TABLE_SLOT_HEIGHT )
                        || ( y < 0 && y + h >= 19 + 6 * CPU_TABLE_SLOT_HEIGHT );
        return xInside && yInside;
    }
}
