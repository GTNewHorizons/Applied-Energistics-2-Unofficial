package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerCrystalGrowthChamber;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileCrystalGrowthChamber;

public class GuiCrystalGrowthChamber extends AEBaseGui {

    private final ContainerCrystalGrowthChamber cg;

    public GuiCrystalGrowthChamber(final InventoryPlayer inventoryPlayer, final TileCrystalGrowthChamber te) {
        super(new ContainerCrystalGrowthChamber(inventoryPlayer, te));
        cg = (ContainerCrystalGrowthChamber) this.inventorySlots;
        ySize = 166;
        xSize = hasToolbox() ? 246 : 211;
    }

    private boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        fontRendererObj.drawString(
                getGuiDisplayName(
                        StatCollector.translateToLocal("tile.appliedenergistics2.BlockCrystalGrowthChamber.name")),
                8,
                6,
                GuiColors.ChestTitle.getColor());
        fontRendererObj
                .drawString(GuiText.inventory.getLocal(), 8, ySize - 96 + 3, GuiColors.ChestInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        bindTexture("guis/crystalGrowthChamber.png");

        drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, ySize);

        if (drawUpgrades()) {
            drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + cg.availableUpgrades() * 18);
        }
        if (hasToolbox()) {
            drawTexturedModalRect(offsetX + 178, offsetY + ySize - 90, 178, ySize - 90, 68, 68);
        }
    }

    private boolean drawUpgrades() {
        return true;
    }
}
