package appeng.helpers;

import static appeng.util.Platform.isServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.google.common.collect.ImmutableList;

import appeng.api.AEApi;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.parts.IPatternTerminal;
import appeng.api.parts.IPatternTerminal.PatternEncodeListener;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemTunnelPattern;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class PatternEncodingHelper {

    static private IAEStack<?>[] getInputs(IPatternTerminal terminal) {
        final IAEStackInventory inputs = terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT);
        final IAEStack<?>[] input = new IAEStack<?>[inputs.getSizeInventory()];
        boolean hasValue = false;

        for (int i = 0; i < inputs.getSizeInventory(); i++) {
            input[i] = inputs.getAEStackInSlot(i);
            if (input[i] != null) {
                hasValue = true;
            }
        }

        if (hasValue) {
            return input;
        }

        return null;
    }

    static private ItemStack getCraftingOutput(IPatternTerminal terminal, World world) {
        if (!terminal.isCraftingRecipe() || !isServer()) return null;
        final InventoryCrafting ic = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return false;
            }
        }, 3, 3);

        final IAEStackInventory inputs = terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT);
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            if (inputs.getAEStackInSlot(x) instanceof IAEItemStack aes) {
                ic.setInventorySlotContents(x, aes.getItemStack());
            }
        }
        return Platform.findMatchingRecipeOutput(ic, world);
    }

    static private IAEStack<?>[] getOutputs(IPatternTerminal terminal, World world) {
        final IAEStackInventory outputs = terminal.getAEInventoryByName(StorageName.CRAFTING_OUTPUT);
        if (terminal.isCraftingRecipe()) {
            final IAEStack<?> out = AEItemStack.create(getCraftingOutput(terminal, world));

            if (out != null && out.getStackSize() > 0) {
                return new IAEStack<?>[] { out };
            }
        } else {
            final List<IAEStack<?>> list = new ArrayList<>(3);
            boolean hasValue = false;

            for (int i = 0; i < outputs.getSizeInventory(); i++) {
                final IAEStack<?> out = outputs.getAEStackInSlot(i);

                if (out != null && out.getStackSize() > 0) {
                    list.add(out);
                    hasValue = true;
                }
            }

            if (hasValue) {
                return list.toArray(new IAEStack<?>[0]);
            }
        }

        return null;
    }

    static public boolean encode(IPatternTerminal terminal, IEnergySource powerSource,
            IMEMonitor<IAEItemStack> itemMonitor, BaseActionSource actionSource, String auther, World world) {
        IInventory pattern = terminal.getInventoryByName("pattern");
        ItemStack output = pattern.getStackInSlot(1);

        final IAEStack<?>[] in = getInputs(terminal);
        final IAEStack<?>[] out = getOutputs(terminal, world);

        // if there is no input, this would be silly.
        if (in == null) {
            return false;
        }
        final boolean inputOnly = !terminal.isCraftingRecipe() && (out == null || out.length == 0);
        if (!inputOnly && out == null) {
            return false;
        }
        final UUID inputOnlyUuid = inputOnly && ItemTunnelPattern.isTunnelPattern(output)
                ? ItemTunnelPattern.getTunnelUuid(output)
                : null;

        // first check the output slots, should either be null, or a pattern
        if (output != null) {
            if (!IPatternTerminal.isEncodedPattern(output)) {
                return false;
            }
        } // if nothing is there we should snag a new pattern.
        else {
            ItemStack blank = pattern.getStackInSlot(0);

            if (IPatternTerminal.isBlankPattern(blank)) {
                blank.stackSize--;
                if (blank.stackSize == 0) {
                    pattern.setInventorySlotContents(0, null);
                }
            } else if (blank == null) {
                IAEItemStack extracted = Platform.poweredExtraction(
                        powerSource,
                        itemMonitor,
                        IPatternTerminal.createBlankPattern(),
                        actionSource);
                if (extracted == null || extracted.getStackSize() <= 0) {
                    return false;
                }
            } else {
                return false;
            }
        }

        // add a new encoded pattern.
        if (terminal.isCraftingRecipe()) {
            output = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).orNull();
        } else if (inputOnly) {
            output = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).orNull();
        } else {
            output = AEApi.instance().definitions().items().encodedUltimatePattern().maybeStack(1).orNull();
        }
        if (output == null) {
            return false;
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final IAEStack<?> i : in) {
            tagIn.appendTag(i != null ? i.toNBTGeneric() : new NBTTagCompound());
        }

        if (out != null) {
            for (final IAEStack<?> o : out) {
                tagOut.appendTag(o != null ? o.toNBTGeneric() : new NBTTagCompound());
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        if (terminal.isCraftingRecipe()) encodedValue.setBoolean("crafting", true);
        encodedValue.setBoolean("substitute", terminal.isSubstitution());
        encodedValue.setBoolean("beSubstitute", terminal.canBeSubstitution());
        if (inputOnly) {
            final UUID uuid = inputOnlyUuid != null ? inputOnlyUuid : UUID.randomUUID();
            ItemTunnelPattern.writeTunnelUuid(encodedValue, uuid);
        }
        encodedValue.setString("author", auther);

        output.setTagCompound(encodedValue);
        pattern.setInventorySlotContents(1, output);
        for (final PatternEncodeListener listener : ImmutableList.copyOf(terminal.getPatternEncodeListeners())) {
            listener.onEncoded(terminal, output);
        }
        return true;
    }
}
