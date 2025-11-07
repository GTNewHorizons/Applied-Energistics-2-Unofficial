package appeng.integration.modules.NEIHelpers;

import static codechicken.lib.gui.GuiDraw.fontRenderer;
import static net.minecraft.util.EnumChatFormatting.GRAY;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.TerminalFontSize;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.StackSizeRenderer;
import appeng.core.localization.GuiText;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IUsageHandler;

public class NEIPatternViewHandler implements IUsageHandler {

    private static class PatternSlot {

        public final PositionedStack stack;
        public long stackSize;

        public PatternSlot(PositionedStack stack, long stackSize) {
            this.stack = stack;
            this.stackSize = stackSize;
        }
    }

    private static final ResourceLocation SLOT_TEXTURE_LOCATION = new ResourceLocation("nei", "textures/slot.png");
    private static final ResourceLocation ARROW_TEXTURE = new ResourceLocation(
            "textures/gui/container/crafting_table.png");

    private static final int OFFSET_X = 10;
    private static final int INFO_OFFSET_Y = 8;
    private static final int SLOTS_OFFSET_Y = INFO_OFFSET_Y + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 5;
    private static final int TEXT_COLOR = 0x404040;

    private static final int CRAFTING_INPUTS_COLS = 3;
    private static final int CRAFTING_INPUTS_ROWS = 3;
    private static final int CRAFTING_OUTPUTS_COLS = 1;
    private static final int CRAFTING_OUTPUTS_ROWS = 1;

    private static final int PROCESSING_INPUTS_COLS = 4;
    private static final int PROCESSING_INPUTS_ROWS = 8;
    private static final int PROCESSING_OUTPUTS_COLS = 1;
    private static final int PROCESSING_OUTPUTS_ROWS = 8;

    private static final int REVERSED_INPUTS_COLS = 1;
    private static final int REVERSED_INPUTS_ROWS = 8;
    private static final int REVERSED_OUTPUTS_COLS = 4;
    private static final int REVERSED_OUTPUTS_ROWS = 8;

    private static final int ARROW_TEXTURE_X = 91;
    private static final int ARROW_TEXTURE_Y = 35;
    private static final int ARROW_TEXTURE_WIDTH = 22;
    private static final int ARROW_TEXTURE_HEIGHT = 15;
    private static final int ARROW_DRAW_WIDTH = 22;
    private static final int ARROW_DRAW_HEIGHT = 15;
    private static final double ARROW_OFFSET_X = 5;
    private static final double ARROW_OFFSET_Y = 8.5F;

    private int inputsCols;
    private int inputsRows;
    private int inputsOffsetX;
    private int outputsCols;
    private int outputsRows;
    private int outputsOffsetX;
    private int arrowOffsetX;
    private int arrowCenterY;
    private int craftingOutputY = SLOTS_OFFSET_Y;

    private final ArrayList<PatternSlot> inputSlots = new ArrayList<>();
    private final ArrayList<PatternSlot> outputSlots = new ArrayList<>();
    private ICraftingPatternDetails patternDetails;

    @Override
    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
        if (ingredients.length == 0 || !(ingredients[0] instanceof ItemStack ingredient)) {
            return null;
        }

        if (!(ingredient.getItem() instanceof ICraftingPatternItem patternItem)) {
            return null;
        }

        this.patternDetails = patternItem.getPatternForItem(ingredient, Minecraft.getMinecraft().theWorld);
        if (this.patternDetails == null) {
            return null;
        }

