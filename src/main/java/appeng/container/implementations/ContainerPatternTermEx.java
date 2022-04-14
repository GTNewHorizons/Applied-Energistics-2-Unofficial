package appeng.container.implementations;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.*;
import appeng.helpers.IContainerCraftingPacket;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

import static appeng.container.implementations.ContainerPatternTerm.canDoubleStacks;
import static appeng.container.implementations.ContainerPatternTerm.doubleStacksInternal;

public class ContainerPatternTermEx extends ContainerMEMonitorable implements IOptionalSlotHost, IContainerCraftingPacket {
    private final PartPatternTerminalEx patternTerminal;
    private final OptionalSlotFake[] craftingSlots = new OptionalSlotFake[16];
    private final OptionalSlotFake[] outputSlots = new OptionalSlotFake[16];
    private final SlotRestrictedInput patternSlotIN;
    private final SlotRestrictedInput patternSlotOUT;
    @GuiSync( 96 + (17-9) + 12 )
    public boolean substitute = false;
    @GuiSync( 96 + (17-9) + 16 )
    public boolean inverted;
    public boolean initialUpdatePassed = false;
    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable )
    {
        super( ip, monitorable, false );
        this.patternTerminal = (PartPatternTerminalEx) monitorable;
        inverted = patternTerminal.isInverted();
        final IInventory patternInv = this.getPatternTerminal().getInventoryByName( "pattern" );
        final IInventory output = this.getPatternTerminal().getInventoryByName( "output" );
        final IInventory crafting = this.getPatternTerminal().getInventoryByName("crafting");

        for( int y = 0; y < 4; y++ )
        {
            for( int x = 0; x < 4; x++ )
            {
                this.addSlotToContainer( this.craftingSlots[x + y * 4] = new OptionalSlotFake(crafting, this, x + y * 4, 15, -83, x, y, x + 4 ) );
                this.craftingSlots[x + y * 4].setRenderDisabled(false);
            }
        }
        for( int x = 0; x < 4; x++ )
        {
            for( int y = 0; y < 4; y++ )
            {
                this.addSlotToContainer( this.outputSlots[x*4 + y] = new OptionalSlotFake( output, this, x*4 + y, 112, -83, -x, y, x ) );
                this.outputSlots[x*4 + y].setRenderDisabled(false);
            }
        }

        this.addSlotToContainer( this.patternSlotIN = new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.BLANK_PATTERN, patternInv, 0, 147, -72 - 9, this.getInventoryPlayer() ) );
        this.addSlotToContainer( this.patternSlotOUT = new SlotRestrictedInput( SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, patternInv, 1, 147, -72 + 34, this.getInventoryPlayer() ) );

        this.patternSlotOUT.setStackLimit( 1 );

        this.bindPlayerInventory( ip, 0, 0 );
    }

    public void encodeAndMoveToInventory(boolean encodeWholeStack)
    {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if ( output != null )
        {
            if (encodeWholeStack)
            {
                ItemStack blanks = this.patternSlotIN.getStack();
                this.patternSlotIN.putStack(null);
                if (blanks != null)
                    output.stackSize += blanks.stackSize;
            }
            if (!getPlayerInv().addItemStackToInventory( output ))
            {
                getPlayerInv().player.entityDropItem(output, 0);
            }
            this.patternSlotOUT.putStack( null );
        }
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
        final List<ItemStack> list = new ArrayList<>(16);
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
        if (idx < 4) // outputs
        {
            return inverted || idx == 0;
        }
        else
        {
            return !inverted || idx == 4;
        }
    }

    @Override
    public void detectAndSendChanges()
    {
        super.detectAndSendChanges();
        if( Platform.isServer() )
        {
            substitute = patternTerminal.isSubstitution();
            if (inverted != patternTerminal.isInverted()) {
                inverted = patternTerminal.isInverted();
                offsetSlots(!initialUpdatePassed);
                initialUpdatePassed = true;
            }
        }
    }

    private void offsetSlots(boolean initial) {
        int offset = inverted ? 9000 : - 9000;
        if (!initial || inverted) {
            for (int y = 0; y < 4; y++) {
                for (int x = 1; x < 4; x++) {
                    craftingSlots[x + y * 4].xDisplayPosition -= offset;
                }
            }
        }
        if (!initial || !inverted) {
            for (int x = 1; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    outputSlots[x * 4 + y].xDisplayPosition += offset;
                }
            }
        }
    }
    @Override
    public void onUpdate( final String field, final Object oldValue, final Object newValue )
    {
        super.onUpdate( field, oldValue, newValue );

        if( field.equals( "inverted" ) )
        {
            offsetSlots(!initialUpdatePassed);
            initialUpdatePassed = true;
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

    public void doubleStacks(boolean isShift)
    {
        if (canDoubleStacks(craftingSlots) && canDoubleStacks(outputSlots))
        {
            doubleStacksInternal(this.craftingSlots);
            doubleStacksInternal(this.outputSlots);
            if (isShift)
            {
                while (canDoubleStacks(craftingSlots) && canDoubleStacks(outputSlots))
                {
                    doubleStacksInternal(this.craftingSlots);
                    doubleStacksInternal(this.outputSlots);
                }
            }
            this.detectAndSendChanges();
        }
    }
}
