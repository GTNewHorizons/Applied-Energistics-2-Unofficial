package appeng.integration.modules.NEIHelpers;

import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import appeng.api.AEApi;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.CellInventoryHandler;
import appeng.util.IterationCounter;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class NEICellSearchFilter implements SearchTokenParser.ISearchParserProvider {

    @Override
    public ItemFilter getFilter(String searchText) {
        Pattern pattern = SearchField.getPattern(searchText);
        return pattern == null ? null : new Filter(pattern);
    }

    @Override
    public char getPrefix() {
        return 0;
    }

    @Override
    public EnumChatFormatting getHighlightedColor() {
        return null;
    }

    @Override
    public SearchTokenParser.SearchMode getSearchMode() {
        return SearchTokenParser.SearchMode.ALWAYS;
    }

    public static class Filter implements ItemFilter {

        Pattern pattern;

        public Filter(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(ItemStack itemStack) {
            if (itemStack.getItem() instanceof IStorageCell storageCell) {
                IAEStackType<?> type = storageCell.getStackType();
                final IMEInventoryHandler<?> inventory = AEApi.instance().registries().cell()
                        .getCellInventory(itemStack, null, type);
                if (inventory instanceof final CellInventoryHandler handler) {
                    final ICellInventory cellInventory = handler.getCellInv();
                    if (cellInventory != null) {
                        final IItemList<IAEStack<?>> out = AEApi.instance().storage().createAEStackList();
                        // Partitions
                        for (Object item : handler.getPartitionList().getItems())
                            out.add((IAEStack<?>) item);
                        // Stacks in the cell
                        cellInventory.getAvailableItems(out, IterationCounter.fetchNewId());

                        for (IAEStack<?> item : out) {
                            boolean result = pattern.matcher(item.getDisplayName().toLowerCase())
                                    .find();
                            if (result) return true;
                        }
                    }
                }
            }

            return false;
        }

    }
}
