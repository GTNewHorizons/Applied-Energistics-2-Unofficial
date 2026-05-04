package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiProgressBar;
import appeng.client.gui.widgets.GuiProgressBar.Direction;
import appeng.client.gui.widgets.ITooltip;
import appeng.container.implementations.ContainerAdvancedInscriber;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.misc.TileAdvancedInscriber;

public class GuiAdvancedInscriber extends AEBaseGui {

    private final ContainerAdvancedInscriber container;
    private GuiProgressBar progressBar;
    private LockButton topLock;
    private LockButton bottomLock;

    public GuiAdvancedInscriber(final InventoryPlayer inventoryPlayer, final TileAdvancedInscriber te) {
        super(new ContainerAdvancedInscriber(inventoryPlayer, te));
        this.container = (ContainerAdvancedInscriber) this.inventorySlots;
        this.ySize = 176;
        this.xSize = this.hasToolbox() ? 246 : 211;
    }

    private boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    @Override
    public void initGui() {
        super.initGui();

        this.progressBar = new GuiProgressBar(
                this.container,
                "guis/inscriber.png",
                135,
                39,
                135,
                177,
                6,
                18,
                Direction.VERTICAL);
        this.buttonList.add(this.progressBar);

        this.topLock = new LockButton(0, this.guiLeft + 34, this.guiTop + 20, "top");
        this.bottomLock = new LockButton(1, this.guiLeft + 34, this.guiTop + 66, "bottom");
        this.buttonList.add(this.topLock);
        this.buttonList.add(this.bottomLock);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        final int maxProgress = Math.max(1, this.container.getMaxProgress());
        this.progressBar.setFullMsg(this.container.getCurrentProgress() * 100 / maxProgress + "%");

        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.AdvancedInscriber.getLocal()),
                8,
                6,
                GuiColors.InscriberTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.InscriberInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/inscriber.png");
        this.progressBar.xPosition = 135 + this.guiLeft;
        this.progressBar.yPosition = 39 + this.guiTop;

        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 176, this.ySize);
        this.bindTexture("guis/mac.png");
        this.drawTexturedModalRect(offsetX + 179, offsetY, 179, 0, 32, 104);

        if (this.hasToolbox()) {
            this.bindTexture("guis/inscriber.png");
            this.drawTexturedModalRect(offsetX + 178, offsetY + 105, 178, this.ySize - 90, 68, 68);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.topLock) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("AdvancedInscriber.Lock", "top:" + !this.container.topLocked));
            } else if (btn == this.bottomLock) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("AdvancedInscriber.Lock", "bottom:" + !this.container.bottomLocked));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private final class LockButton extends GuiButton implements ITooltip {

        private final String slot;
        private final ResourceLocation lockedTexture = new ResourceLocation(
                "appliedenergistics2",
                "textures/guis/advanced_inscriber_lock_on.png");
        private final ResourceLocation unlockedTexture = new ResourceLocation(
                "appliedenergistics2",
                "textures/guis/advanced_inscriber_lock_off.png");

        private LockButton(final int id, final int x, final int y, final String slot) {
            super(id, x, y, 8, 8, "");
            this.slot = slot;
        }

        @Override
        public void drawButton(final Minecraft minecraft, final int mouseX, final int mouseY) {
            if (!this.visible) {
                return;
            }

            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            minecraft.renderEngine.bindTexture(this.isLocked() ? this.lockedTexture : this.unlockedTexture);
            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;
            this.drawFullTexture(this.xPosition, this.yPosition, this.width, this.height);
        }

        private void drawFullTexture(final int x, final int y, final int width, final int height) {
            final Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, this.zLevel, 0, 1);
            tessellator.addVertexWithUV(x + width, y + height, this.zLevel, 1, 1);
            tessellator.addVertexWithUV(x + width, y, this.zLevel, 1, 0);
            tessellator.addVertexWithUV(x, y, this.zLevel, 0, 0);
            tessellator.draw();
        }

        private boolean isLocked() {
            return this.slot.equals("top") ? GuiAdvancedInscriber.this.container.topLocked
                    : GuiAdvancedInscriber.this.container.bottomLocked;
        }

        @Override
        public String getMessage() {
            if (this.isLocked()) {
                return GuiText.AdvancedInscriberLockOn.getLocal();
            }

            return GuiText.AdvancedInscriberLockOff.getLocal() + '\n' + GuiText.AdvancedInscriberLockNote.getLocal();
        }

        @Override
        public int xPos() {
            return this.xPosition;
        }

        @Override
        public int yPos() {
            return this.yPosition;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public boolean isVisible() {
            return this.visible;
        }
    }
}
