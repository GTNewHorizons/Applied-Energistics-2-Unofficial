# ME Storage Reshuffle

## Overview

The **ME Storage Reshuffle** block is a powerful tool that reorganizes your entire ME network storage based on storage priority, partitions, and cell configurations. It allows you to redistribute items, fluids, and essentia to their optimal storage locations.

---

## What It Does

The reshuffle operation processes every item type in your ME network and redistributes them according to:

1. **Storage Priority** - Higher priority storage cells receive items first
2. **Partitioned Cells** - Items configured in cell partitions go to those cells
3. **Digital Singularities** - Items with allocated singularity storage move there
4. **Cell Type & Capacity** - Optimizes distribution from lower to higher capacity cells

### Cross-Dimensional Support

The reshuffle block processes storage across **all dimensions**:
- Works with Quantum Network Bridges
- Accesses drives in unloaded chunks
- Handles storage in Overworld, Nether, End, and modded dimensions

---

## The Reshuffle Block

### Crafting

Place the **ME Storage Reshuffle** block adjacent to any cable in your ME network.

### Placement

- Connect to your ME network via any cable side
- Requires ME network power to operate
- Must be on the same network as the storage you want to reshuffle

### Block States

The block's front face changes based on the current filter mode and activity:

| Filter Mode | Idle Texture | Active Texture |
|-------------|--------------|----------------|
| **All Types** | Purple arrows | Purple arrows + animation |
| **Items Only** | Green arrows | Green arrows + animation |
| **Fluids Only** | Blue arrows | Blue arrows + animation |

---

## GUI Interface

### Opening the GUI

Right-click the **ME Storage Reshuffle** block to open its interface.

### GUI Layout

```
┌─────────────────────────────────────────┐
│  Filter:                  Protection:   │
│  [ALL] [Items] [Fluids]   [Void] [OW]  │
├─────────────────────────────────────────┤
│  [START RESHUFFLE]  [Scan]              │
├─────────────────────────────────────────┤
│  Status: Idle                           │
│  ████████████████░░░░░░░░░░░░░░ 65%     │
│  4,523 / 6,900 types processed          │
├─────────────────────────────────────────┤
│  Report:                                │
│  (scrollable text area)                 │
└─────────────────────────────────────────┘
```

---

## GUI Controls

### Filter Buttons

Select which storage types to reshuffle:

| Button | Icon | Description |
|--------|------|-------------|
| **All** | Purple shuffle arrows | Reshuffles items, fluids, and essentia |
| **Items** | Green shuffle arrows | Only reshuffles item storage |
| **Fluids** | Blue shuffle arrows | Only reshuffles fluid storage |

### Protection Buttons

| Button | Icon | Description |
|--------|------|-------------|
| **Void** | Shield icon | Prevents items from being voided (recommended: ON) |
| **Overwrite** | Lock icon | Prevents overwriting existing storage allocations |

### Action Buttons

| Button | Function |
|--------|----------|
| **START RESHUFFLE** | Begins the reshuffle operation |
| **Scan** | Analyzes network without reshuffling (generates preview report) |

### Status Display

Shows:
- **Current state**: Idle, Scanning, Reshuffling, or Complete
- **Progress bar**: Visual indicator with percentage
- **Counter**: Types processed / Total types

### Report Area

Scrollable text area displaying:
- **Scan Report**: Network statistics, cell counts, storage analysis
- **Reshuffle Report**: Completion summary, item changes, integrity check

---

## Using Reshuffle

### Step 1: Scan the Network (Optional)

Click **[Scan]** to analyze your network without making changes:

```
═══════ Network Scan Report ═══════

── Stack Types ──
  Items: 6,900 types (3,456,789 total)
  Fluids: 123 types (98,765,432 mB)
  Essentia: 45 types (12,345 total)

── Storage Cells ──
  Total Cells: 847

── Cell Types ──
  16384k Storage Cell: 425
  4096k Storage Cell: 198
  1024k Fluid Cell: 124
  Digital Singularity: 67
  Quantum Storage Cell: 33

── Reshuffle Estimate ──
  Potential Freed Bytes: 2,456,789
```

### Step 2: Configure Options

- **Select filter mode**: Choose which types to reshuffle
- **Enable protections**:
  - ✅ **Void Protection** - Recommended for safety
  - ⚠️ **Overwrite Protection** - Use when testing

### Step 3: Start Reshuffle

