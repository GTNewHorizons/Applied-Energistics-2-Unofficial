package appeng.integration.modules.NEIHelpers;

import static net.minecraft.util.EnumChatFormatting.GRAY;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;
import appeng.api.config.TerminalFontSize;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.StackSizeRenderer;
import appeng.core.localization.GuiText;
import appeng.me.storage.CellInventoryHandler;
import appeng.util.Platform;
import appeng.util.item.ItemList;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IUsageHandler;

public class NEICellViewHandler implements IUsageHandler {

    private static class ViewItemStack {

        public PositionedStack stack;
        public long stackSize;

        public ViewItemStack(PositionedStack stack, long stackSize) {
            this.stack = stack;
            this.stackSize = stackSize;
        }
    }

    private static final ResourceLocation SLOT_TEXTURE_LOCATION = new ResourceLocation("nei", "textures/slot.png");
    private static final int OFFSET_X = 2;
    private static final int INFO_OFFSET_Y = 4;
    private static final int ITEMS_OFFSET_Y = INFO_OFFSET_Y + Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT * 2 + 6;
    private static final int ROW_ITEM_NUM = 9;

    private final ArrayList<ViewItemStack> stacks = new ArrayList<>();
    private CellInventoryHandler cellHandler;

    @Override
    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {
        if (ingredients.length > 0 && ingredients[0] instanceof ItemStack ingredient
                && AEApi.instance().registries().cell().getCellInventory(
                        ingredient,
                        null,
                        StorageChannel.ITEMS) instanceof CellInventoryHandler handler
                && handler.getTotalBytes() > 0) {
            this.cellHandler = handler;

            ItemList list = new ItemList();
            handler.getAvailableItems(list, appeng.util.IterationCounter.fetchNewId());

            List<IAEItemStack> sortedStacks = new ArrayList<>();
            list.iterator().forEachRemaining(sortedStacks::add);
            sortedStacks.sort(Comparator.comparing(IAEStack::getStackSize, Comparator.reverseOrder()));

            stacks.clear();
            int count = 0;
            for (IAEItemStack item : sortedStacks) {
                ItemStack stack = item.getItemStack();
                long stackSize = stack.stackSize;
                stack.stackSize = 1;
                PositionedStack positionedStack = new PositionedStack(
                        stack,
                        OFFSET_X + count % ROW_ITEM_NUM * 18 + 1,
                        ITEMS_OFFSET_Y + count / ROW_ITEM_NUM * 18 + 1);
                stacks.add(new ViewItemStack(positionedStack, stackSize));
                count++;
            }
            return this;
        }
        return null;
    }

    @Override
    public String getRecipeName() {
        return GuiText.CellView.getLocal();
    }

    @Override
    public int numRecipes() {
        return 1;
    }

    @Override
    public void drawBackground(int recipe) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(SLOT_TEXTURE_LOCATION);

        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int i = 0; i < this.cellHandler.getTotalTypes(); i++) {
            final int line = i % ROW_ITEM_NUM;
            final int row = i / ROW_ITEM_NUM;
            tessellator.addVertexWithUV(OFFSET_X + 18 * (line + 1), ITEMS_OFFSET_Y + 18 * row, 0, 1, 0);
            tessellator.addVertexWithUV(OFFSET_X + 18 * line, ITEMS_OFFSET_Y + 18 * row, 0, 0, 0);
            tessellator.addVertexWithUV(OFFSET_X + 18 * line, ITEMS_OFFSET_Y + 18 * (row + 1), 0, 0, 1);
            tessellator.addVertexWithUV(OFFSET_X + 18 * (line + 1), ITEMS_OFFSET_Y + 18 * (row + 1), 0, 1, 1);
        }
        tessellator.draw();
    }

    @Override
    public void drawForeground(int recipe) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        final String usedBytes = Platform.formatByteDouble(cellHandler.getUsedBytes());
        final String totalBytes = Platform.formatByteDouble(cellHandler.getTotalBytes());
        fontRenderer.drawString(
                usedBytes + " " + GuiText.Of.getLocal() + ' ' + totalBytes + ' ' + GuiText.BytesUsed.getLocal(),
                OFFSET_X,
                INFO_OFFSET_Y,
                0);
        fontRenderer.drawString(
                NumberFormat.getInstance().format(cellHandler.getUsedTypes()) + " "
                        + GuiText.Of.getLocal()
                        + ' '
                        + NumberFormat.getInstance().format(cellHandler.getTotalTypes())
                        + ' '
                        + GuiText.Types.getLocal(),
                OFFSET_X,
                INFO_OFFSET_Y + fontRenderer.FONT_HEIGHT + 2,
                0);

        for (ViewItemStack viewStack : this.stacks) {
            StackSizeRenderer.drawStackSize(
                    viewStack.stack.relx,
                    viewStack.stack.rely,
                    viewStack.stackSize,
                    fontRenderer,
                    TerminalFontSize.SMALL);
        }
    }

    @Override
    public List<PositionedStack> getIngredientStacks(int recipe) {
        return new ArrayList<>();
    }

    @Override
    public List<PositionedStack> getOtherStacks(int recipe) {
        return this.stacks.stream().map(stack -> stack.stack).collect(Collectors.toList());
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

        this.stacks.stream().filter(viewStack -> viewStack.stack.item.equals(stack)).findFirst().ifPresent(
                viewItemStack -> currenttip.add(
                        1,
                        GRAY + GuiText.Stored.getLocal()
                                + ": "
                                + NumberFormat.getNumberInstance().format(viewItemStack.stackSize)));

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
}
