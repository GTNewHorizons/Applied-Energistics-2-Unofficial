package appeng.container.implementations;

import static appeng.parts.reporting.PartPatternTerminalEx.*;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

public class ContainerPatternTermEx extends ContainerPatternTerm {

    @GuiSync(96 + (17 - 9) + 16)
    public boolean inverted;

    @GuiSync(96 + (17 - 9) + 17)
    public int activePage = 0;

    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);
        inverted = getExPatternTerminal().isInverted();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            final IPatternTerminalEx temp = getExPatternTerminal();
            substitute = temp.isSubstitution();
            beSubstitute = temp.canBeSubstitution();
            inverted = temp.isInverted();
            activePage = temp.getActivePage();
        }
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
