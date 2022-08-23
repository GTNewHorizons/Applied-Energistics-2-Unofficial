package appeng.integration.modules.NEIHelpers;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.IDropToFillTextField;
import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.regex.Pattern;

public class NEIGuiHandler extends INEIGuiAdapter
{

    protected static final Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button)
    {

        if (draggedStack != null && draggedStack.getItem() != null && gui instanceof IDropToFillTextField) {
            IDropToFillTextField gmm = (IDropToFillTextField)gui;

            if (gmm.isOverTextField(mousex, mousey)) {
                gmm.setTextFieldValue(formattingText(draggedStack.getDisplayName()), mousex, mousey, draggedStack.copy());
                return true;
            }

        }

        return super.handleDragNDrop(gui, mousex, mousey, draggedStack, button);
    }

    @Override
    public boolean hideItemPanelSlot( GuiContainer gui, int x, int y, int w, int h )
    {
        if (gui instanceof GuiCraftingStatus) {
            return ((GuiCraftingStatus) gui).hideItemPanelSlot(x, y, w, h);
        } else if (gui instanceof GuiCraftConfirm ) {
            return ((GuiCraftConfirm) gui).hideItemPanelSlot(x, y, w, h);
        } else if (gui instanceof GuiMEMonitorable) {
            return ((GuiMEMonitorable) gui).hideItemPanelSlot(x, y, w, h);
        }

        return false;
    }

    private String formattingText(final String displayName)
    {
        return SPECIAL_REGEX_CHARS.matcher(EnumChatFormatting.getTextWithoutFormattingCodes(displayName)).replaceAll("\\\\$0");
    }

}
