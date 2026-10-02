package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.implementations.GuiOreFilter;
import appeng.container.ContainerSubGui;
import appeng.container.guisync.GuiSync;
import appeng.helpers.IOreFilterable;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerOreFilter extends ContainerSubGui {

    private final IOreFilterable filterHost;

    @SideOnly(Side.CLIENT)
    private GuiOreFilter gui;

    @GuiSync(2)
    public String filter = "";

    public ContainerOreFilter(final InventoryPlayer ip, final IOreFilterable te) {
        super(ip, te);
        this.filterHost = te;
    }

    @SideOnly(Side.CLIENT)
    public void setGui(final GuiOreFilter gui) {
        this.gui = gui;
    }

    public void setFilter(final String newValue) {
        this.filterHost.setFilter(newValue);
        this.filter = newValue;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) this.filter = this.filterHost.getFilter();
        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("filter") && this.gui != null) this.gui.setFilterText(filter);

        super.onUpdate(field, oldValue, newValue);
    }
}
