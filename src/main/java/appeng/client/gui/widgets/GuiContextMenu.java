package appeng.client.gui.widgets;

import static net.minecraft.client.gui.Gui.drawRect;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import appeng.api.util.AEColor;

public abstract class GuiContextMenu {

    int x = 0;
    int y = 0;
    int width = 0;
    int visibleSections;
    int scrollOffset = 0;
    boolean isActive = false;
    protected List<?> list = new ArrayList<>();
    FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

    public GuiContextMenu(int visibleRows) {
        this.visibleSections = visibleRows;
    }

    public abstract void action(int listItemIndex);

    public abstract String getDrawText(int listItemIndex);

    public boolean isActive() {
        return isActive;
    }

    public void init(List<?> list, int x, int y) {
        this.list = list;
        this.x = x;
        this.y = y;
        width = getMaxWidth();
        isActive = true;
    }

    private int getMaxWidth() {
        int width = 0;
        for (int i = 0; i < list.size(); i++) {
            int newWidth = fontRenderer.getStringWidth(getDrawText(i));
            if (newWidth > width) {
                width = newWidth;
            }
        }
        return width + 3;
    }

    public void clear() {
        x = 0;
        y = 0;
        isActive = false;
        scrollOffset = 0;
        list.clear();
    }

    public boolean mouseClick(int x, int y, int button) {
        if (isActive) {
            if (isMouseOn(x, y)) {
                for (int i = 0; i < visibleSections && i < list.size(); i++) {
                    if (y < this.y + SECTION_HEIGHT + (SECTION_HEIGHT * i)) {
                        action(i + scrollOffset);
                        return true;
                    }
                }
            } else {
                clear();
            }
        }
        return false;
    }

    public boolean mouseWheelEvent(int x, int y, int wheel) {
        if (isMouseOn(x, y)) {
            if (wheel > 0 && scrollOffset > 0) {
                scrollOffset--;
            } else if (wheel < 0 && scrollOffset < list.size() - visibleSections) {
                scrollOffset++;
            }
            return true;
        }
        return false;
    }

    public boolean isMouseOn(int x, int y) {
        return x >= this.x && x < this.x + width && y >= this.y && y < this.y + (SECTION_HEIGHT * visibleSections);
    }

    private final int SECTION_HEIGHT = 12;

    public void draw(int mouseX, int mouseY) {
        if (!isActive) {
            return;
        }
        int j = 0;
        for (int i = scrollOffset; j < visibleSections && i < list.size(); i++) {
            int yPos = y + (SECTION_HEIGHT * j);
            int xOff = x + width;
            int color = AEColor.LightGray.mediumVariant - 16777216;

            if (mouseX >= x && mouseX < xOff && mouseY >= yPos && mouseY < yPos + SECTION_HEIGHT) {
                color = AEColor.Gray.mediumVariant - 16777216;
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(0.0f, 0.0f, 1000f);
            drawRect(x, yPos, xOff, yPos + SECTION_HEIGHT, color);

            fontRenderer.drawString(getDrawText(i), x + 2, yPos + 2, 0x404040);

            drawRect(x, yPos, xOff, yPos + 1, 0xFF404040);
            drawRect(x, yPos + SECTION_HEIGHT - 1, xOff, yPos + SECTION_HEIGHT, 0xFF404040);
            drawRect(x, yPos, x + 1, yPos + SECTION_HEIGHT, 0xFF404040);
            drawRect(xOff - 1, y + (SECTION_HEIGHT * j), xOff, yPos + SECTION_HEIGHT, 0xFF404040);
            GL11.glTranslatef(0.0f, 0.0f, 0f);

            GL11.glPopMatrix();
            j++;

        }
    }
}
