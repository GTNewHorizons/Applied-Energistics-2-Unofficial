package appeng.api.implementations;

import appeng.util.AEStackTypeFilter;

public interface ITypeFilterProvider {

    AEStackTypeFilter getTypeFilters();

    void onChangeTypeFilters();
}
