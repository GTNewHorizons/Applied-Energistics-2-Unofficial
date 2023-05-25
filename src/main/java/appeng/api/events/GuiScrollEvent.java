package appeng.api.events;

import appeng.client.gui.AEBaseGui;
import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

/**
 * Posted to the {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} when mouse wheel scrolls over AE2 windows.
 *
 * Client-only.
 */
@Cancelable
public class GuiScrollEvent extends Event {

    public final AEBaseGui guiScreen;
    public final int mouseX, mouseY;
    public final int scrollAmount;

    public GuiScrollEvent(AEBaseGui guiScreen, int mouseX, int mouseY, int scrollAmount) {
        this.guiScreen = guiScreen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.scrollAmount = scrollAmount;
    }
}
