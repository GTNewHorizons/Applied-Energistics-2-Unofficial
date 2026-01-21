package appeng.integration.modules.NEIHelpers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

public class NEIInputHandler implements IContainerInputHandler {

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        ItemStack stack = GuiContainerManager.getStackMouseOver(gui);
        if (stack == null) return false;
        Item item = stack.getItem();

        if (item instanceof ICraftingPatternItem pattern) {
            if (NEIClientConfig.isKeyHashDown("gui.pattern_view")) {
                return GuiUsageRecipe.openRecipeGui("pattern", stack);
            }

            ICraftingPatternDetails details = pattern.getPatternForItem(stack, Minecraft.getMinecraft().theWorld);
            if (details == null) return false;
            stack = details.getCondensedAEOutputs()[0].getItemStackForNEI();

            if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
                return GuiCraftingRecipe.openRecipeGui("item", stack);
            }

            if (NEIClientConfig.isKeyHashDown("gui.usage")) {
                return GuiUsageRecipe.openRecipeGui("item", stack);
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
