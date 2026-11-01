package appeng.integration.modules.NEIHelpers;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.client.gui.implementations.GuiCellWorkbench;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiStorageBus;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.integration.modules.NEI;
import codechicken.nei.ItemPanels;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.SortableGroup;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

public class NEIInputHandler implements IContainerInputHandler {

    private List<ItemStack> draggedBookmarkGroup;

    @Override
    public boolean keyTyped(@NotNull GuiContainer gui, char keyChar, int keyCode) {
        if (!(gui instanceof GuiPatternTerm terminal) || !NEIClientConfig.isLoaded()
                || terminal.isSearchFieldManuallyFocused()
                || NEI.searchField.focused()) {
            return false;
        }

        if (!(terminal.getVirtualMESlotUnderMouse() instanceof VirtualMEPatternSlot slot)
                || slot.getAEStack() == null) {
            return false;
        }

        if (keyCode == Keyboard.KEY_NONE || keyCode == Keyboard.KEY_ESCAPE
                || (keyCode != KeyManager.getKeyCode("recipe.recipe")
                        && keyCode != KeyManager.getKeyCode("recipe.usage"))) {
            return false;
        }

        // Run the normal NEI handlers before the terminal's search field can consume the key.
        GuiContainerManager.getManager(gui).lastKeyTyped(keyCode, keyChar);
        // A missing recipe must not turn the shortcut into search text either.
        return true;
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
            final var outputs = details.getCondensedAEOutputs();
            if (outputs.length == 0) return false;
            stack = outputs[0].getItemStackForNEI();

            if (NEIClientConfig.isKeyHashDown("recipe.recipe")) {
                return GuiCraftingRecipe.openRecipeGui("item", stack);
            }

            if (NEIClientConfig.isKeyHashDown("recipe.usage")) {
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
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {
        if (button != 0) return;

        if (this.draggedBookmarkGroup != null) {
            if (gui instanceof GuiStorageBus storageBus) {
                storageBus.handleBookmarkGroupDrop(mousex, mousey, this.draggedBookmarkGroup);
            } else if (gui instanceof GuiCellWorkbench cellWorkbench) {
                cellWorkbench.handleBookmarkGroupDrop(mousex, mousey, this.draggedBookmarkGroup);
            }
        }
        this.draggedBookmarkGroup = null;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {
        final SortableGroup group = ItemPanels.bookmarkPanel.sortableGroup;
        if ((gui instanceof GuiStorageBus || gui instanceof GuiCellWorkbench) && group != null
                && this.draggedBookmarkGroup == null) {
            this.draggedBookmarkGroup = group.getBookmarkItems().stream().map(BookmarkItem::getItemStack)
                    .collect(Collectors.toList());
        }
    }
}
