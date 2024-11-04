package appeng.recipes.game;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPartItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class PreConfiguredPartRecipe implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting invCraft, World world) {
        return getCraftingResult(invCraft) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting invCraft) {
        ItemStack memoryCardStack = null;
        IMemoryCard memoryCard = null;
        ItemStack part = null;
        for (int i = 0; i < invCraft.getSizeInventory(); i++) {
            ItemStack stack = invCraft.getStackInSlot(i);
            if (stack != null) {
                if (stack.getItem() instanceof IPartItem) {
                    if (part != null)
                        return null;
                    part = stack;
                } else if (stack.getItem() instanceof IMemoryCard item) {
                    if (memoryCardStack != null)
                        return null;
                    memoryCardStack = stack;
                    memoryCard = item;
                }
            }
        }

        if (memoryCardStack == null || part == null) {
            return null;
        }

        NBTTagCompound data = memoryCard.getData(memoryCardStack);
        if (data != null && !data.hasNoTags()){
            ItemStack result = part.copy();
            result.stackSize = 1;
            NBTTagCompound partData = result.getTagCompound();
            if (partData == null) {
                partData = new NBTTagCompound();
            }
            partData.setTag("memoryCardData", data);
            result.setTagCompound(partData);
            return result;
        }

        return null;
    }

    @Override
    public int getRecipeSize() {
        return 4;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }

}