        initializeLayout();
        initializeSlots();
        return this;
    }

    private void initializeLayout() {
        boolean isCraftable = patternDetails.isCraftable();
        List<IAEItemStack> aeOutputs = getFilteredStacks(patternDetails.getAEOutputs());
        int nonNullOutputs = (int) aeOutputs.stream().filter(Objects::nonNull).count();

        if (isCraftable) {
            setupCraftingLayout();
        } else if (nonNullOutputs > 8) {
            setupReversedLayout();
        } else {
            setupProcessingLayout();
        }
    }

    private void setupCraftingLayout() {
        inputsCols = CRAFTING_INPUTS_COLS;
        inputsRows = CRAFTING_INPUTS_ROWS;
        outputsCols = CRAFTING_OUTPUTS_COLS;
        outputsRows = CRAFTING_OUTPUTS_ROWS;
        inputsOffsetX = OFFSET_X;
        arrowOffsetX = inputsOffsetX + inputsCols * 18 + 10;
        arrowCenterY = SLOTS_OFFSET_Y + (inputsRows * 18) / 2 - 16;
        outputsOffsetX = arrowOffsetX + 32 + 10;
        craftingOutputY = arrowCenterY + 8;
    }

    private void setupReversedLayout() {
        inputsCols = REVERSED_INPUTS_COLS;
        inputsRows = REVERSED_INPUTS_ROWS;
        outputsCols = REVERSED_OUTPUTS_COLS;
        outputsRows = REVERSED_OUTPUTS_ROWS;
        inputsOffsetX = OFFSET_X + 10;
        arrowOffsetX = inputsOffsetX + inputsCols * 18 + 8;
        arrowCenterY = SLOTS_OFFSET_Y + (inputsRows * 18) / 2 - 16;
        outputsOffsetX = inputsOffsetX + inputsCols * 18 + 50;
    }

    private void setupProcessingLayout() {
        inputsCols = PROCESSING_INPUTS_COLS;
        inputsRows = PROCESSING_INPUTS_ROWS;
        outputsCols = PROCESSING_OUTPUTS_COLS;
        outputsRows = PROCESSING_OUTPUTS_ROWS;
        inputsOffsetX = OFFSET_X;
        arrowOffsetX = inputsOffsetX + inputsCols * 18 + 4;
        arrowCenterY = SLOTS_OFFSET_Y + (inputsRows * 18) / 2 - 16;
        outputsOffsetX = inputsOffsetX + inputsCols * 18 + 40;
    }

    private void initializeSlots() {
        inputSlots.clear();
        outputSlots.clear();

        List<IAEItemStack> aeInputs = getFilteredStacks(patternDetails.getAEInputs());
        List<IAEItemStack> aeOutputs = getFilteredStacks(patternDetails.getAEOutputs());

        populateInputSlots(aeInputs);
        populateOutputSlots(aeOutputs);
    }

    private List<IAEItemStack> getFilteredStacks(IAEStack<?>[] stacks) {
        boolean isCraftable = patternDetails.isCraftable();

        return Arrays.stream(stacks).filter(stack -> isCraftable || stack != null)
                .map(stack -> stack instanceof IAEItemStack ? (IAEItemStack) stack : null)
                .filter(stack -> isCraftable || stack != null).collect(Collectors.toList());
    }

    private void populateInputSlots(List<IAEItemStack> inputs) {
        for (int i = 0; i < inputsCols * inputsRows; i++) {
            int row = i / inputsCols;
            int col = i % inputsCols;
            int x = inputsOffsetX + col * 18 + 1;
            int y = SLOTS_OFFSET_Y + row * 18 + 1;

            if (i < inputs.size()) {
                IAEItemStack aeInput = inputs.get(i);
                if (aeInput != null) {
                    ItemStack inputStack = aeInput.getItemStack().copy();
                    inputStack.stackSize = 1;
                    PositionedStack posStack = createPositionedStack(inputStack, x, y);
                    inputSlots.add(new PatternSlot(posStack, aeInput.getStackSize()));

                }
            }
        }
    }

    private void populateOutputSlots(List<IAEItemStack> outputs) {
        for (int i = 0; i < outputsCols * outputsRows; i++) {
            int row = i / outputsCols;
            int col = i % outputsCols;
            int x = outputsOffsetX + col * 18 + 1;
            int y = patternDetails.isCraftable() && i == 0 ? craftingOutputY + 1 : SLOTS_OFFSET_Y + row * 18 + 1;

            if (i < outputs.size()) {
                IAEItemStack aeOutput = outputs.get(i);
                if (aeOutput != null) {
                    ItemStack outputStack = aeOutput.getItemStack().copy();
                    outputStack.stackSize = 1;
                    PositionedStack posStack = createPositionedStack(outputStack, x, y);
                    outputSlots.add(new PatternSlot(posStack, aeOutput.getStackSize()));
                }
            }
        }
    }

    private PositionedStack createPositionedStack(ItemStack stack, int x, int y) {
        ItemStack stackCopy = stack.copy();
        PositionedStack posStack = new PositionedStack(stackCopy, x, y);
        posStack.items = new ItemStack[] { stackCopy };
        return posStack;
    }

    @Override
    public String getRecipeName() {
        return GuiText.EncodedPattern.getLocal();
    }

    @Override
    public int numRecipes() {
        return 1;
    }

    @Override
    public void drawBackground(int recipe) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(SLOT_TEXTURE_LOCATION);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        drawInputSlots(tessellator);
        drawOutputSlots(tessellator);

        tessellator.draw();

        drawArrow();
    }

    private void drawInputSlots(Tessellator tessellator) {
        for (int row = 0; row < inputsRows; row++) {
            for (int col = 0; col < inputsCols; col++) {
                drawSlot(tessellator, inputsOffsetX + col * 18, SLOTS_OFFSET_Y + row * 18);
            }
        }
    }

    private void drawOutputSlots(Tessellator tessellator) {
        for (int row = 0; row < outputsRows; row++) {
            for (int col = 0; col < outputsCols; col++) {
                int x = outputsOffsetX + col * 18;
                int y = patternDetails.isCraftable() && row == 0 ? craftingOutputY : SLOTS_OFFSET_Y + row * 18;
                drawSlot(tessellator, x, y);
            }
        }
    }

    private void drawSlot(Tessellator tessellator, int x, int y) {
        tessellator.addVertexWithUV(x + 18, y, 0, 1, 0);
        tessellator.addVertexWithUV(x, y, 0, 0, 0);
        tessellator.addVertexWithUV(x, y + 18, 0, 0, 1);
        tessellator.addVertexWithUV(x + 18, y + 18, 0, 1, 1);
    }

    private void drawArrow() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ARROW_TEXTURE);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        double arrowX = arrowOffsetX + ARROW_OFFSET_X;
        double arrowY = arrowCenterY + ARROW_OFFSET_Y;

        float uMin = ARROW_TEXTURE_X / 256f;
        float uMax = (ARROW_TEXTURE_X + ARROW_TEXTURE_WIDTH) / 256f;
        float vMin = ARROW_TEXTURE_Y / 256f;
        float vMax = (ARROW_TEXTURE_Y + ARROW_TEXTURE_HEIGHT) / 256f;

        tessellator.addVertexWithUV(arrowX + ARROW_DRAW_WIDTH, arrowY, 0, uMax, vMin);
        tessellator.addVertexWithUV(arrowX, arrowY, 0, uMin, vMin);
        tessellator.addVertexWithUV(arrowX, arrowY + ARROW_DRAW_HEIGHT, 0, uMin, vMax);
        tessellator.addVertexWithUV(arrowX + ARROW_DRAW_WIDTH, arrowY + ARROW_DRAW_HEIGHT, 0, uMax, vMax);

        tessellator.draw();
    }

    @Override
    public void drawForeground(int recipe) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        String inputs = GuiText.Inputs.getLocal();
        String outputs = GuiText.Outputs.getLocal();

        int inputsCenterX = inputsOffsetX + (inputsCols * 18) / 2 - fontRenderer.getStringWidth(inputs) / 2;
        int outputsCenterX = outputsOffsetX + (outputsCols * 18) / 2 - fontRenderer.getStringWidth(outputs) / 2;

        fontRenderer.drawString(inputs, inputsCenterX, INFO_OFFSET_Y, TEXT_COLOR);
        fontRenderer.drawString(outputs, outputsCenterX, INFO_OFFSET_Y, TEXT_COLOR);

        drawStackSize(inputSlots);
        drawStackSize(outputSlots);
    }

    private void drawStackSize(ArrayList<PatternSlot> stacks) {
        for (PatternSlot viewStack : stacks) {
            StackSizeRenderer.drawStackSize(
                    viewStack.stack.relx,
                    viewStack.stack.rely,
                    viewStack.stackSize,
                    fontRenderer,
                    TerminalFontSize.SMALL);
        }
    }

    private void drawStackSizeTooltip(ArrayList<PatternSlot> stacks, ItemStack curStack, List<String> currenttip) {
        stacks.stream().filter(viewStack -> viewStack.stack.item.equals(curStack)).findFirst().ifPresent(
                viewItemStack -> currenttip.add(
                        1,
                        GRAY + GuiText.Stored.getLocal()
                                + ": "
                                + NumberFormat.getNumberInstance().format(viewItemStack.stackSize)));
    }

    @Override
    public List<PositionedStack> getIngredientStacks(int recipe) {
        return Collections.emptyList();
    }

    @Override
    public List<PositionedStack> getOtherStacks(int recipe) {
        List<PositionedStack> result = new ArrayList<>(inputSlots.size() + outputSlots.size());
        result.addAll(inputSlots.stream().map(slot -> slot.stack).collect(Collectors.toList()));
        result.addAll(outputSlots.stream().map(slot -> slot.stack).collect(Collectors.toList()));
        return result;
    }

    @Override
    public PositionedStack getResultStack(int recipe) {
        return null;
    }

    @Override
    public void onUpdate() {}

    @Override
    public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
        return false;
    }

    @Override
    public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
        return currenttip;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe) {
        if (stack == null) return currenttip;

        drawStackSizeTooltip(inputSlots, stack, currenttip);
        drawStackSizeTooltip(outputSlots, stack, currenttip);

        return currenttip;
    }

    @Override
    public boolean keyTyped(GuiRecipe<?> gui, char keyChar, int keyCode, int recipe) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {
        return false;
    }

    @Override
    public int getRecipeHeight(int recipe) {
        int headerHeight = INFO_OFFSET_Y + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
        int slotsHeight = inputsRows * 18;
        int bottomPadding = 10;
        return headerHeight + slotsHeight + bottomPadding;
    }
}
