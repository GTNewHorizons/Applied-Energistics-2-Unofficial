package appeng.test.tile.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import appeng.tile.crafting.TileCraftingTile;

public class TileCraftingTileFunctionalTest {

    @Test
    void unformedCoreKeepsPendingJobAcrossSaves() {
        final NBTTagList inventory = new NBTTagList();
        inventory.appendTag(new ItemStack(Blocks.cobblestone, 64).writeToNBT(new NBTTagCompound()));
        final NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean("core", true);
        saved.setTag("inventory", inventory);

        final TileCraftingTile unformed = new TileCraftingTile();
        unformed.readFromNBT(saved);
        final NBTTagCompound resaved = new NBTTagCompound();
        unformed.writeToNBT(resaved);
        final TileCraftingTile reloaded = new TileCraftingTile();
        reloaded.readFromNBT(resaved);

        assertNotNull(reloaded.getPreviousState());
        assertEquals(inventory, reloaded.getPreviousState().getTag("inventory"));
    }
}
