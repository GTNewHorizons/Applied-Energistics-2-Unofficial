package appeng.integration.modules.NEIHelpers;

import static net.minecraft.util.EnumChatFormatting.GRAY;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.TerminalFontSize;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.StackSizeRenderer;
import appeng.core.localization.GuiText;
import appeng.integration.modules.NEIHelpers.NEICellViewHandler.ViewItemStack;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IUsageHandler;

public class NEIPatternViewHandler implements IUsageHandler {

    public static final ResourceLocation SLOT_TEXTURE_LOCATION = new ResourceLocation("nei", "textures/slot.png");
    public static final ResourceLocation ARROW_TEXTURE = new ResourceLocation(
            "textures/gui/container/crafting_table.png");

    public static final int OFFSET_X = 10;
    public static final int INFO_OFFSET_Y = 8;
    public static final int SLOTS_OFFSET_Y = INFO_OFFSET_Y + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 5;
    public static final int TEXT_COLOR = 0x404040;

    public static final int CRAFTING_INPUTS_COLS = 3;
    public static final int CRAFTING_INPUTS_ROWS = 3;
    public static final int CRAFTING_OUTPUTS_COLS = 1;
    public static final int CRAFTING_OUTPUTS_ROWS = 1;

    public static final int PROCESSING_INPUTS_COLS = 4;
    public static final int PROCESSING_INPUTS_ROWS = 8;
    public static final int PROCESSING_OUTPUTS_COLS = 1;
    public static final int PROCESSING_OUTPUTS_ROWS = 8;

    public static final int REVERSED_INPUTS_COLS = 1;
    public static final int REVERSED_INPUTS_ROWS = 8;
    public static final int REVERSED_OUTPUTS_COLS = 4;
    public static final int REVERSED_OUTPUTS_ROWS = 8;

    public static final int ARROW_TEXTURE_X = 91;
    public static final int ARROW_TEXTURE_Y = 35;
    public static final int ARROW_TEXTURE_WIDTH = 22;
    public static final int ARROW_TEXTURE_HEIGHT = 15;
    public static final int ARROW_DRAW_WIDTH = 22;
    public static final int ARROW_DRAW_HEIGHT = 15;
    public static final double ARROW_OFFSET_X = 5;
    public static final double ARROW_OFFSET_Y = 8.5F;

    public int inputsCols;
    public int inputsRows;
    public int inputsOffsetX;
    public int outputsCols;
    public int outputsRows;
    public int outputsOffsetX;
    public int arrowOffsetX;
    public int arrowCenterY;
    public int craftingOutputY = SLOTS_OFFSET_Y;

    public final ArrayList<ViewItemStack> inputSlots = new ArrayList<>();
    public final ArrayList<ViewItemStack> outputSlots = new ArrayList<>();
    public ICraftingPatternDetails patternDetails;

    @Override
    public String getRecipeName() {
        return GuiText.EncodedPattern.getLocal();
    }

    @Override
    public int numRecipes() {
        return 1;
    }

    @Override
    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
        if (!inputId.equals("pattern")) return null;
        if (ingredients == null || ingredients.length == 0) return null;
        if (!(ingredients[0] instanceof ItemStack patternStack)) return null;

        Item item = patternStack.getItem();
        if (!(item instanceof ICraftingPatternItem patternItem)) return null;

        ICraftingPatternDetails details = patternItem
                .getPatternForItem(patternStack, Minecraft.getMinecraft().theWorld);
        if (details == null) return null;

