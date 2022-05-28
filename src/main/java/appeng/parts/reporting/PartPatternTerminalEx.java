package appeng.parts.reporting;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

public class PartPatternTerminalEx extends AbstractPartTerminal {
    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartPatternTerm_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartPatternTerm_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartPatternTerm_Colored;

    private final AppEngInternalInventory crafting = new AppEngInternalInventory( this, 16 );
    private final AppEngInternalInventory output = new AppEngInternalInventory( this, 16 );
    private final AppEngInternalInventory pattern = new AppEngInternalInventory( this, 3 );

    private boolean substitute = false;
    private boolean inverted = false;

    @Reflected
    public PartPatternTerminalEx( final ItemStack is )
    {
        super( is );
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched )
    {
        for( final ItemStack is : this.pattern )
        {
            if( is != null )
            {
                drops.add( is );
            }
        }
    }

    @Override
    public void readFromNBT( final NBTTagCompound data )
    {
        super.readFromNBT( data );
        this.setSubstitution( data.getBoolean( "substitute" ) );
        this.pattern.readFromNBT( data, "pattern" );
        this.output.readFromNBT( data, "outputList" );
        this.crafting.readFromNBT( data, "craftingGrid" );
        inverted = data.getBoolean( "inverted" );
    }

    @Override
    public void writeToNBT( final NBTTagCompound data )
    {
        super.writeToNBT( data );
        data.setBoolean( "substitute", this.substitute );
        this.pattern.writeToNBT( data, "pattern" );
        this.output.writeToNBT( data, "outputList" );
        this.crafting.writeToNBT( data, "craftingGrid" );
        data.setBoolean("inverted", inverted);
    }

    @Override
    public GuiBridge getGui(final EntityPlayer p )
    {
        int x = (int) p.posX;
        int y = (int) p.posY;
        int z = (int) p.posZ;
        if( this.getHost().getTile() != null )
        {
            x = this.getTile().xCoord;
            y = this.getTile().yCoord;
            z = this.getTile().zCoord;
        }

        if( GuiBridge.GUI_PATTERN_TERMINAL_EX.hasPermissions( this.getHost().getTile(), x, y, z, this.getSide(), p ) )
        {
            return GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
        return GuiBridge.GUI_ME;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack )
    {
        if( inv == this.pattern && slot == 1 )
        {
            final ItemStack is = this.pattern.getStackInSlot( 1 );
            if( is != null && is.getItem() instanceof ICraftingPatternItem)
            {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) is.getItem();
                final ICraftingPatternDetails details = pattern.getPatternForItem( is, this.getHost().getTile().getWorldObj() );
                if( details != null )
                {
                    this.setSubstitution( details.canSubstitute() );

                    for( int x = 0; x < this.crafting.getSizeInventory() && x < details.getInputs().length; x++ )
                    {
                        final IAEItemStack item = details.getInputs()[x];
                        this.crafting.setInventorySlotContents( x, item == null ? null : item.getItemStack() );
                    }

                    for( int x = 0; x < this.output.getSizeInventory() && x < details.getOutputs().length; x++ )
                    {
                        final IAEItemStack item = details.getOutputs()[x];
                        this.output.setInventorySlotContents( x, item == null ? null : item.getItemStack() );
                    }
                }
            }
        }
        this.getHost().markForSave();
    }

    public boolean isSubstitution()
    {
        return this.substitute;
    }

    public void setSubstitution( boolean canSubstitute )
    {
        this.substitute = canSubstitute;
    }

    @Override
    public IInventory getInventoryByName( final String name )
    {
        if( name.equals( "crafting" ) )
        {
            return this.crafting;
        }

        if( name.equals( "output" ) )
        {
            return this.output;
        }

        if( name.equals( "pattern" ) )
        {
            return this.pattern;
        }

        return super.getInventoryByName( name );
    }

    @Override
    public CableBusTextures getFrontBright()
    {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored()
    {
        return FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark()
    {
        return FRONT_DARK_ICON;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }
}
