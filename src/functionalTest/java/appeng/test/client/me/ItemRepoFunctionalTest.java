package appeng.test.client.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.util.ItemSorters;
import appeng.util.item.AEItemStack;

public class ItemRepoFunctionalTest {

    private static class MutableSortSource implements ISortSource {

        Enum sortBy = SortOrder.NAME;
        Enum sortDir = SortDir.ASCENDING;
        Enum display = ViewItems.ALL;

        @Override
        public Enum getSortBy() {
            return sortBy;
        }

        @Override
        public Enum getSortDir() {
            return sortDir;
        }

        @Override
        public Enum getSortDisplay() {
            return display;
        }
    }

    private static class Fixture {

        final MutableSortSource sort = new MutableSortSource();
        final ItemRepo repo = new ItemRepo(() -> 0, sort);
    }

    private static Fixture newFixture() {
        final Fixture fixture = new Fixture();
        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 5)));
        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.apple, 3)));
        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Blocks.dirt, 1)));
        fixture.repo.updateView();
        return fixture;
    }

    private static List<IAEStack<?>> viewList(final ItemRepo repo) {
        final List<IAEStack<?>> view = new ArrayList<>();
        for (int i = 0; i < repo.size(); i++) {
            view.add(repo.getReferenceStack(i));
        }
        return view;
    }

    private static IAEStack<?> findItem(final ItemRepo repo, final Item item) {
        for (int i = 0; i < repo.size(); i++) {
            final IAEStack<?> stack = repo.getReferenceStack(i);
            if (stack instanceof IAEItemStack ais && ais.getItem() == item) {
                return stack;
            }
        }
        return null;
    }

    private static void assertNameSorted(final ItemRepo repo) {
        final List<IAEStack<?>> view = viewList(repo);
        final List<IAEStack<?>> expected = new ArrayList<>(view);
        expected.sort(ItemSorters.CONFIG_BASED_SORT_BY_NAME);
        assertEquals(expected, view);
    }

    @Test
    void nameSortMatchesUncachedComparator() {
        final Fixture fixture = newFixture();

        assertNameSorted(fixture.repo);
    }

    @Test
    void countOnlyUpdateSkipsRebuild() {
        final Fixture fixture = newFixture();

        final IAEStack<?> stickBefore = findItem(fixture.repo, Items.stick);

        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 7)));
        assertFalse(fixture.repo.updateViewIfChanged());

        final IAEStack<?> stickAfter = findItem(fixture.repo, Items.stick);
        assertSame(stickBefore, stickAfter);
        assertEquals(7, stickAfter.getStackSize());
    }

    @Test
    void visibilityChangesRebuild() {
        final Fixture fixture = newFixture();

        final AEItemStack zeroStick = AEItemStack.create(new ItemStack(Items.stick, 1));
        zeroStick.setStackSize(0);
        fixture.repo.postUpdate(zeroStick);
        assertTrue(fixture.repo.updateViewIfChanged());
        assertNull(findItem(fixture.repo, Items.stick));

        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.bread, 2)));
        assertTrue(fixture.repo.updateViewIfChanged());
        assertNotNull(findItem(fixture.repo, Items.bread));

        assertNameSorted(fixture.repo);
    }

    @Test
    void amountSortRebuildsOnCountChange() {
        final Fixture fixture = newFixture();
        fixture.sort.sortBy = SortOrder.AMOUNT;
        fixture.repo.updateView();

        final ItemStack dirtStack = new ItemStack(Blocks.dirt, 10);
        fixture.repo.postUpdate(AEItemStack.create(dirtStack));
        assertTrue(fixture.repo.updateViewIfChanged());

        final IAEStack<?> first = fixture.repo.getReferenceStack(0);
        assertTrue(first instanceof IAEItemStack ais && ais.getItemStack().isItemEqual(dirtStack));
    }

    @Test
    void craftableModeAlwaysRebuilds() {
        final Fixture fixture = newFixture();
        fixture.sort.display = ViewItems.CRAFTABLE;
        fixture.repo.updateView();

        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 7)));
        assertTrue(fixture.repo.updateViewIfChanged());
    }

    @Test
    void flowRatesRebuildOnlyInFlowingMode() {
        final Fixture fixture = newFixture();

        final Map<IAEStack<?>, FlowRate> rates = new HashMap<>();
        rates.put(AEItemStack.create(new ItemStack(Items.apple, 1)), new FlowRate(1, 0));
        fixture.repo.updateFlowRates(rates);
        assertFalse(fixture.repo.updateViewIfChanged());

        fixture.sort.display = ViewItems.FLOWING;
        fixture.repo.updateView();
        assertEquals(1, fixture.repo.size());
        assertNotNull(findItem(fixture.repo, Items.apple));

        fixture.repo.updateFlowRates(new HashMap<>());
        assertTrue(fixture.repo.updateViewIfChanged());
        assertEquals(0, fixture.repo.size());
    }

    @Test
    void pausedUpdateAppendsWithoutResort() {
        final Fixture fixture = newFixture();

        final List<IAEStack<?>> before = viewList(fixture.repo);
        fixture.repo.setPaused(true);
        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.bread, 2)));
        assertTrue(fixture.repo.updateViewIfChanged());

        final List<IAEStack<?>> after = viewList(fixture.repo);
        assertEquals(before, after.subList(0, 3));
        final IAEStack<?> last = after.get(after.size() - 1);
        assertTrue(last instanceof IAEItemStack ais && ais.getItem() == Items.bread);
    }

    @Test
    void sortKeysPrunedAfterChurn() {
        final Fixture fixture = newFixture();

        for (int meta = 1; meta <= 600; meta++) {
            fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 1, meta)));
        }
        fixture.repo.updateView();
        assertEquals(603, fixture.repo.getSortKeyCount());

        for (int meta = 1; meta <= 600; meta++) {
            final AEItemStack gone = AEItemStack.create(new ItemStack(Items.stick, 1, meta));
            gone.setStackSize(0);
            fixture.repo.postUpdate(gone);
        }
        fixture.repo.updateView();
        assertEquals(3, fixture.repo.getSortKeyCount());
        assertNotNull(fixture.repo.peekSortKey(AEItemStack.create(new ItemStack(Items.apple, 1))));

        for (int meta = 601; meta <= 1200; meta++) {
            fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 1, meta)));
        }
        fixture.repo.updateView();
        assertEquals(603, fixture.repo.getSortKeyCount());
        assertNameSorted(fixture.repo);
    }

    @Test
    void bouncingItemKeepsSortKey() {
        final Fixture fixture = newFixture();
        final AEItemStack stick = AEItemStack.create(new ItemStack(Items.stick, 1));
        final Object keyBefore = fixture.repo.peekSortKey(stick);
        assertNotNull(keyBefore);

        final IAEItemStack zeroStick = stick.copy();
        zeroStick.setStackSize(0);
        fixture.repo.postUpdate(zeroStick);
        fixture.repo.updateView();
        assertNull(findItem(fixture.repo, Items.stick));

        fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 4)));
        fixture.repo.updateView();
        assertSame(keyBefore, fixture.repo.peekSortKey(stick));
    }

    @Test
    void pruneKeepsKeysOfFilteredLiveStacks() {
        final Fixture fixture = newFixture();
        final AEItemStack apple = AEItemStack.create(new ItemStack(Items.apple, 1));
        final Object appleKey = fixture.repo.peekSortKey(apple);
        assertNotNull(appleKey);

        final IAEItemStack craftableApple = apple.copy();
        craftableApple.setStackSize(0);
        craftableApple.setCraftable(true);
        fixture.repo.postUpdate(craftableApple);
        fixture.sort.display = ViewItems.STORED;
        for (int meta = 1; meta <= 600; meta++) {
            fixture.repo.postUpdate(AEItemStack.create(new ItemStack(Items.stick, 1, meta)));
        }
        fixture.repo.updateView();
        assertNull(findItem(fixture.repo, Items.apple));

        for (int meta = 1; meta <= 600; meta++) {
            final AEItemStack gone = AEItemStack.create(new ItemStack(Items.stick, 1, meta));
            gone.setStackSize(0);
            fixture.repo.postUpdate(gone);
        }
        fixture.repo.updateView();
        assertEquals(3, fixture.repo.getSortKeyCount());
        assertSame(appleKey, fixture.repo.peekSortKey(apple));
    }
}
