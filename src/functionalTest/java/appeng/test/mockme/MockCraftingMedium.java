package appeng.test.mockme;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.util.inv.MEInventoryCrafting;

public class MockCraftingMedium implements ICraftingMedium {

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final MEInventoryCrafting table,
            final int multiplier) {
        return true;
    }

    @Override
    public boolean isBusy() {
        return false;
    }
}
