package appeng.client.gui;

import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Interface for handling tooltips from NEIGuiContainerManager.
 */
public interface IGuiTooltipHandler {

    default List<String> handleItemTooltip(final ItemStack stack, final int mouseX, final int mouseY,
            final List<String> currentToolTip) {
        return currentToolTip;
    }

    default ItemStack getHoveredStack() {
        return null;
    }
}