Click **[START RESHUFFLE]** to begin:

1. System locks storage (prevents external changes)
2. Progress bar updates every 500 types (10% increments)
3. Report generates upon completion
4. Storage unlocks automatically

### Progress Updates

During operation, the GUI shows:
```
Status: Reshuffling
██████████████████████░░░░░░░░ 72%
5,000 / 6,900 types processed
```

### Completion Report

```
═══════ Reshuffle Report ═══════

Duration: 25.96s
Mode: All Types | Void Protection: ON | Overwrite Protection: OFF

── Processing Stats ──
  Processed: 6,900 | Skipped: 23

── Storage Totals ──
  Item Types: 6,900 → 6,899 (-1)
  Total Stacks: 3,456,789 → 3,456,789 (0)

── Item Changes ──
  Gained: 234 types (+1,234,567 items)
  Lost: 235 types (-1,234,568 items)
  Unchanged: 6,430 types

── Top Lost Items ──
  • Cobblestone -50,000 (100,000 → 50,000)
  • Dirt -25,000 (50,000 → 25,000)
  ... and 233 more

── Top Gained Items ──
  • Diamond +10,000 (5,000 → 15,000)
  • Gold Ingot +8,000 (2,000 → 10,000)
  ... and 232 more

✓ No net change - storage integrity verified
═══════════════════════════════
```

---

## Protection Systems

### Void Protection (Recommended: ON)

**Prevents item loss** during reshuffle operations.

**How it works:**
1. Before extracting an item, the system simulates re-injection
2. If the simulation shows items would be voided, that type is **skipped**
3. Skipped items remain in their original location

**When items might void:**
- Storage is full and can't accept items back
- Partitioned cells reject the items after extraction
- Network configuration changed during operation

**Example:**
```
[Void Protection: ON]
- Processing: Diamond (1000x)
- Simulation: Only 950 can be re-injected
- Action: SKIP (prevents loss of 50 diamonds)
```

### Overwrite Protection

**Prevents unnecessary storage moves** that would just return items to the same location.

