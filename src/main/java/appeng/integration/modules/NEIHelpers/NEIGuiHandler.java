package appeng.integration.modules.NEIHelpers;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.IDropToFillTextField;
import codechicken.nei.api.INEIGuiAdapter;
import codechicken.nei.recipe.StackInfo;

public class NEIGuiHandler extends INEIGuiAdapter {

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {

        if (draggedStack != null && draggedStack.getItem() != null
                && (gui instanceof IDropToFillTextField gmm)
                && gmm.isOverTextField(mousex, mousey)) {
            gmm.setTextFieldValue(getItemStackText(draggedStack), mousex, mousey, draggedStack.copy());

            return true;
        }

        return super.handleDragNDrop(gui, mousex, mousey, draggedStack, button);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (gui instanceof GuiCraftingStatus) {
            return ((GuiCraftingStatus) gui).hideItemPanelSlot(x, y, w, h);
        } else if (gui instanceof GuiCraftConfirm) {
            return ((GuiCraftConfirm) gui).hideItemPanelSlot(x, y, w, h);
        } else if (gui instanceof GuiMEMonitorable) {
            return ((GuiMEMonitorable) gui).hideItemPanelSlot(x, y, w, h);
        }

        return false;
    }

    protected static String getItemStackText(ItemStack stack) {
        final FluidStack fluidStack = StackInfo.isFluidContainer(stack) ? null : StackInfo.getFluid(stack);
        String displayName;

        if (fluidStack != null) {
            displayName = fluidStack.getLocalizedName();
        } else {
            displayName = stack.getDisplayName();
        }

        return EnumChatFormatting.getTextWithoutFormattingCodes(displayName);
    }
}
