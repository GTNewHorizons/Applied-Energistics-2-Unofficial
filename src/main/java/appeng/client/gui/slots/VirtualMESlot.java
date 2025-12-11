package appeng.client.gui.slots;

import static appeng.integration.modules.NEI.searchField;
import static codechicken.nei.NEIClientConfig.getSearchExpression;
import static net.minecraft.client.gui.Gui.drawRect;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.IAEStack;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.ItemFilter;

public abstract class VirtualMESlot {

    private int xPos;
    private int yPos;
    private boolean isHidden = false;

    protected final int slotIndex;

    protected boolean showAmount = true;
    protected boolean showAmountAlways = false;
    protected boolean showCraftableText = false;
    protected boolean showCraftableIcon = false;

    public VirtualMESlot(int x, int y, int slotIndex) {
        this.xPos = x;
        this.yPos = y;
        this.slotIndex = slotIndex;
    }

    public int getX() {
        return this.xPos;
    }

    public int getY() {
        return this.yPos;
    }

    public void setX(int x) {
        this.xPos = x;
    }

    public void setY(int y) {
        this.yPos = y;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    @Nullable
    public abstract IAEStack<?> getAEStack();

    public boolean isHovered(int mouseX, int mouseY) {
        return !this.isHidden && mouseX >= this.xPos
                && mouseX < this.xPos + 18
                && mouseY >= this.yPos
                && mouseY < this.yPos + 18;
    }

    /**
     * @return ture if hovered
     */
    public boolean drawStackAndOverlay(Minecraft mc, int mouseX, int mouseY) {
        if (this.isHidden) return false;

        IAEStack<?> aes = this.getAEStack();
        if (aes != null) {
            aes.drawInGui(mc, this.xPos, this.yPos);
            aes.drawOverlayInGui(
                    mc,
                    this.xPos,
                    this.yPos,
                    this.showAmount,
                    this.showAmountAlways,
                    this.showCraftableText,
                    this.showCraftableIcon);
        }

        this.drawNEIOverlay();

        final boolean hovered = this.isHovered(mouseX, mouseY);
        if (hovered) {
            this.drawHoveredOverlay();
        }
        return hovered;
    }

    protected void drawHoveredOverlay() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(true, true, true, false);
        drawRect(this.xPos, this.yPos, this.xPos + 16, this.yPos + 16, -2130706433);
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    protected void drawNEIOverlay() {
        if (searchField.isSearchingInventory()) {
            ItemFilter itemFilter = searchField.getItemFilter();
            if (itemFilter == null) return;

            IAEStack<?> aes = this.getAEStack();
            ItemStack item = aes != null ? aes.getItemStackForNEI() : null;
            if (aes == null || item == null ? !getSearchExpression().isEmpty() : !itemFilter.matches(item)) {
                NEIClientUtils.gl2DRenderContext(() -> {
                    GL11.glTranslatef(0, 0, 150);
                    drawRect(this.xPos, this.yPos, this.xPos + 16, this.yPos + 16, 0x80000000);
                    GL11.glTranslatef(0, 0, -150);
                });
            }
        }
    }

    public boolean isHidden() {
        return this.isHidden;
    }

    public void setHidden(boolean hidden) {
        this.isHidden = hidden;
    }

    public void setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
    }

    public void setShowAmountAlways(boolean showAmountAlways) {
        this.showAmountAlways = showAmountAlways;
    }

    public void setShowCraftableText(boolean showCraftableText) {
        this.showCraftableText = showCraftableText;
    }

    public void setShowCraftableIcon(boolean showCraftableIcon) {
        this.showCraftableIcon = showCraftableIcon;
    }
}
