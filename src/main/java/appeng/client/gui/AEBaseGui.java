/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;

import appeng.api.events.GuiScrollEvent;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotDisconnected;
import appeng.client.me.SlotME;
import appeng.client.render.AppEngRenderItem;
import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngCraftingSlot;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.AppEngSlot.hasCalculatedValidness;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotOutput;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketSwapSlots;
import appeng.helpers.InventoryAction;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.INEI;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ObfuscationReflectionHelper;

public abstract class AEBaseGui extends GuiContainer {

    private static boolean switchingGuis;
    private final List<InternalSlotME> meSlots = new LinkedList<>();
    // drag y
    private final Set<Slot> drag_click = new HashSet<>();
    private final AppEngRenderItem aeRenderItem = new AppEngRenderItem();
    private GuiScrollbar scrollBar = null;
    private boolean disableShiftClick = false;
    private Stopwatch dbl_clickTimer = Stopwatch.createStarted();
    private ItemStack dbl_whichItem;
    private Slot bl_clicked;
    private boolean subGui;

    public AEBaseGui(final Container container) {
        super(container);
        this.subGui = switchingGuis;
        switchingGuis = false;
    }

    protected static String join(final Collection<String> toolTip, final String delimiter) {
        final Joiner joiner = Joiner.on(delimiter);

        return joiner.join(toolTip);
    }

    protected int getQty(final GuiButton btn) {
        try {
            final DecimalFormat df = new DecimalFormat("+#;-#");
            return df.parse(btn.displayString).intValue();
        } catch (final ParseException e) {
            return 0;
        }
    }

    public boolean isSubGui() {
        return this.subGui;
    }

