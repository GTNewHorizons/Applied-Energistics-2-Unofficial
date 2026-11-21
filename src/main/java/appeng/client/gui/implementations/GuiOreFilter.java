package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.client.gui.GuiSub;
import appeng.client.gui.widgets.IDropToFillTextField;
import appeng.client.gui.widgets.MEGuiMultilineTextField;
import appeng.container.implementations.ContainerOreFilter;
import appeng.core.AELog;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IOreFilterable;
import appeng.integration.modules.NEI;
import appeng.util.prioitylist.OreFilteredList.OreFilterTextFormatter;

public class GuiOreFilter extends GuiSub implements IDropToFillTextField {

    private MEGuiMultilineTextField textField;

    private boolean useNEIFilter = false;
    private long lastclicktime;

    public GuiOreFilter(InventoryPlayer ip, IOreFilterable obj) {
        super(new ContainerOreFilter(ip, obj));
        this.xSize = 256;
        this.ySize = 180;

        this.textField = new MEGuiMultilineTextField(231, 126) {

            @Override
            public void onTextChange(final String oldText) {
                final String text = getText();

                if (!text.equals(oldText)) {
                    ((ContainerOreFilter) inventorySlots).setFilter(text);
                    if (GuiOreFilter.this.useNEIFilter) {
                        NEI.searchField.updateFilter();
                    }
                }
            }
        };

        if (NEI.searchField.existsSearchField()) {
            this.textField.setFormatter(new OreFilterTextFormatter());
        }
    }

    @Override
    public void initGui() {
        this.ySize = Math.max(72, Math.min(180, this.height - 24));
        super.initGui();

        this.textField.x = this.guiLeft + 12;
        this.textField.y = this.guiTop + 35;
        this.textField.h = this.ySize - 54;
        this.textField.setFocused(true);

        ((ContainerOreFilter) this.inventorySlots).setTextField(this.textField);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (this.useNEIFilter) {
            this.useNEIFilter = false;
            NEI.searchField.updateFilter();
        }
    }

    public String getText() {
        return this.textField.getText();
    }

    public boolean useNEIFilter() {
        return this.useNEIFilter;
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj
                .drawString(GuiText.OreFilterLabel.getLocal(), 12, 8, ColorUtils.guiTextColorGray.getColor());

        String counterText = String.valueOf(textField.getText().length()) + "/"
                + String.valueOf(textField.getMaxStringLength());
        int counterColor = textField.getText().length() == textField.getMaxStringLength()
                ? ColorUtils.oreFilterTextLengthFull.getColor()
                : ColorUtils.guiTextColorGray.getColor();
        this.fontRendererObj
                .drawString(counterText, xSize - fontRendererObj.getStringWidth(counterText) - 13, 25, counterColor);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/renamer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 34);
        GL11.glPushMatrix();
        GL11.glTranslatef(offsetX, offsetY + 34, 0);
        GL11.glScalef(1, this.ySize - 47, 1);
        this.drawTexturedModalRect(0, 0, 0, 33, this.xSize, 1);
        GL11.glPopMatrix();
        this.drawTexturedModalRect(offsetX, offsetY + this.ySize - 13, 0, 47, this.xSize, 13);
        this.textField.drawTextBox();

        if (this.useNEIFilter) {
            drawRect(textField.x - 1, textField.y - 1, textField.x + textField.w, textField.y, 0xFFFFFF00); // Top

            drawRect(
                    textField.x - 1,
                    textField.y + textField.h - 1,
                    textField.x + textField.w,
                    textField.y + textField.h,
                    0xFFFFFF00); // Bottom

            drawRect(textField.x - 1, textField.y, textField.x, textField.y + textField.h - 1, 0xFFFFFF00); // Left

            drawRect(
                    textField.x + textField.w - 1,
                    textField.y,
                    textField.x + textField.w,
                    textField.y + textField.h - 1,
                    0xFFFFFF00); // Right
        }
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        if (this.textField.isMouseIn(mouseX, mouseY)) {
            this.textField.scroll(wheel);
            return true;
        }
        return super.mouseWheelEvent(mouseX, mouseY, wheel);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {

        if (btn == 0 && NEI.searchField.existsSearchField() && textField.isMouseIn(xCoord, yCoord)) {
            if (textField.isFocused() && (System.currentTimeMillis() - lastclicktime < 400)) { // double click
                useNEIFilter = !useNEIFilter;
                NEI.searchField.updateFilter();
            }
            lastclicktime = System.currentTimeMillis();
        }

        this.textField.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsedTime) {
        if (button == 0 && this.textField.isFocused()) this.textField.mouseDragged(mouseX, mouseY);
        super.mouseClickMove(mouseX, mouseY, button, elapsedTime);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && !GuiScreen.isShiftKeyDown()) {
            try {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("OreFilter", this.textField.getText()));
            } catch (IOException e) {
                AELog.debug(e);
            }

            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());

        } else if (!this.textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    public boolean isOverTextField(final int mousex, final int mousey) {
        return textField.isMouseIn(mousex, mousey);
    }

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack) {
        final int[] ores = OreDictionary.getOreIDs(stack);

        if (ores.length > 0) {

            // left click
            if (Mouse.isButtonDown(0)) {
                // If had something in text field before
                // add a ` | ` automatically
                String oldText = textField.getText();
                if (!oldText.isEmpty()) {
                    oldText = oldText + " | ";
                }

                textField.setText(oldText + OreDictionary.getOreName(ores[0]));

            } else if (Mouse.isButtonDown(1)) {
                // right click
                textField.setText(OreDictionary.getOreName(ores[0]));
            }

            // Move the cursor to end
            textField.setCursorPositionEnd();
        }
    }
}
