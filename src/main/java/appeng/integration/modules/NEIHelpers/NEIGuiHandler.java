package appeng.integration.modules.NEIHelpers;

import appeng.client.gui.implementations.GuiMEMonitorable;
import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import java.util.regex.Pattern;

public class NEIGuiHandler extends INEIGuiAdapter {

    protected Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button) {
        if (gui instanceof GuiMEMonitorable && draggedStack != null && draggedStack.getItem() != null)
        {
            GuiMEMonitorable gmm = (GuiMEMonitorable)gui;
            if (gmm.isOverSearchField(mousex, mousey))
            {
                gmm.setSearchString(SPECIAL_REGEX_CHARS.matcher(draggedStack.getDisplayName()).replaceAll("\\\\$0"), true);
                return true;
            }
        }
        return super.handleDragNDrop(gui, mousex, mousey, draggedStack, button);
    }
}
