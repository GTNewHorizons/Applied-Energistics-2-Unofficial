package appeng.container.implementations;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.*;
import appeng.helpers.IContainerCraftingPacket;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class ContainerPatternTermEx extends ContainerMEMonitorable implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket {
    private final PartPatternTerminalEx patternTerminal;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory( null, 1 );
    private final IInventory crafting;
    private final SlotFakeCraftingMatrix[] craftingSlots = new SlotFakeCraftingMatrix[16];
    private final OptionalSlotFake[] outputSlots = new OptionalSlotFake[4];
    private final SlotRestrictedInput patternSlotIN;
    private final SlotRestrictedInput patternSlotOUT;
    @GuiSync( 96 + (17-9) )
    public boolean substitute = false;

    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable )
    {
        super( ip, monitorable, false );
        this.patternTerminal = (PartPatternTerminalEx) monitorable;

        final IInventory patternInv = this.getPatternTerminal().getInventoryByName( "pattern" );
        final IInventory output = this.getPatternTerminal().getInventoryByName( "output" );

        this.crafting = this.getPatternTerminal().getInventoryByName( "crafting" );

        for( int y = 0; y < 4; y++ )
        {
            for( int x = 0; x < 4; x++ )
            {
                this.addSlotToContainer( this.craftingSlots[x + y * 4] = new SlotFakeCraftingMatrix( this.crafting, x + y * 4, 15 + x * 18, -83 + y * 18 ) );
            }
        }

        for( int y = 0; y < 4; y++ )
        {
            this.addSlotToContainer( this.outputSlots[y] = new SlotPatternOutputs( output, this, y, 112, -83 + y * 18, 0, 0, 1 ) );
            this.outputSlots[y].setRenderDisabled( false );
            this.outputSlots[y].setIIcon( -1 );
        }

        this.addSlotToContainer( this.patternSlotIN = new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.BLANK_PATTERN, patternInv, 0, 147, -72 - 9, this.getInventoryPlayer() ) );
        this.addSlotToContainer( this.patternSlotOUT = new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, patternInv, 1, 147, -72 + 34, this.getInventoryPlayer() ) );

        this.patternSlotOUT.setStackLimit( 1 );

        this.bindPlayerInventory( ip, 0, 0 );
        this.updateOrderOfOutputSlots();
    }

    private void updateOrderOfOutputSlots()
    {
       for( int y = 0; y < 4; y++ )
           this.outputSlots[y].xDisplayPosition = this.outputSlots[y].getX();
    }

    @Override
    public void putStackInSlot( final int par1, final ItemStack par2ItemStack )
    {
        super.putStackInSlot( par1, par2ItemStack );
        this.getAndUpdateOutput();
    }

    @Override
    public void putStacksInSlots( final ItemStack[] par1ArrayOfItemStack )
    {
        super.putStacksInSlots( par1ArrayOfItemStack );
        this.getAndUpdateOutput();
    }

    private void getAndUpdateOutput()
    {
        final InventoryCrafting ic = new InventoryCrafting( this, 4, 4 );

        for( int x = 0; x < ic.getSizeInventory(); x++ )
        {
            ic.setInventorySlotContents( x, this.crafting.getStackInSlot( x ) );
        }

        final ItemStack is = CraftingManager.getInstance().findMatchingRecipe( ic, this.getPlayerInv().player.worldObj );
        this.cOut.setInventorySlotContents( 0, is );
    }

    @Override
    public void saveChanges()
    {

    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removedStack, final ItemStack newStack )
    {

    }

    public void encode()
    {
        ItemStack output = this.patternSlotOUT.getStack();

        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();

        // if there is no input, this would be silly.
        if( in == null || out == null )
        {
            return;
        }

        // first check the output slots, should either be null, or a pattern
        if (output != null && this.isNotPattern(output)) {
            return;
        }// if nothing is there we should snag a new pattern.
        else if (output == null) {
            output = this.patternSlotIN.getStack();
            if (this.isNotPattern(output)) {
                return; // no blanks.
            }

            // remove one, and clear the input slot.
            output.stackSize--;
            if( output.stackSize == 0 )
            {
                this.patternSlotIN.putStack( null );
            }

            // add a new encoded pattern.
            for( final ItemStack encodedPatternStack : AEApi.instance().definitions().items().encodedPattern().maybeStack( 1 ).asSet() )
            {
                output = encodedPatternStack;
                this.patternSlotOUT.putStack( output );
            }
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for( final ItemStack i : in )
        {
            tagIn.appendTag( this.createItemTag( i ) );
        }

        for( final ItemStack i : out )
        {
            tagOut.appendTag( this.createItemTag( i ) );
        }

        encodedValue.setTag( "in", tagIn );
        encodedValue.setTag( "out", tagOut );
        encodedValue.setBoolean( "crafting", false );
        encodedValue.setBoolean( "substitute", this.isSubstitute() );

        output.setTagCompound( encodedValue );
    }

    private ItemStack[] getInputs()
    {
        final ItemStack[] input = new ItemStack[16];
        boolean hasValue = false;

        for( int x = 0; x < this.craftingSlots.length; x++ )
        {
            input[x] = this.craftingSlots[x].getStack();
            if( input[x] != null )
            {
                hasValue = true;
            }
        }

        if( hasValue )
        {
            return input;
        }

        return null;
    }

    private ItemStack[] getOutputs()
    {
        final List<ItemStack> list = new ArrayList<>(4);
        boolean hasValue = false;

        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();

            if (out != null && out.stackSize > 0) {
                list.add(out);
                hasValue = true;
            }
        }

        if (hasValue) {
            return list.toArray(new ItemStack[0]);
        }

        return null;
    }

    private boolean isNotPattern(final ItemStack output )
    {
        if( output == null )
        {
            return true;
        }

        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs( output );
        isPattern |= definitions.materials().blankPattern().isSameAs( output );

        return !isPattern;
    }

    private NBTBase createItemTag(final ItemStack i )
    {
        final NBTTagCompound c = new NBTTagCompound();

        if( i != null )
        {
            i.writeToNBT( c );
        }

        return c;
    }

    @Override
    public boolean isSlotEnabled( final int idx )
    {
        if( idx == 1 )
        {
            return true;
        }
        else if( idx == 2 )
        {
            return false;
        }
        else
        {
            return false;
        }
    }

    @Override
    public void detectAndSendChanges()
    {
        super.detectAndSendChanges();
        if( Platform.isServer() )
        {
            this.substitute = this.patternTerminal.isSubstitution();
        }
    }

    @Override
    public void onSlotChange( final Slot s )
    {
        if( s == this.patternSlotOUT && Platform.isServer() )
        {
            for( final Object crafter : this.crafters )
            {
                final ICrafting icrafting = (ICrafting) crafter;

                for( final Object g : this.inventorySlots )
                {
                    if( g instanceof OptionalSlotFake || g instanceof SlotFakeCraftingMatrix )
                    {
                        final Slot sri = (Slot) g;
                        icrafting.sendSlotContents( this, sri.slotNumber, sri.getStack() );
                    }
                }
                ( (EntityPlayerMP) icrafting ).isChangingQuantityOnly = false;
            }
            this.detectAndSendChanges();
        }
    }

    public void clear()
    {
        for( final Slot s : this.craftingSlots )
        {
            s.putStack( null );
        }

        for( final Slot s : this.outputSlots )
        {
            s.putStack( null );
        }

        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    @Override
    public IInventory getInventoryByName( final String name )
    {
        if( name.equals( "player" ) )
        {
            return this.getInventoryPlayer();
        }
        return this.getPatternTerminal().getInventoryByName( name );
    }

    @Override
    public boolean useRealItems()
    {
        return false;
    }

    public PartPatternTerminalEx getPatternTerminal()
    {
        return this.patternTerminal;
    }

    private boolean isSubstitute()
    {
        return this.substitute;
    }

    public void setSubstitute( final boolean substitute )
    {
        this.substitute = substitute;
    }
}
