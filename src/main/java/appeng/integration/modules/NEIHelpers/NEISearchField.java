package appeng.integration.modules.NEIHelpers;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.MEGuiTextField;
import codechicken.nei.ItemList;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTextFormatter;
import codechicken.nei.api.ItemFilter;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class NEISearchField {

    private SearchField searchField = null;

    public NEISearchField() {}

    protected SearchField getSearchField() {

        if (searchField != null) {
            return searchField;
        }

        try {
            final Class<? super Object> clazz = ReflectionHelper
                    .getClass(this.getClass().getClassLoader(), "codechicken.nei.LayoutManager");
            final Field fldSearchField = clazz.getField("searchField");
            searchField = (SearchField) fldSearchField.get(clazz);
        } catch (Throwable __) {}

        return searchField;
    }

    public boolean existsSearchField() {
        return getSearchField() != null;
    }

    public String getEscapedSearchText(String text) {

        if (existsSearchField()) {
            return SearchField.getEscapedSearchText(text);
        }

        return text;
    }

    public void putFormatter(MEGuiTextField field) {
        final SearchField searchField = getSearchField();

        if (searchField != null) {
            try {
                field.setFormatter(new SearchTextFormatter(searchField.searchParser));
            } catch (Throwable __) {}
        }
    }

    public void setText(String filter) {
        final SearchField searchField = getSearchField();

        if (searchField != null && !searchField.text().equals(filter)) {
            searchField.setText(filter);
        }
    }

    public void updateFilter() {
        if (existsSearchField()) {
            ItemList.updateFilter.restart();
        }
    }

    public Predicate<ItemStack> getFilter(String filterText) {
        final SearchField searchField = getSearchField();

        if (searchField != null) {
            final ItemFilter filter = searchField.getFilter(filterText);
            return stack -> filter.matches(stack);
        }

        return null;
    }

    public boolean focused() {
        final SearchField searchField = getSearchField();

        return searchField != null && searchField.focused();
    }

    public void setFocus(boolean focused) {
        final SearchField searchField = getSearchField();

        if (searchField != null) {
            searchField.setFocus(focused);
        }
    }

}
