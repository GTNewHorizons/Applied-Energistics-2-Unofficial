package appeng.client.gui.implementations;

import static appeng.client.gui.implementations.GuiCraftConfirm.*;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.google.common.base.Joiner;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.IGuiTooltipHandler;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerOptimizePatterns;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketOptimizePatterns;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiOptimizePatterns extends AEBaseGui implements IGuiTooltipHandler {

    private GuiTextField amountToCraft;
    private int amountToCraftI = 1;

    private final List<IAEItemStack> visual = new ArrayList<>();
    private int rows = 5;
    final private boolean tallMode;

    final GuiScrollbar scrollbar;

    private GuiBridge OriginalGui;
    private GuiButton cancel;
    private GuiButton optimize;

    private int tooltip = -1;
    private IAEItemStack hoveredStack;
    private final HashSet<IAEItemStack> ignoreList = new HashSet<>();
    private final HashMap<IAEItemStack, Integer> multiplierMap = new HashMap<>();

    public GuiOptimizePatterns(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerOptimizePatterns(inventoryPlayer, te));
        this.tallMode = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;

        this.xSize = LIST_VIEW_TEXTURE_WIDTH;
        this.rows = 5;
        this.ySize = LIST_VIEW_TEXTURE_HEIGHT;

        scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        if (te instanceof WirelessTerminalGuiObject) {
            this.OriginalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (te instanceof PartTerminal) {
            this.OriginalGui = GuiBridge.GUI_ME;
        }

        if (te instanceof PartCraftingTerminal) {
            this.OriginalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (te instanceof PartPatternTerminal) {
            this.OriginalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (te instanceof PartPatternTerminalEx) {
            this.OriginalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
    }

    @Override
    public void initGui() {
        if (tallMode) {
            final int maxAvailableHeight = height - 64;
            this.rows = (maxAvailableHeight - LIST_VIEW_TEXTURE_NONROW_HEIGHT) / LIST_VIEW_TEXTURE_ROW_HEIGHT;
            this.ySize = LIST_VIEW_TEXTURE_NONROW_HEIGHT + this.rows * LIST_VIEW_TEXTURE_ROW_HEIGHT;
        } else {
            this.rows = 5;
            this.ySize = LIST_VIEW_TEXTURE_HEIGHT;
        }

        super.initGui();

        this.setScrollBar();

        this.optimize = new GuiButton(
                0,
                this.guiLeft + this.xSize - 76,
                this.guiTop + this.ySize - 25,
                50,
                20,
                GuiText.Optimize.getLocal());
        this.optimize.enabled = false;
        this.buttonList.add(this.optimize);

        this.cancel = new GuiButton(
                0,
                this.guiLeft + 6,
                this.guiTop + this.ySize - 25,
                50,
                20,
                GuiText.Cancel.getLocal());
        this.buttonList.add(this.cancel);

        this.amountToCraft = new GuiTextField(
                this.fontRendererObj,
                this.guiLeft + 113,
                this.guiTop + this.ySize - 68,
                100,
                20);
        this.amountToCraft.setEnableBackgroundDrawing(true);
        this.amountToCraft.setMaxStringLength(16);
        this.amountToCraft.setTextColor(GuiColors.CraftAmountToCraft.getColor());
        this.amountToCraft.setVisible(true);
        this.amountToCraft.setFocused(true);
        this.amountToCraft.setText("1");
        this.amountToCraft.setSelectionPos(0);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float btn) {
        super.drawScreen(mouseX, mouseY, btn);

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= 4 * this.rows; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {

        this.fontRendererObj
                .drawString(GuiText.PatternOptimizer.getLocal(), 8, 7, GuiColors.CraftConfirmCraftingPlan.getColor());

        this.fontRendererObj.drawString(
                GuiText.StepsPerCraft.getLocal() + ":",
                6,
                (ySize - 68) + (20 / 2) - (this.fontRendererObj.FONT_HEIGHT / 2),
                GuiColors.CraftConfirmSimulation.getColor());

        String dsp = GuiText.PatternsAffected.getLocal() + ": " + multiplierMap.size();

        final int offset = (219 - this.fontRendererObj.getStringWidth(dsp)) / 2;
        this.fontRendererObj.drawString(dsp, offset, ySize - 41, GuiColors.CraftConfirmSimulation.getColor());

        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * this.rows;

        final int sectionLength = 67;

        int x = 0;
        int y = 0;
        final int xo = 9;
        final int yo = 22;
        final int offY = 23;

        String dspToolTip = "";
        final List<String> lineList = new LinkedList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        hoveredStack = null;

        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final IAEItemStack refStack = this.visual.get(z);
            if (refStack != null) {
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);

                int lines = 1;
                long multipliedBy = multiplierMap.getOrDefault(refStack, 0);
                if (amountToCraftI > 0 && multipliedBy > 0) {
                    lines++;
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                {
                    String str = GuiText.ToCraftRequests.getLocal() + ": "
                            + ReadableNumberConverter.INSTANCE.toWideReadableForm(refStack.getCountRequestableCrafts());
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2,
                            GuiColors.CraftConfirmMissing.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.ToCraftRequests.getLocal() + ": "
                                        + NumberFormat.getInstance().format(refStack.getCountRequestableCrafts()));
                    }

                    downY += 5;
                }

                if (amountToCraftI > 0 && multipliedBy > 0) {
                    String str = GuiText.Multiplied.getLocal() + ": x"
                            + ReadableNumberConverter.INSTANCE.toWideReadableForm(1L << multipliedBy);
                    final int w = 4 + this.fontRendererObj.getStringWidth(str);
                    this.fontRendererObj.drawString(
                            str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2,
                            GuiColors.CraftConfirmMissing.getColor());

                    if (this.tooltip == z - viewStart) {
                        lineList.add(
                                GuiText.MultipliedBy.getLocal() + ": "
                                        + NumberFormat.getInstance().format(1L << multipliedBy));
                        lineList.add(
                                GuiText.CurrentPatternOutput.getLocal() + ": "
                                        + NumberFormat.getInstance().format(refStack.getCountRequestable()));
                        lineList.add(
                                GuiText.NewPatternOutput.getLocal() + ": "
                                        + NumberFormat.getInstance()
                                                .format(refStack.getCountRequestable() << multipliedBy));
                    }

                    downY += 5;
                }

                GL11.glPopMatrix();
                final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
                final int posY = y * offY + yo;

                final ItemStack is = refStack.copy().getItemStack();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(is);
                    if (!lineList.isEmpty()) {
                        addItemTooltip(is, lineList);
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                    toolPosY = y * offY + yo;

                    hoveredStack = refStack.copy();
                }

                this.drawItem(posX, posY, is);

                if (ignoreList.contains(refStack) || multipliedBy == 0) {
                    final int startX = x * (1 + sectionLength) + xo;
                    final int startY = posY - 4;
                    drawRect(
                            startX,
                            startY,
                            startX + sectionLength,
                            startY + offY,
                            GuiColors.CraftConfirmMissingItem.getColor());
                }

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }

            }
        }

        if (this.tooltip >= 0 && !dspToolTip.isEmpty()) {
            this.drawTooltip(toolPosX, toolPosY + 10, 0, dspToolTip);
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/craftingreport.png");
        if (tallMode) {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y);
            int y = LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y;
            // first and last row are pre-baked
            for (int row = 1; row < rows - 1; row++) {
                this.drawTexturedModalRect(
                        offsetX,
                        offsetY + y,
                        0,
                        LIST_VIEW_TEXTURE_BELOW_TOP_ROW_Y,
                        this.xSize,
                        LIST_VIEW_TEXTURE_ROW_HEIGHT);
                y += LIST_VIEW_TEXTURE_ROW_HEIGHT;
            }
            this.drawTexturedModalRect(
                    offsetX,
                    offsetY + y,
                    0,
                    LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y,
                    this.xSize,
                    LIST_VIEW_TEXTURE_HEIGHT - LIST_VIEW_TEXTURE_ABOVE_BOTTOM_ROW_Y);
        } else {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        }

        this.amountToCraft.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.optimize);
            }
            this.amountToCraft.textboxKeyTyped(character, key);
            super.keyTyped(character, key);

            String out = this.amountToCraft.getText();

            double resultD = Calculator.conversion(out);
            int resultI;

            if (resultD <= 0 || Double.isNaN(resultD)) {
                resultI = 0;
            } else {
                resultI = (int) ArithHelper.round(resultD, 0);
            }

            amountToCraftI = resultI;
            updateMultipliers();
            this.optimize.enabled = resultI > 0 && !multiplierMap.isEmpty();
        }
    }

    private void updateMultipliers() {
        if (amountToCraftI == 0) return;
        multiplierMap.clear();

        for (IAEItemStack stack : this.visual) {
            if (!ignoreList.contains(stack)) {
                int v = Math.min(
                        ContainerOptimizePatterns.getBitMultiplier(
                                stack.getCountRequestableCrafts(),
                                stack.getCountRequestable(),
                                amountToCraftI),
                        (int) (stack.getStackSize() & 0b11111));
                if (v > 0) multiplierMap.put(stack, v);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        super.actionPerformed(btn);

        if (btn == this.cancel) {
            if (this.OriginalGui != null) {
                NetworkHandler.instance.sendToServer(new PacketSwitchGuis(this.OriginalGui));
            }
        } else if (btn == this.optimize && this.optimize.enabled) {
            try {
                NetworkHandler.instance.sendToServer(new PacketOptimizePatterns(multiplierMap));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }

    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {
        if (hoveredStack != null) {
            if (ignoreList.contains(hoveredStack)) ignoreList.remove(hoveredStack);
            else ignoreList.add(hoveredStack);
            updateMultipliers();
            this.optimize.enabled = amountToCraftI > 0 && !multiplierMap.isEmpty();
            return;
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    public void postUpdate(final List<IAEItemStack> list, final byte ref) {
        visual.clear();
        for (IAEItemStack stack : list) {
            visual.add(stack.copy());
        }

        this.sortItems();
        this.setScrollBar();

        updateMultipliers();
        this.optimize.enabled = amountToCraftI > 0 && !multiplierMap.isEmpty();
    }

    @Override
    public ItemStack getHoveredStack() {
        if (hoveredStack != null) return hoveredStack.getItemStack();
        return null;
    }

    Comparator<IAEItemStack> comparator = (i1,
            i2) -> (int) (i2.getCountRequestableCrafts() - i1.getCountRequestableCrafts());

    private void sortItems() {
        this.visual.sort(comparator);
    }

    private void setScrollBar() {
        if (getScrollBar() == null) {
            setScrollBar(scrollbar);
        }
        final int size = this.visual.size();
        this.getScrollBar().setTop(19).setLeft(218).setHeight(ySize - 92);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }
}
