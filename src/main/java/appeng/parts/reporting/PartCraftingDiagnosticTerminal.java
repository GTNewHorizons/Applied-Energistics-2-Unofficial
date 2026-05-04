package appeng.parts.reporting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.DiagnosticSortButton;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;

public class PartCraftingDiagnosticTerminal extends AbstractPartTerminal {

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartCraftingDiagnosticTerm_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartCraftingDiagnosticTerm_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartCraftingDiagnosticTerm_Colored;

    @Reflected
    public PartCraftingDiagnosticTerminal(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.DIAGNOSTIC_SORT_BY, DiagnosticSortButton.TIME);
        this.getConfigManager().putSetting(Settings.SORT_DIRECTION, SortDir.DESCENDING);
    }

    @Override
    public GuiBridge getGui(final EntityPlayer player) {
        int x = (int) player.posX;
        int y = (int) player.posY;
        int z = (int) player.posZ;
        if (this.getHost().getTile() != null) {
            x = this.getTile().xCoord;
            y = this.getTile().yCoord;
            z = this.getTile().zCoord;
        }

        if (GuiBridge.GUI_CRAFTING_DIAGNOSTIC_TERMINAL
                .hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), player)) {
            return GuiBridge.GUI_CRAFTING_DIAGNOSTIC_TERMINAL;
        }

        return GuiBridge.GUI_ME;
    }

    @Override
    public CableBusTextures getFrontBright() {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return FRONT_DARK_ICON;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().parts().craftingDiagnosticTerminal().maybeStack(1).orNull();
    }
}
