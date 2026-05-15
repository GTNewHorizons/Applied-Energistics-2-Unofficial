package appeng.client.gui.implementations;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerStorageBus;
import appeng.container.implementations.ContainerSuperMEReplenisher;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileSuperMEReplenisher;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class GuiSuperMEReplenisher extends AEBaseGui {
    private VirtualMEPatternSlot[] configSlots;
    private final ContainerSuperMEReplenisher containerSuperMEReplenisher;

    public GuiSuperMEReplenisher(final InventoryPlayer inventoryPlayer, final TileSuperMEReplenisher te) {
        super(new ContainerSuperMEReplenisher(inventoryPlayer, te));
        this.containerSuperMEReplenisher = (ContainerSuperMEReplenisher) inventorySlots;
        this.ySize = 256;
        this.xSize = 256;
    }

    @Override
    public void initGui() {
        super.initGui();
        initVirtualSlots();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {}

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/superMEReplenisher.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 256, this.ySize);
    }

    private void initVirtualSlots() {
        this.configSlots = new VirtualMEPatternSlot[11 * 9];
        final IAEStackInventory inputInv = this.containerSuperMEReplenisher.getConfig();
        final int xo = 30;
        final int yo = -190;

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 11; x++) {
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        xo + x * 18,
                        yo + y * 18 + 11 * 18,
                        inputInv,
                        x + y * 11,
                        this::acceptType);
                this.configSlots[x + y * 11] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return true;
    }
}