    @Override
    public void initGui() {
        super.initGui();

        final List<Slot> slots = this.getInventorySlots();
        slots.removeIf(slot -> slot instanceof SlotME);

        for (final InternalSlotME me : this.meSlots) {
            slots.add(new SlotME(me));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Slot> getInventorySlots() {
        return this.inventorySlots.inventorySlots;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        super.drawScreen(mouseX, mouseY, btn);

        for (final Object c : this.buttonList) {
            if (c instanceof ITooltip) {
                handleTooltip(mouseX, mouseY, (ITooltip) c);
            }
        }
    }

    protected void handleTooltip(int mouseX, int mouseY, ITooltip tooltip) {
        final int x = tooltip.xPos(); // ((GuiImgButton) c).xPosition;
        int y = tooltip.yPos(); // ((GuiImgButton) c).yPosition;

        if (x < mouseX && x + tooltip.getWidth() > mouseX && tooltip.isVisible()) {
            if (y < mouseY && y + tooltip.getHeight() > mouseY) {
                if (y < 15) {
                    y = 15;
                }

                final String msg = tooltip.getMessage();
                if (msg != null && !"".equals(msg)) {
                    this.drawTooltip(x + 11, y + 4, 0, msg);
                }
            }
        }
    }

    public void drawTooltip(final int par2, final int par3, final int forceWidth, final String message) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        final String[] var4 = message.split("\n");

        if (var4.length > 0) {
            int var5 = 0;
            int var6;
            int var7;

            for (var6 = 0; var6 < var4.length; ++var6) {
                var7 = this.fontRendererObj.getStringWidth(var4[var6]);

                if (var7 > var5) {
                    var5 = var7;
                }
            }

            var6 = par2 + 12;
            var7 = par3 - 12;
            int var9 = 8;

            if (var4.length > 1) {
                var9 += 2 + (var4.length - 1) * 10;
            }

            if (this.guiTop + var7 + var9 + 6 > this.height) {
                var7 = this.height - var9 - this.guiTop - 6;
            }

            if (forceWidth > 0) {
                var5 = forceWidth;
            }

            this.zLevel = 300.0F;
            itemRender.zLevel = 300.0F;
            final int var10 = -267386864;
            this.drawGradientRect(var6 - 3, var7 - 4, var6 + var5 + 3, var7 - 3, var10, var10);
            this.drawGradientRect(var6 - 3, var7 + var9 + 3, var6 + var5 + 3, var7 + var9 + 4, var10, var10);
            this.drawGradientRect(var6 - 3, var7 - 3, var6 + var5 + 3, var7 + var9 + 3, var10, var10);
            this.drawGradientRect(var6 - 4, var7 - 3, var6 - 3, var7 + var9 + 3, var10, var10);
            this.drawGradientRect(var6 + var5 + 3, var7 - 3, var6 + var5 + 4, var7 + var9 + 3, var10, var10);
            final int var11 = 1347420415;
            final int var12 = (var11 & 16711422) >> 1 | var11 & -16777216;
            this.drawGradientRect(var6 - 3, var7 - 3 + 1, var6 - 3 + 1, var7 + var9 + 3 - 1, var11, var12);
            this.drawGradientRect(var6 + var5 + 2, var7 - 3 + 1, var6 + var5 + 3, var7 + var9 + 3 - 1, var11, var12);
            this.drawGradientRect(var6 - 3, var7 - 3, var6 + var5 + 3, var7 - 3 + 1, var11, var11);
            this.drawGradientRect(var6 - 3, var7 + var9 + 2, var6 + var5 + 3, var7 + var9 + 3, var12, var12);

            for (int var13 = 0; var13 < var4.length; ++var13) {
                String var14 = var4[var13];

                if (var13 == 0) {
                    var14 = '\u00a7' + Integer.toHexString(15) + var14;
                } else {
                    var14 = "\u00a77" + var14;
                }

                this.fontRendererObj.drawStringWithShadow(var14, var6, var7, -1);

                if (var13 == 0) {
                    var7 += 2;
                }

                var7 += 10;
            }

            this.zLevel = 0.0F;
            itemRender.zLevel = 0.0F;
        }
        GL11.glPopAttrib();
    }

    /**
     * Utility to add the vertices of a rectangle to an active Tesselator tesselation. The rectangle is defined to be
     * between points (x0, y0)..(x1, y1) and have corresponding texture coordinates (u0, v0)..(u1, v1).
     */
    public void addTexturedRectToTesselator(float x0, float y0, float x1, float y1, float zLevel, float u0, float v0,
            float u1, float v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.addVertexWithUV(x0, y1, this.zLevel, u0, v1);
        tessellator.addVertexWithUV(x1, y1, this.zLevel, u1, v1);
        tessellator.addVertexWithUV(x1, y0, this.zLevel, u1, v0);
        tessellator.addVertexWithUV(x0, y0, this.zLevel, u0, v0);
    }

    /**
     * Like {@link net.minecraft.client.gui.Gui#drawTexturedModalRect(int, int, int, int, int, int)} but draws the
     * texture in 9 patches, stretching the middle patch in X&Y directions. The north, east, west and south patches are
     * stretched along one axis, and the corner patches are rendered in 1:1 scale to preserve corner texture quality. A
     * 256x256 GUI texture size is assumed like in the vanilla function.
     *
     * @see <a href="https://developer.android.com/develop/ui/views/graphics/drawables#nine-patch">Android documentation
     *      for a more detailed description.</a>
     * @param x        X coordinate of the drawn rectangle of the screen
     * @param y        Y coordinate of the drawn rectangle of the screen
     * @param width    Width of the drawn rectangle of the screen
     * @param height   Height of the drawn rectangle of the screen
     * @param textureX X coordinate of the top-left pixel in the texture
     * @param textureY Y coordinate of the top-left pixel in the texture
     * @param textureW Width of texture fragment to draw
     * @param textureH Height of texture fragment to draw
     */
    public void drawTextured9PatchRect(int x, int y, int width, int height, int textureX, int textureY, int textureW,
            int textureH) {
        final float uvScale = 1.0f / 256.0f;

        // On-screen thirds (use texture thirds as corner sizes)
        // 03 = 0/3, 13 = 1/3, etc.
        final float x03 = x;
        final float x13 = x + textureW / 3f;
        final float x23 = x + width - textureW / 3f;
        final float x33 = x + width;
        final float y03 = y;
        final float y13 = y + textureH / 3f;
        final float y23 = y + height - textureH / 3f;
        final float y33 = y + height;
        // Texture UV thirds (uniformly scaled 3x3 grid)
        final float u03 = uvScale * textureX;
        final float u13 = uvScale * (textureX + textureW / 3f);
        final float u23 = uvScale * (textureX + 2 * textureW / 3f);
        final float u33 = uvScale * (textureX + textureW);
        final float v03 = uvScale * textureY;
        final float v13 = uvScale * (textureY + textureH / 3f);
        final float v23 = uvScale * (textureY + 2 * textureH / 3f);
        final float v33 = uvScale * (textureY + textureH);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        // top row
        addTexturedRectToTesselator(x03, y03, x13, y13, this.zLevel, u03, v03, u13, v13); // top-left
        addTexturedRectToTesselator(x13, y03, x23, y13, this.zLevel, u13, v03, u23, v13); // top-middle
        addTexturedRectToTesselator(x23, y03, x33, y13, this.zLevel, u23, v03, u33, v13); // top-right
        // middle row
        addTexturedRectToTesselator(x03, y13, x13, y23, this.zLevel, u03, v13, u13, v23); // middle-left
        addTexturedRectToTesselator(x13, y13, x23, y23, this.zLevel, u13, v13, u23, v23); // middle-middle
        addTexturedRectToTesselator(x23, y13, x33, y23, this.zLevel, u23, v13, u33, v23); // middle-right
        // bottom row
        addTexturedRectToTesselator(x03, y23, x13, y33, this.zLevel, u03, v23, u13, v33); // bottom-left
        addTexturedRectToTesselator(x13, y23, x23, y33, this.zLevel, u13, v23, u23, v33); // bottom-middle
        addTexturedRectToTesselator(x23, y23, x33, y33, this.zLevel, u23, v23, u33, v33); // bottom-right

        tessellator.draw();
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(final int x, final int y) {
        final int ox = this.guiLeft; // (width - xSize) / 2;
        final int oy = this.guiTop; // (height - ySize) / 2;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if (this.getScrollBar() != null) {
            this.getScrollBar().draw(this);
        }

        this.drawFG(ox, oy, x, y);
    }

    public abstract void drawFG(int offsetX, int offsetY, int mouseX, int mouseY);

    @Override
    protected final void drawGuiContainerBackgroundLayer(final float f, final int x, final int y) {
        final int ox = this.guiLeft; // (width - xSize) / 2;
        final int oy = this.guiTop; // (height - ySize) / 2;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawBG(ox, oy, x, y);

        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            if (slot instanceof OptionalSlotFake fs) {
                if (fs.renderDisabled()) {
                    if (fs.isEnabled()) {
                        this.drawTexturedModalRect(
                                ox + fs.xDisplayPosition - 1,
                                oy + fs.yDisplayPosition - 1,
                                fs.getSourceX() - 1,
                                fs.getSourceY() - 1,
                                18,
                                18);
                    } else {
                        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.4F);
                        GL11.glEnable(GL11.GL_BLEND);
                        this.drawTexturedModalRect(
                                ox + fs.xDisplayPosition - 1,
                                oy + fs.yDisplayPosition - 1,
                                fs.getSourceX() - 1,
                                fs.getSourceY() - 1,
                                18,
                                18);
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                        GL11.glPopAttrib();
                    }
                }
            }
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.drag_click.clear();

        if (btn == 1) {
            for (final Object o : this.buttonList) {
                final GuiButton guibutton = (GuiButton) o;
                if (guibutton.mousePressed(this.mc, xCoord, yCoord)) {
                    super.mouseClicked(xCoord, yCoord, 0);
                    return;
                }
            }
        }

        if (this.getScrollBar() != null) {
            this.getScrollBar().click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        final Slot slot = this.getSlot(x, y);
        final ItemStack itemstack = this.mc.thePlayer.inventory.getItemStack();

        if (this.getScrollBar() != null) {
            this.getScrollBar().click(this, x - this.guiLeft, y - this.guiTop);
        }

        if (slot instanceof SlotFake && itemstack != null) {
            this.drag_click.add(slot);
            if (this.drag_click.size() > 1) {
                for (final Slot dr : this.drag_click) {
                    final PacketInventoryAction p = new PacketInventoryAction(
                            c == 0 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.PLACE_SINGLE,
                            dr.slotNumber,
                            0);
                    NetworkHandler.instance.sendToServer(p);
                }
            }
        } else if (slot instanceof SlotDisconnected) {
            this.drag_click.add(slot);
            if (this.drag_click.size() > 1) {
                if (itemstack != null) {
                    for (final Slot dr : this.drag_click) {
                        if (slot.getStack() == null) {
                            InventoryAction action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    action,
                                    dr.getSlotIndex(),
                                    ((SlotDisconnected) slot).getSlot().getId());
                            NetworkHandler.instance.sendToServer(p);
                        }
                    }
                } else if (isShiftKeyDown()) {
                    for (final Slot dr : this.drag_click) {
                        InventoryAction action = null;
                        if (slot.getStack() != null) {
                            action = InventoryAction.SHIFT_CLICK;
                        }
                        if (action != null) {
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    action,
                                    dr.getSlotIndex(),
                                    ((SlotDisconnected) slot).getSlot().getId());
                            NetworkHandler.instance.sendToServer(p);
                        }
                    }
                }
            }
        } else {
            super.mouseClickMove(x, y, c, d);
        }
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int ctrlDown, final int mouseButton) {
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        if (mouseButton == 3) {
            if (slot instanceof OptionalSlotFake || slot instanceof SlotFakeCraftingMatrix) {
                if (slot.getHasStack()) {
                    InventoryAction action = InventoryAction.SET_PATTERN_VALUE;
                    IAEItemStack stack = AEItemStack.create(slot.getStack());

                    ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                    final PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
                    NetworkHandler.instance.sendToServer(p);

                    return;
                }
            }

        } else {

            if (slot instanceof SlotFake) {
                final InventoryAction action = ctrlDown == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                        : InventoryAction.PICKUP_OR_SET_DOWN;

                if (this.drag_click.size() > 1) {
                    return;
                }

                final PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
                NetworkHandler.instance.sendToServer(p);

                return;
            }
        }

        if (slot instanceof SlotPatternTerm) {
            if (mouseButton == 6) {
                return; // prevent weird double clicks..
            }

            try {
                NetworkHandler.instance.sendToServer(((SlotPatternTerm) slot).getRequest(isShiftKeyDown()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (slot instanceof SlotCraftingTerm) {
            if (mouseButton == 6) {
                return; // prevent weird double clicks..
            }

            InventoryAction action = null;
            if (isShiftKeyDown()) {
                action = InventoryAction.CRAFT_SHIFT;
            } else {
                // Craft stack on right-click, craft single on left-click
                action = (mouseButton == 1) ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            }

            final PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
            NetworkHandler.instance.sendToServer(p);

            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (this.enableSpaceClicking() && !(slot instanceof SlotPatternTerm)) {
                IAEItemStack stack = null;
                if (slot instanceof SlotME) {
                    stack = ((SlotME) slot).getAEStack();
                }

                int slotNum = this.getInventorySlots().size();

                if (!(slot instanceof SlotME) && slot != null) {
                    slotNum = slot.slotNumber;
                }

                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.MOVE_REGION, slotNum, 0);
                NetworkHandler.instance.sendToServer(p);
                return;
            }
        }

        if (slot instanceof SlotDisconnected) {
            if (this.drag_click.size() > 1) {
                return;
            }

            InventoryAction action = null;

            switch (mouseButton) {
                case 0 -> // pickup / set-down.
                {
                    ItemStack heldStack = player.inventory.getItemStack();
                    if (slot.getStack() == null && heldStack != null) action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
                    else if (slot.getStack() != null && (heldStack == null || heldStack.stackSize <= 1))
                        action = InventoryAction.PICKUP_OR_SET_DOWN;
                }
                case 1 -> action = ctrlDown == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                case 3 -> { // creative dupe:
                    if (player.capabilities.isCreativeMode) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }
                } // drop item:
                default -> {}
            }

            if (action != null) {
                final PacketInventoryAction p = new PacketInventoryAction(
                        action,
                        slot.getSlotIndex(),
                        ((SlotDisconnected) slot).getSlot().getId());
                NetworkHandler.instance.sendToServer(p);
            }

            return;
        }

        if (slot instanceof SlotME) {
            InventoryAction action = null;
            IAEItemStack stack = null;

            switch (mouseButton) {
                case 0 -> { // pickup / set-down.
                    action = ctrlDown == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
                    stack = ((SlotME) slot).getAEStack();
                    if (stack != null && action == InventoryAction.PICKUP_OR_SET_DOWN
                            && stack.getStackSize() == 0
                            && player.inventory.getItemStack() == null) {
                        action = InventoryAction.AUTO_CRAFT;
                    }
                }
                case 1 -> {
                    action = ctrlDown == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    stack = ((SlotME) slot).getAEStack();
                }
                case 3 -> { // creative dupe:
                    stack = ((SlotME) slot).getAEStack();
                    if (stack != null && stack.isCraftable()) {
                        action = InventoryAction.AUTO_CRAFT;
                    } else if (player.capabilities.isCreativeMode) {
                        final IAEItemStack slotItem = ((SlotME) slot).getAEStack();
                        if (slotItem != null) {
                            action = InventoryAction.CREATIVE_DUPLICATE;
                        }
                    }
                } // drop item:
                default -> {}
            }

            if (action != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                final PacketInventoryAction p = new PacketInventoryAction(action, this.getInventorySlots().size(), 0);
                NetworkHandler.instance.sendToServer(p);
            }

            return;
        }

        if (!this.disableShiftClick && isShiftKeyDown()) {
            this.disableShiftClick = true;

            if (this.dbl_whichItem == null || this.bl_clicked != slot
                    || this.dbl_clickTimer.elapsed(TimeUnit.MILLISECONDS) > 150) {
                // some simple double click logic.
                this.bl_clicked = slot;
                this.dbl_clickTimer = Stopwatch.createStarted();
                if (slot != null) {
                    this.dbl_whichItem = slot.getHasStack() ? slot.getStack().copy() : null;
                } else {
                    this.dbl_whichItem = null;
                }
            } else if (this.dbl_whichItem != null) {
                // a replica of the weird broken vanilla feature.

                final List<Slot> slots = this.getInventorySlots();
                for (final Slot inventorySlot : slots) {
                    if (inventorySlot != null && inventorySlot.canTakeStack(this.mc.thePlayer)
                            && inventorySlot.getHasStack()
                            && inventorySlot.inventory == slot.inventory
                            && Container.func_94527_a(inventorySlot, this.dbl_whichItem, true)) {
                        this.handleMouseClick(inventorySlot, inventorySlot.slotNumber, ctrlDown, 1);
                    }
                }
            }

            this.disableShiftClick = false;
        }

        super.handleMouseClick(slot, slotIdx, ctrlDown, mouseButton);
    }

    @Override
    protected boolean checkHotbarKeys(final int keyCode) {
        final Slot theSlot;

        try {
            theSlot = ObfuscationReflectionHelper
                    .getPrivateValue(GuiContainer.class, this, "theSlot", "field_147006_u", "f");
        } catch (final Throwable t) {
            return false;
        }

        if (this.mc.thePlayer.inventory.getItemStack() == null && theSlot != null) {
            for (int j = 0; j < 9; ++j) {
                if (keyCode == this.mc.gameSettings.keyBindsHotbar[j].getKeyCode()) {
                    final List<Slot> slots = this.getInventorySlots();
                    for (final Slot s : slots) {
                        if (s.getSlotIndex() == j
                                && s.inventory == ((AEBaseContainer) this.inventorySlots).getPlayerInv()) {
                            if (!s.canTakeStack(((AEBaseContainer) this.inventorySlots).getPlayerInv().player)) {
                                return false;
                            }
                        }
                    }

                    if (theSlot.getSlotStackLimit() == 64) {
                        this.handleMouseClick(theSlot, theSlot.slotNumber, j, 2);
                        return true;
                    } else {
                        for (final Slot s : slots) {
                            if (s.getSlotIndex() == j
                                    && s.inventory == ((AEBaseContainer) this.inventorySlots).getPlayerInv()) {
                                NetworkHandler.instance
                                        .sendToServer(new PacketSwapSlots(s.slotNumber, theSlot.slotNumber));
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        this.subGui = true; // in case the gui is reopened later ( i'm looking at you NEI )
    }

    protected Slot getSlot(final int mouseX, final int mouseY) {
        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            // isPointInRegion
            if (this.func_146978_c(slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }

        return null;
    }

    public abstract void drawBG(int offsetX, int offsetY, int mouseX, int mouseY);

    private static boolean hasLwjgl3 = Loader.isModLoaded("lwjgl3ify");

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (!hasLwjgl3) {
            // LWJGL2 reports different scroll values for every platform, 120 for one tick on Windows.
            // LWJGL3 reports the delta in exact scroll ticks.
            // Round away from zero to avoid dropping small scroll events
            if (wheel > 0) {
                wheel = (int) Platform.ceilDiv(wheel, 120);
            } else {
                wheel = -(int) Platform.ceilDiv(-wheel, 120);
            }
        }

        final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (MinecraftForge.EVENT_BUS.post(new GuiScrollEvent(this, x, y, wheel))) {
            return;
        }

        if (!this.mouseWheelEvent(x, y, wheel)) {
            if (this.getScrollBar() != null) {
                final GuiScrollbar scrollBar = this.getScrollBar();

                if (x > this.guiLeft && y - this.guiTop > scrollBar.getTop()
                        && x <= this.guiLeft + this.xSize
                        && y - this.guiTop <= scrollBar.getTop() + scrollBar.getHeight()) {
                    this.getScrollBar().wheel(wheel);
                }
            }
        }
    }

    /**
     * @param x     Current mouse X coordinate
     * @param y     Current mouse Y coordinate
     * @param wheel Wheel movement normalized to units of 1
     * @return If the event was handled
     */
    protected boolean mouseWheelEvent(final int x, final int y, final int wheel) {
        if (!isShiftKeyDown()) {
            return false;
        }
        final Slot slot = this.getSlot(x, y);
        if (slot instanceof SlotME) {
            final IAEItemStack item = ((SlotME) slot).getAEStack();
            if (item != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(item);
                final InventoryAction direction = wheel > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP;
                final int times = Math.abs(wheel);
                final int inventorySize = this.getInventorySlots().size();
                for (int h = 0; h < times; h++) {
                    final PacketInventoryAction p = new PacketInventoryAction(direction, inventorySize, 0);
                    NetworkHandler.instance.sendToServer(p);
                }
            }
        }
        return true;
    }

    protected boolean enableSpaceClicking() {
        return true;
    }

    public void bindTexture(final String base, final String file) {
        final ResourceLocation loc = new ResourceLocation(base, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    public void drawItem(final int x, final int y, final ItemStack is) {
        this.zLevel = 100.0F;
        itemRender.zLevel = 100.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.renderEngine, is, x, y);
        GL11.glPopAttrib();

        itemRender.zLevel = 0.0F;
        this.zLevel = 0.0F;
    }

    protected String getGuiDisplayName(final String in) {
        return this.hasCustomInventoryName() ? this.getInventoryName() : in;
    }

    private boolean hasCustomInventoryName() {
        if (this.inventorySlots instanceof AEBaseContainer) {
            return ((AEBaseContainer) this.inventorySlots).getCustomName() != null;
        }
        return false;
    }

    private String getInventoryName() {
        return ((AEBaseContainer) this.inventorySlots).getCustomName();
    }

    private void drawSlot(final Slot s) {
        if (s instanceof SlotME || s instanceof SlotFake) {
            IAEItemStack stack = Platform.getAEStackInSlot(s);
            if (s instanceof SlotFake && stack != null && stack.getStackSize() == 1) {
                this.safeDrawSlot(s);
                return;
            }

            RenderItem pIR = this.setItemRender(this.aeRenderItem);
            try {
                this.zLevel = 100.0F;
                itemRender.zLevel = 100.0F;

                if (!this.isPowered()) {
                    GL11.glDisable(GL11.GL_LIGHTING);
                    drawRect(
                            s.xDisplayPosition,
                            s.yDisplayPosition,
                            16 + s.xDisplayPosition,
                            16 + s.yDisplayPosition,
                            GuiColors.ItemSlotOverlayUnpowered.getColor());
                    GL11.glEnable(GL11.GL_LIGHTING);
                }

                this.zLevel = 0.0F;
                itemRender.zLevel = 0.0F;

                this.aeRenderItem.setAeStack(Platform.getAEStackInSlot(s));

                this.safeDrawSlot(s);
            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err.toString());
            }
            this.setItemRender(pIR);
            return;
        } else {
            try {
                final ItemStack is = s.getStack();
                if (s instanceof AppEngSlot aes && (((AppEngSlot) s).renderIconWithItem() || is == null)
                        && (((AppEngSlot) s).shouldDisplay())) {
                    if (aes.getIcon() >= 0) {
                        this.bindTexture("guis/states.png");

                        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                        final Tessellator tessellator = Tessellator.instance;
                        try {
                            final int uv_y = (int) Math.floor(aes.getIcon() / 16);
                            final int uv_x = aes.getIcon() - uv_y * 16;

                            GL11.glEnable(GL11.GL_BLEND);
                            GL11.glDisable(GL11.GL_LIGHTING);
                            GL11.glEnable(GL11.GL_TEXTURE_2D);
                            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                            final float par1 = aes.xDisplayPosition;
                            final float par2 = aes.yDisplayPosition;
                            final float par3 = uv_x * 16;
                            final float par4 = uv_y * 16;

                            tessellator.startDrawingQuads();
                            tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon());
                            final float f1 = 0.00390625F;
                            final float f = 0.00390625F;
                            final float par6 = 16;
                            tessellator.addVertexWithUV(
                                    par1 + 0,
                                    par2 + par6,
                                    this.zLevel,
                                    (par3 + 0) * f,
                                    (par4 + par6) * f1);
                            final float par5 = 16;
                            tessellator.addVertexWithUV(
                                    par1 + par5,
                                    par2 + par6,
                                    this.zLevel,
                                    (par3 + par5) * f,
                                    (par4 + par6) * f1);
                            tessellator.addVertexWithUV(
                                    par1 + par5,
                                    par2 + 0,
                                    this.zLevel,
                                    (par3 + par5) * f,
                                    (par4 + 0) * f1);
                            tessellator
                                    .addVertexWithUV(par1 + 0, par2 + 0, this.zLevel, (par3 + 0) * f, (par4 + 0) * f1);
                            tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, 1.0f);
                            tessellator.draw();

                        } catch (final Exception err) {}
                        GL11.glPopAttrib();
                    }
                }

                if (is != null && s instanceof AppEngSlot) {
                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.NotAvailable) {
                        boolean isValid = s.isItemValid(is) || s instanceof SlotOutput
                                || s instanceof AppEngCraftingSlot
                                || s instanceof SlotDisabled
                                || s instanceof SlotInaccessible
                                || s instanceof SlotFake
                                || s instanceof SlotRestrictedInput
                                || s instanceof SlotDisconnected;
                        if (isValid && s instanceof SlotRestrictedInput) {
                            try {
                                isValid = ((SlotRestrictedInput) s).isValid(is, this.mc.theWorld);
                            } catch (final Exception err) {
                                AELog.debug(err);
                            }
                        }
                        ((AppEngSlot) s)
                                .setIsValid(isValid ? hasCalculatedValidness.Valid : hasCalculatedValidness.Invalid);
                    }

                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.Invalid) {
                        this.zLevel = 100.0F;
                        itemRender.zLevel = 100.0F;

                        GL11.glDisable(GL11.GL_LIGHTING);
                        drawRect(
                                s.xDisplayPosition,
                                s.yDisplayPosition,
                                16 + s.xDisplayPosition,
                                16 + s.yDisplayPosition,
                                GuiColors.ItemSlotOverlayInvalid.getColor());
                        GL11.glEnable(GL11.GL_LIGHTING);

                        this.zLevel = 0.0F;
                        itemRender.zLevel = 0.0F;
                    }
                }

                if (s instanceof AppEngSlot) {
                    ((AppEngSlot) s).setDisplay(true);
                    this.safeDrawSlot(s);
                } else {
                    this.safeDrawSlot(s);
                }

                return;
            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err.toString());
            }
        }
        // do the usual for non-ME Slots.
        this.safeDrawSlot(s);
    }

    private RenderItem setItemRender(final RenderItem item) {
        if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.NEI)) {
            return ((INEI) IntegrationRegistry.INSTANCE.getInstance(IntegrationType.NEI)).setItemRender(item);
        } else {
            final RenderItem ri = itemRender;
            itemRender = item;
            return ri;
        }
    }

    protected boolean isPowered() {
        return true;
    }

    private void safeDrawSlot(final Slot s) {
        try {
            GuiContainer.class.getDeclaredMethod("func_146977_a_original", Slot.class).invoke(this, s);
        } catch (final Exception err) {}
    }

    public void bindTexture(final String file) {
        final ResourceLocation loc = new ResourceLocation(AppEng.MOD_ID, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    public void func_146977_a(final Slot s) {
        this.drawSlot(s);
    }

    protected GuiScrollbar getScrollBar() {
        return this.scrollBar;
    }

    protected void setScrollBar(final GuiScrollbar myScrollBar) {
        this.scrollBar = myScrollBar;
    }

    protected List<InternalSlotME> getMeSlots() {
        return this.meSlots;
    }

    public static final synchronized boolean isSwitchingGuis() {
        return switchingGuis;
    }

    public static final synchronized void setSwitchingGuis(final boolean switchingGuis) {
        AEBaseGui.switchingGuis = switchingGuis;
    }

    protected void addItemTooltip(ItemStack is, List<String> lineList) {
        if (isShiftKeyDown()) {
            List<String> l = is.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
            if (!l.isEmpty()) l.remove(0);
            lineList.addAll(l);
        } else {
            lineList.add(GuiText.HoldShiftForTooltip.getLocal());
        }
    }

    // Accessors for protected GUI methods to make them reusable in widget classes

    public FontRenderer getFontRenderer() {
        return fontRendererObj;
    }

    @Override
    public void drawHorizontalLine(int startX, int endX, int y, int color) {
        super.drawHorizontalLine(startX, endX, y, color);
    }

    @Override
    public void drawVerticalLine(int x, int startY, int endY, int color) {
        super.drawVerticalLine(x, startY, endY, color);
    }

    @Override
    public void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        super.drawGradientRect(left, top, right, bottom, startColor, endColor);
    }

    @Override
    public void renderToolTip(ItemStack itemIn, int x, int y) {
        super.renderToolTip(itemIn, x, y);
    }

    @Override
    public void drawCreativeTabHoveringText(String tabName, int mouseX, int mouseY) {
        super.drawCreativeTabHoveringText(tabName, mouseX, mouseY);
    }

    public void drawHoveringText(List<String> textLines, int x, int y) {
        super.func_146283_a(textLines, x, y);
    }

    @Override
    public void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        super.drawHoveringText(textLines, x, y, font);
    }

    public boolean isMouseOverRect(int left, int top, int right, int bottom, int pointX, int pointY) {
        return super.func_146978_c(left, top, right, bottom, pointX, pointY);
    }

    public int getGuiLeft() {
        return guiLeft;
    }

    public int getGuiTop() {
        return guiTop;
    }

    public int getXSize() {
        return xSize;
    }

    public int getYSize() {
        return ySize;
    }
}
