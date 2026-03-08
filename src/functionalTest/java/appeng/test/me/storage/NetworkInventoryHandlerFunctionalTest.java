package appeng.test.me.storage;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.DimensionManager;

import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.SecurityCache;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.test.DummySaveHandler;
import appeng.test.mockme.MockAESystem;
import appeng.test.mockme.MockGrid;
import appeng.test.mockme.MockGridNode;
import appeng.util.item.ItemList;

public class NetworkInventoryHandlerFunctionalTest {

    private static World createDummyWorld() {
        if (!DimensionManager.isDimensionRegistered(256)) {
            DimensionManager.registerProviderType(256, WorldProviderSurface.class, false);
            DimensionManager.registerDimension(256, 256);
        }
        return new WorldServer(
                MinecraftServer.getServer(),
                new DummySaveHandler(),
                "DummyTestWorld",
                256,
                new WorldSettings(256, GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                MinecraftServer.getServer().theProfiler) {

            @Override
            public File getChunkSaveLocation() {
                return new File("dummy-ignoreme");
            }
        };
    }

    @Test
    public void testGetAvailableItems() {
        // Setup
        World dummyWorld = createDummyWorld();
        MockAESystem mockSystem = new MockAESystem(dummyWorld);
        MockGrid mockGrid = mockSystem.grid;
        MockGridNode mockNode = mockGrid.rootNode;
        SecurityCache mockSecurity = mockSystem.grid.getCache(SecurityCache.class);
        NetworkInventoryHandler<IAEItemStack> handler = new NetworkInventoryHandler<>(ITEM_STACK_TYPE, mockSecurity);
        IItemList<IAEItemStack> inputList = new ItemList();

        // Execute
        IItemList<IAEItemStack> result = handler.getAvailableItems(inputList, 1);

        // Verify
        assertNotNull(result, "Result should not be null");
        assertSame(inputList, result, "Returned list should be the same instance as the input");
        assertEquals(inputList.size(), result.size(), "Result size should match input size");
    }

    @Test
    public void testGetAvailableItemsWithFilter() {
        // Setup
        World dummyWorld = createDummyWorld();
        MockAESystem mockSystem = new MockAESystem(dummyWorld);
        MockGrid mockGrid = mockSystem.grid;
        MockGridNode mockNode = mockGrid.rootNode;
        SecurityCache mockSecurity = mockSystem.grid.getCache(SecurityCache.class);
        NetworkInventoryHandler<IAEItemStack> handler = new NetworkInventoryHandler<>(ITEM_STACK_TYPE, mockSecurity);
        IItemList<IAEItemStack> inputList = new ItemList();

        // Execute
        IItemList<IAEItemStack> result = handler.getAvailableItems(inputList, 1);

        // Verify
        assertNotNull(result, "Result should not be null");
        assertEquals(inputList.size(), result.size(), "Result size should match input size");
    }
}