**Use when:**
- Testing reshuffle behavior
- You want items to stay where they are unless there's a better location
- Avoiding unnecessary cell wear (if that's a concern in your modpack)

---

## Advanced Features

### Batched Processing

The reshuffle operates in **batches** to prevent server lag:
- **Default**: 500 types per server tick
- Processes large networks smoothly
- Prevents freezing on networks with 10,000+ item types

### Storage Locking

During reshuffle, the storage grid is **locked**:
- External insertions/extractions are blocked
- Autocrafting is paused
- Pattern/crafting job changes are prevented

**Warning**: Do not start reshuffle if crafting jobs are running!

### Crafting Detection

The system checks for active crafting jobs before starting:
```
❌ Cannot start reshuffle: Crafting jobs are still running.
   Please wait for them to complete.
```

### Log File Generation

For debugging, complete operation logs are written to:
```
minecraft/logs/reshuffle_<timestamp>.log
```

Contains:
- Every item processed
- Skip reasons
- Simulation results
- Error messages

---

## Technical Details

### Cell Detection

The reshuffle scans all cell providers in the network:

1. **Primary**: `getAllCellProviders()` from GridStorageCache
2. **Iterates**: Each ICellProvider's `getCellArray()`
3. **Detects**: Cell type, tier, and capacity
4. **Supports**:
   - Standard Storage Cells (1k - 16384k)
   - Fluid Cells (single & multi-fluid variants)
   - Quantum Storage Cells
   - Digital Singularities
   - Artificial Universe cells
   - Essentia Cells
   - Block Container cells

### Item Movement

Each item type is processed:
```java
1. Extract all items of this type
2. If (void protection) {
      Simulate re-injection
      If (would void) skip to next type
   }
3. Re-inject items into network
4. Network routes to optimal storage
```

### Storage Optimization

The system prioritizes **higher capacity cells**:
- Items in 1k cells move to 4k cells (if available)
- Items in 4k cells move to 16k cells (if available)
- Final report shows cell usage optimization

**Example:**
```
Before: 16 × 1k cells (fully used)
After:  8 × 16k cells (optimized)
```

---

## Performance

### Network Size vs. Duration

| Item Types | Estimated Time |
|-----------|----------------|
| 1,000 | ~2 seconds |
| 5,000 | ~10 seconds |
| 10,000 | ~20 seconds |
| 50,000 | ~100 seconds |

*Times vary based on server TPS and network complexity*

### Optimization Tips

1. **Use higher capacity cells** - Faster to process
2. **Partition cells** - Reduces routing calculations
3. **Scan first** - Preview changes before committing
4. **Close unnecessary GUIs** - Frees up network bandwidth

---

## Troubleshooting

### "Crafting jobs are running"

**Problem**: Cannot start reshuffle while crafts are active

**Solution**: Wait for all crafting jobs to complete or cancel them

---

### Items are skipped

**Problem**: Many items show as "Skipped" in the report

**Cause**: Void protection detected potential item loss

**Solutions**:
1. Add more storage capacity
2. Check partition configurations
3. Review storage priorities

---

### Progress stops at X%

**Problem**: Reshuffle seems frozen

**Check**:
1. Is the GUI still open? (Required for operation)
2. Server TPS - Low TPS slows processing
3. Check logs for errors

---

### Report shows net change

**Problem**: Report warns "items were voided"

**Explanation**: 
- This can happen if autocrafting is running during reshuffle
- External mods inserting/extracting items
- Network changes during operation

**Prevention**: Stop all automation before reshuffling

---

## Best Practices

### ✅ Do

- **Scan first** - Preview changes before starting
- **Enable void protection** - Prevents item loss
- **Stop autocrafting** - Prevents interference
- **Use during low activity** - Better performance
- **Have spare storage** - Ensures items can be redistributed

### ❌ Don't

- Don't close the GUI during reshuffle
- Don't start with active crafting jobs
- Don't disable void protection unless you're sure
- Don't run reshuffle during high server load

---

## Localization

All GUI text and tooltips support full localization via `en_US.lang`:

```properties
gui.appliedenergistics2.reshuffle.start=Start Reshuffle
gui.appliedenergistics2.reshuffle.scan=Scan Network
gui.tooltips.appliedenergistics2.ReshuffleStorageAll=All Types
gui.tooltips.appliedenergistics2.ReshuffleStorageAllDesc=Reshuffles all storage types
gui.tooltips.appliedenergistics2.ReshuffleVoidProtection=Void Protection
gui.tooltips.appliedenergistics2.ReshuffleVoidProtectionDesc=Prevents items from being voided during reshuffle operations
```

---

## FAQ

**Q: Can I run reshuffle on a live server?**  
A: Yes, but warn players and ensure autocrafting is idle.

**Q: Does reshuffle work with AE2 Fluid Crafting?**  
A: Yes, fully supported including Quantum Fluid cells and Singularities.

**Q: Will reshuffle damage my storage cells?**  
A: No, it's a safe operation that only moves items internally.

**Q: Can I cancel a reshuffle?**  
A: Yes, close the GUI or click Cancel (items already processed won't roll back).

**Q: Does it work with Gregtech Digital Storage?**  
A: Yes, supports all Gregtech storage cell types.

---

## Summary

The **ME Storage Reshuffle** block is a powerful maintenance tool for optimizing ME network storage. Use it after:

- Adding new storage cells
- Changing storage priorities
- Configuring partitions
- Adding Digital Singularities
- Major network reorganization

Always **scan first**, enable **void protection**, and ensure **crafting is idle** for best results! 🎯


Hovering over the button shows:
- Current mode (All Types / Items Only / Fluids Only)
- Void Protection status (ON/OFF)
- All available controls
- Progress percentage (when running)

## Configuration Options

### Mode Selection

| Mode | Description |
|------|-------------|
| **All Types** | Reshuffles both items and fluids |
| **Items Only** | Only reshuffles item storage |
| **Fluids Only** | Only reshuffles fluid storage |

### Void Protection (Default: ON)

When enabled, the system **simulates** each extract/inject operation before performing it. If the simulation shows that items would be lost (voided), that item type is **skipped**.

**Why items might be voided:**
- Storage is full and can't accept the items back
- Partitioned cells reject the items
- Network issues during the operation

### Overwrite Protection

When enabled (via Ctrl+click), the system performs additional validation before moving items to prevent unnecessary operations where items would simply return to their original location.

## How Reshuffle Works Internally

### Cross-Dimensional Storage Support

The reshuffle feature works across **all dimensions** in your ME network:
- Drives in the Overworld, Nether, End, and other dimensions are all processed
- Uses Quantum Network Bridges to access storage in other dimensions
- Cell counting and item movement works regardless of chunk loading status
- The grid's cell providers are tracked globally, not per-dimension

### Cell Detection

The system uses multiple methods to find all storage cells:

1. **Primary Method**: `getCellArray()` from each ICellProvider
   - Works even if the drive is in an unloaded chunk in another dimension
   - Retrieves cell handlers registered with the ME network
   - Determines cell tier by examining handler metadata and total bytes

2. **Fallback Method**: Direct inventory access for loaded drives
   - Only used when getCellArray() returns no results
   - Reads ItemStacks directly from TileDrive/TileChest inventories
   - Parses cell type and tier from item metadata

3. **Cell Type Detection**:
   - Uses `IChestOrDrive.getCellType(slot)` interface method (0=item, 1=fluid, 2=essentia)
   - Falls back to name-based detection for unknown types
   - Handles both standard AE2 cells and modded cells (AE2 Fluid Crafting, etc.)

### Item Tracking

Each item type is tracked using a **unique key** based on:
- Item registry name + damage value
- NBT tag hash (if present)
- Fluid name + NBT hash (for fluids)
- **Not** including stack size (to prevent duplicate tracking)

This ensures that the same item type is always recognized as identical, even if quantities change or items move between cells.

### Processing Loop

For each item type in storage:

```
1. Snapshot the current count
2. SIMULATE extract + inject to check for voiding
3. If void protection is ON and simulation would void items:
   -> Skip this item and log reason
4. Otherwise:
   -> Extract the entire stack
   -> Re-inject it (ME network routes to optimal location)
5. Log the before/after counts
6. Continue to next item
```

### Batching and Progress

- Processes **100 item types per server tick**
- Prevents lag on large networks (10,000+ item types)
- Progress reported to player every **10%** (in chat)
- Can be cancelled mid-operation (processed items stay moved, rest stay put)

## Safety Features

### No Reshuffle During Active Crafting

The reshuffle operation is **blocked** when crafting jobs are active in the network. This prevents:
- Items being moved while crafting needs them
- Crafting jobs failing due to missing ingredients
- Potential item duplication or loss

You'll see a message: "Cannot reshuffle - X crafting job(s) are currently active"

**Solution**: Wait for all crafting jobs to complete before reshuffling.

### Batched Processing

- Items are processed in batches of **100 item types per tick**
- Prevents server freezing on large networks
- Allows the operation to be cancelled mid-way

### Large Network Confirmation

Networks with **1,000+ unique item types** require confirmation:
- Click once to see the warning
- Hold **Shift** and click again to confirm
- Or just click again without shift to proceed anyway

### Cancellation

- Click the button while running to **cancel** the operation
- Already-processed items remain in their new locations
- Unprocessed items stay where they were

### Error Handling

- Individual item failures are logged and skipped
- The operation continues with remaining items
- Errors don't cause item loss

## The Report

After completion, a detailed report is displayed in chat:

```
═══════ Reshuffle Report ═══════
Duration: 2.34s
Mode: ALL | Void Protection: ON | Overwrite Protection: OFF

── Processing Stats ──
  Processed: 1,234 | Skipped: 5

── Storage Totals ──
  Item Types: 1,234 → 1,234 (0)
  Total Items: 5,678,901 → 5,678,901 (0)

── Item Changes ──
  Gained: 0 types (+0 items)
  Lost: 0 types (-0 items)  
  Unchanged: 1,234 types

✓ No net change - storage integrity verified
═══════════════════════════════
```

Or if items were gained/lost during the operation (due to autocrafts or other network activity):

```
═══════ Reshuffle Report ═══════
Duration: 25.96s
Mode: ALL | Void Protection: ON | Overwrite Protection: OFF

── Processing Stats ──
  Processed: 7,263 | Skipped: 173

── Storage Totals ──
  Item Types: 7,263 → 7,042 (-221)
  Total Items: 3,791,312,595,817,325 → 3,791,306,788,793,060 (-5,807,024,265)

── Item Changes ──
  Gained: 568 types (+68,326,480,785 items)
  Lost: 661 types (-74,133,500,954 items)
  Unchanged: 5,959 types

── Top Lost Items ──
  • Osmium Ingot -2,740,002,000 (88,858,866,343,166 → 88,856,126,341,166)
  • Electrum Dust -2,087,984,191 (2,087,984,191 → 0)
  • Steel Plate -2,081,216,208 (2,081,216,208 → 0)
  ... and 656 more

── Top Gained Items ──
  • Titanium Ingot +2,550,291,152 (0 → 2,550,291,152)
  • Iron Dust +2,087,989,289 (0 → 2,087,989,289)
  • Copper Wire +1,938,208,603 (0 → 1,938,208,603)
  ... and 563 more

⚠ Net change: -5,807,024,265 items
  (likely due to ongoing autocrafts or other network activity)
═══════════════════════════════
```

### Report Sections

| Section | Description |
|---------|-------------|
| **Duration** | How long the operation took in seconds |
| **Mode** | Which mode was used (ALL, ITEMS_ONLY, FLUIDS_ONLY) |
| **Void Protection** | Whether void protection was enabled |
| **Overwrite Protection** | Whether overwrite protection was enabled |
| **Processing Stats** | Number of item types processed vs skipped |
| **Storage Totals** | Before/after comparison of total item types and counts |
| **Item Changes** | Summary of item types that gained/lost/remained unchanged |
| **Top Lost Items** | Up to 5 items with largest quantity decreases |
| **Top Gained Items** | Up to 5 items with largest quantity increases |
| **Net Change** | Total items gained or lost (0 = perfect integrity) |

### Understanding Item Changes

The report compares storage **before** and **after** the reshuffle operation by tracking each unique item type.

**Important**: "Gained" and "Lost" refer to **net changes** in item quantities:
- An item appears in "Gained" if its total quantity **increased**
- An item appears in "Lost" if its total quantity **decreased**  
- The same item **cannot** appear in both lists

Changes can occur due to:
- **Ongoing autocrafts** consuming ingredients or producing outputs
- **Other players** adding or removing items from the network
- **External automation** (import/export buses, storage buses, interfaces)
- **Actual movement** during reshuffle doesn't change quantities, just locations

**Net change of 0**: Perfect - storage remained consistent during the operation.

**Non-zero net change**: Normal if you have active autocrafts or automation. The reshuffle itself doesn't void items, but items can change while the operation is running.

## Use Cases

### 1. Setting Up Sticky Cells

You've just configured a Digital Singularity cell to be "sticky" for specific items:
1. Configure the cell's partition with desired items
2. Enable sticky mode on the cell
3. Run **Reshuffle Storage** to move all matching items to the sticky cell

### 2. Reorganizing After Priority Changes

You've changed storage priorities:
1. Update priority settings on your ME Drives/Storage Buses
2. Run **Reshuffle Storage** to redistribute items based on new priorities

### 3. Cleaning Up After Bulk Imports

After importing many items via IO Port or other means:
1. Items may be scattered across various cells
2. Run **Reshuffle Storage** to consolidate items properly

### 4. Optimizing Cell Usage

When you want to fill larger cells first and free up smaller cells:
1. Run **Reshuffle Storage** - it automatically prioritizes filling largest cells first
2. This can free up smaller cells that you can then remove or repurpose

### 5. Mode-Specific Operations

You only want to reorganize fluids without touching items:
1. Right-click the button to set mode to "Fluids Only"
2. Left-click to start
3. Only fluid storage is reshuffled, items remain untouched

## Technical Details

### Debug Logging

When `DEBUG_LOGGING_ENABLED` is set to `true` in `ReshuffleLogger.java`, a detailed log file is written to the game's `logs/` folder.

**Log file location**: `logs/reshuffle_<playername>_<timestamp>.log`

**Log contents include**:
- Configuration (mode, void protection, overwrite protection)
- Before/after storage snapshots
- Each item extraction and injection
- Skip reasons for each skipped item
- Batch completion summaries
- Progress milestones (10%, 20%, etc.)
- Final report summary
- Error details if any

**Example log output**:
```
================================================================================
AE2 RESHUFFLE OPERATION LOG
================================================================================
Started: 2026-02-14 16:30:45
Player: Steve
Log File: /minecraft/logs/reshuffle_Steve_2026-02-14_16-30-45.log
================================================================================

CONFIGURATION:
  Mode: ALL
  Void Protection: ENABLED
  Overwrite Protection: DISABLED

BEFORE SNAPSHOT:
  Unique item types: 7,263
  Total item count: 3,791,312,595,817,325

================================================================================
PROCESSING LOG:
================================================================================
[16:30:45.123] [1/7263] Processing: Diamond x1,000
[16:30:45.124]   -> Extracted: 1,000
[16:30:45.125]   -> Injected: 1,000 (complete)
...
```

**To disable logging**: Set `DEBUG_LOGGING_ENABLED = false` in `ReshuffleLogger.java`

### Permissions Required

- **Inject** permission (to insert items)
- **Extract** permission (to remove items)

### Network Requirements

- Network must be **powered**
- Player must have access via **Security Terminal** (if configured)

### Implementation Notes

- Uses `Actionable.SIMULATE` for void protection checks
- Uses `Actionable.MODULATE` for actual item movement
- Processes via `detectAndSendChanges()` each server tick
- State synchronized to client via `@GuiSync` annotations

## Troubleshooting

### Operation Stops / Only Shows Partial Progress

The most common cause is **closing the terminal GUI**. The reshuffle operation runs inside the container, so:
- **Keep the terminal open** during the entire operation
- If you close the GUI, the reshuffle is cancelled
- You'll see a message: "Reshuffle cancelled - GUI closed"

Other possible causes:
- Check server logs for errors
- May indicate network connectivity issues
- Try with a smaller batch of items first
- Verify the network has sufficient power

### Items Not Moving to Expected Location

- Verify sticky cell configuration
- Check storage priorities (higher priority = filled first)
- Ensure target storage has available space
- Confirm partition settings are correct
- Check that cells aren't full (63 type limit per cell)

### "No Permission" Error

- Check Security Terminal settings
- Ensure your player has both Inject and Extract permissions
- Verify you have access to the terminal type you're using

### "No Power" Error

- Verify network has sufficient power reserves
- Check Energy Acceptor connections
- Ensure power generation is active
- Large operations may spike power usage temporarily

### "Cannot Reshuffle - Crafting Active" Error

- Wait for all crafting jobs to complete
- Check the Crafting Status screen to monitor active jobs
- Cancel unnecessary crafting jobs if needed

## Additional Information

### Button Icons

The reshuffle button displays different icons based on the current mode:
- **Green shuffle arrows**: Items Only mode
- **Blue shuffle arrows**: Fluids Only mode  
- **Purple shuffle arrows**: All Types mode
- **Red X overlay**: Void Protection is OFF

The icons are located in the `states.png` texture file:
- Items (green): tile (10, 2)
- Fluids (blue): tile (9, 2)
- All (purple): tile (11, 2)
- Red X overlay: tile (12, 1)

Tooltip text updates dynamically to show the current configuration.

### Debug Logging

When `DEBUG_LOGGING_ENABLED` is set to `true` in `ReshuffleLogger.java`, a detailed log file is written to the game's `logs/` folder.

**Log file location**: `logs/reshuffle_<playername>_<timestamp>.log`

**Log contents include**:
- Configuration (mode, void protection, overwrite protection)
- Before/after storage snapshots
- Each item extraction and injection
- Skip reasons for each skipped item
- Batch completion summaries
- Progress milestones (10%, 20%, etc.)
- Final report summary
- Error details if any

**Example log output**:
```
================================================================================
AE2 RESHUFFLE OPERATION LOG
================================================================================
Started: 2026-02-14 16:30:45
Player: Steve
Log File: /minecraft/logs/reshuffle_Steve_2026-02-14_16-30-45.log
================================================================================

CONFIGURATION:
  Mode: ALL
  Void Protection: ENABLED
  Overwrite Protection: DISABLED

BEFORE SNAPSHOT:
  Unique item types: 7,263
  Total item count: 3,791,312,595,817,325

================================================================================
PROCESSING LOG:
================================================================================
[16:30:45.123] [1/7263] Processing: Diamond x1,000
[16:30:45.124]   -> Extracted: 1,000
[16:30:45.125]   -> Injected: 1,000 (complete)
...
```

**To disable logging**: Set `DEBUG_LOGGING_ENABLED = false` in `ReshuffleLogger.java`

### Permissions Required

- **Inject** permission (to insert items)
- **Extract** permission (to remove items)

### Network Requirements

- Network must be **powered**
- Player must have access via **Security Terminal** (if configured)

### Implementation Notes

- Uses `Actionable.SIMULATE` for void protection checks
- Uses `Actionable.MODULATE` for actual item movement
- Processes via `detectAndSendChanges()` each server tick
- State synchronized to client via `@GuiSync` annotations