        this.patternDetails = details;
        initializeLayout();
        initializeSlots();
        return this;
    }

    @Override
    public void onUpdate() {}

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public List<PositionedStack> getIngredientStacks(int recipe) {
        List<PositionedStack> result = new ArrayList<>();
        inputSlots.forEach(s -> result.add(s.stack));
        return result;
    }

    @Override
    public List<PositionedStack> getOtherStacks(int recipe) {
        List<PositionedStack> result = new ArrayList<>();
        outputSlots.forEach(s -> result.add(s.stack));
        return result;
    }

    @Override
    public PositionedStack getResultStack(int recipe) {
        return null;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe) {
        if (stack == null) return currenttip;
        drawStackSizeTooltip(inputSlots, stack, currenttip);
        drawStackSizeTooltip(outputSlots, stack, currenttip);
        return currenttip;
    }

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
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
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
        return calculateRecipeHeight();
    }

    public void initializeLayout() {
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

    public void setupCraftingLayout() {
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

    public void setupReversedLayout() {
        inputsCols = REVERSED_INPUTS_COLS;
        inputsRows = REVERSED_INPUTS_ROWS;
        outputsCols = REVERSED_OUTPUTS_COLS;
        outputsRows = REVERSED_OUTPUTS_ROWS;
        inputsOffsetX = OFFSET_X + 10;
        arrowOffsetX = inputsOffsetX + inputsCols * 18 + 8;
        arrowCenterY = SLOTS_OFFSET_Y + (inputsRows * 18) / 2 - 16;
        outputsOffsetX = inputsOffsetX + inputsCols * 18 + 50;
    }

    public void setupProcessingLayout() {
        inputsCols = PROCESSING_INPUTS_COLS;
        inputsRows = PROCESSING_INPUTS_ROWS;
        outputsCols = PROCESSING_OUTPUTS_COLS;
        outputsRows = PROCESSING_OUTPUTS_ROWS;
        inputsOffsetX = OFFSET_X;
        arrowOffsetX = inputsOffsetX + inputsCols * 18 + 4;
        arrowCenterY = SLOTS_OFFSET_Y + (inputsRows * 18) / 2 - 16;
        outputsOffsetX = inputsOffsetX + inputsCols * 18 + 40;
    }

    public List<IAEItemStack> getFilteredStacks(IAEStack<?>[] stacks) {
        boolean isCraftable = patternDetails.isCraftable();

        return Arrays.stream(stacks).filter(stack -> isCraftable || stack != null)
                .map(stack -> stack instanceof IAEItemStack ? (IAEItemStack) stack : null)
                .filter(stack -> isCraftable || stack != null).collect(Collectors.toList());
    }

    public void initializeSlots() {
        inputSlots.clear();
        outputSlots.clear();

        List<IAEItemStack> aeInputs = getFilteredStacks(patternDetails.getAEInputs());
        List<IAEItemStack> aeOutputs = getFilteredStacks(patternDetails.getAEOutputs());

        populateInputSlots(aeInputs);
        populateOutputSlots(aeOutputs);
    }

    public void populateInputSlots(List<IAEItemStack> inputs) {
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
                    inputSlots.add(new ViewItemStack(posStack, aeInput.getStackSize()));
                }
            }
        }
    }

    public void populateOutputSlots(List<IAEItemStack> outputs) {
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
                    outputSlots.add(new ViewItemStack(posStack, aeOutput.getStackSize()));
                }
            }
        }
    }

    public PositionedStack createPositionedStack(ItemStack stack, int x, int y) {
        ItemStack stackCopy = stack.copy();
        PositionedStack posStack = new PositionedStack(stackCopy, x, y);
        posStack.items = new ItemStack[] { stackCopy };
        return posStack;
    }

    public void drawInputSlots(Tessellator tess) {
        for (int r = 0; r < inputsRows; r++)
            for (int c = 0; c < inputsCols; c++) drawSlot(tess, inputsOffsetX + c * 18, SLOTS_OFFSET_Y + r * 18);
    }

    public void drawOutputSlots(Tessellator tess) {
        for (int r = 0; r < outputsRows; r++) for (int c = 0; c < outputsCols; c++) drawSlot(
                tess,
                outputsOffsetX + c * 18,
                (patternDetails.isCraftable() && r == 0) ? craftingOutputY : SLOTS_OFFSET_Y + r * 18);
    }

    public void drawSlot(Tessellator t, int x, int y) {
        t.addVertexWithUV(x + 18, y, 0, 1, 0);
        t.addVertexWithUV(x, y, 0, 0, 0);
        t.addVertexWithUV(x, y + 18, 0, 0, 1);
        t.addVertexWithUV(x + 18, y + 18, 0, 1, 1);
    }

    public void drawArrow() {
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

    public void drawBackground(int recipe) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(SLOT_TEXTURE_LOCATION);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        drawInputSlots(tessellator);
        drawOutputSlots(tessellator);

        tessellator.draw();

        drawArrow();
    }

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

    public int calculateRecipeHeight() {
        return INFO_OFFSET_Y + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + (inputsRows * 18) + 10;
    }

    public void drawStackSize(List<ViewItemStack> stacks) {
        for (ViewItemStack s : stacks) {
            StackSizeRenderer.drawStackSize(
                    s.stack.relx,
                    s.stack.rely,
                    s.stackSize,
                    Minecraft.getMinecraft().fontRenderer,
                    TerminalFontSize.SMALL);
        }
    }

    public void drawStackSizeTooltip(List<ViewItemStack> stacks, ItemStack cur, List<String> tip) {
        stacks.stream().filter(s -> NEIClientUtils.areStacksSameType(s.stack.item, cur)).findFirst().ifPresent(
                s -> tip.add(
                        1,
                        GRAY + GuiText.Stored.getLocal()
                                + ": "
                                + NumberFormat.getNumberInstance().format(s.stackSize)));
    }
}
