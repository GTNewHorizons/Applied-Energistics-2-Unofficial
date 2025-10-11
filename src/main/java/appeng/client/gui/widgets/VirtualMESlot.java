package appeng.client.gui.widgets;

import static net.minecraft.client.gui.Gui.drawRect;

import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;

public class VirtualMESlot {

    private final int xPos;
    private final int yPos;

    protected final IDisplayRepo repo;
    protected final int slotIndex;

    public VirtualMESlot(int x, int y, IDisplayRepo repo, int slotIndex) {
        this.xPos = x;
        this.yPos = y;
        this.repo = repo;
        this.slotIndex = slotIndex;
    }

    public int getX() {
        return this.xPos;
    }

    public int getY() {
        return this.yPos;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    @Nullable
    public IAEStack<?> getAEStack() {
        return this.repo.getReferenceStack(this.slotIndex);
    }

    public void drawHoveredOverlay() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColorMask(true, true, true, false);
        drawRect(this.xPos, this.yPos, this.xPos + 16, this.yPos + 16, -2130706433);
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
