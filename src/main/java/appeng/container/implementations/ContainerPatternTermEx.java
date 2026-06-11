package appeng.container.implementations;

import static appeng.parts.reporting.PartPatternTerminalEx.*;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiPatternTermEx;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.util.Platform;

public class ContainerPatternTermEx extends ContainerPatternTerm {

    public BooleanSyncHandler invertedSync;
    public IntSyncHandler activePageSync;

    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);

        SyncRegistrar sync = this.syncRegistrar();
        this.invertedSync = sync.booleanSync("inverted")
                .onServerChange((oldValue, newValue) -> this.getExPatternTerminal().setInverted(newValue))
                .onClientChange((oldValue, newValue) -> {
                    if (oldValue != newValue
                            && Minecraft.getMinecraft().currentScreen instanceof GuiPatternTermEx gui) {
                        gui.onUpdateInvertedOrActivePage();
                    }
                });
        this.activePageSync = sync.intSync("activePage")
                .onServerChange((oldValue, newValue) -> this.getExPatternTerminal().setActivePage(newValue))
                .onClientChange((oldValue, newValue) -> {
                    if (oldValue != newValue
                            && Minecraft.getMinecraft().currentScreen instanceof GuiPatternTermEx gui) {
                        gui.onUpdateInvertedOrActivePage();
                    }
                });

        if (Platform.isServer()) {
            this.invertedSync.set(getExPatternTerminal().isInverted());
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            final IPatternTerminalEx temp = getExPatternTerminal();
            this.invertedSync.set(temp.isInverted());
            this.activePageSync.set(temp.getActivePage());
        }

        super.detectAndSendChanges();
    }

    public IPatternTerminalEx getExPatternTerminal() {
        return (IPatternTerminalEx) getPatternTerminal();
    }

    @Override
    public int getPatternInputsWidth() {
        return exPatternInputsWidth;
    }

    @Override
    public int getPatternInputsHeigh() {
        return exPatternInputsHeigh;
    }

    @Override
    public int getPatternInputPages() {
        return exPatternInputsPages;
    }

    @Override
    public int getPatternOutputsWidth() {
        return exPatternOutputsWidth;
    }

    @Override
    public int getPatternOutputsHeigh() {
        return exPatternOutputsHeigh;
    }

    @Override
    public int getPatternOutputPages() {
        return exPatternOutputPages;
    }
}
