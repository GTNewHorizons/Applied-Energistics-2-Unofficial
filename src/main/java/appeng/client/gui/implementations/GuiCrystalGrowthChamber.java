package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerCrystalGrowthChamber;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileCrystalGrowthChamber;

public class GuiCrystalGrowthChamber extends AEBaseGui {

    private final ContainerCrystalGrowthChamber cg;

    public GuiCrystalGrowthChamber(final InventoryPlayer inventoryPlayer, final TileCrystalGrowthChamber te) {
        super(new ContainerCrystalGrowthChamber(inventoryPlayer, te));
        cg = (ContainerCrystalGrowthChamber) this.inventorySlots;
        ySize = 166;
        xSize = !hasToolbox() ? 211 : getToolboxSize() == 5 ? 290 : 246;
    }

    private boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    private int getToolboxSize() {
        return ((ContainerUpgradeable) this.inventorySlots).getToolboxSize();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        fontRendererObj.drawString(
                getGuiDisplayName(
                        StatCollector.translateToLocal("tile.appliedenergistics2.BlockCrystalGrowthChamber.name")),
                8,
                6,
                ColorUtils.guiTextColorGray.getColor());
        fontRendererObj
                .drawString(GuiText.inventory.getLocal(), 8, ySize - 96 + 3, ColorUtils.guiTextColorGray.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        bindTexture("guis/crystalGrowthChamber.png");

        drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, ySize);

        if (drawUpgrades()) {
            drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + cg.availableUpgrades() * 18);
        }
        if (hasToolbox() && getToolboxSize() == 5) {
            bindTexture("guis/advanced_toolbox.png");
            drawTexturedModalRect(offsetX + 178, offsetY + ySize - 90 - 7, 0, 0, 104, 104);
        } else if (hasToolbox()) {
            drawTexturedModalRect(offsetX + 178, offsetY + ySize - 90, 178, ySize - 90, 68, 68);
        }
    }

    private boolean drawUpgrades() {
        return true;
    }
}
