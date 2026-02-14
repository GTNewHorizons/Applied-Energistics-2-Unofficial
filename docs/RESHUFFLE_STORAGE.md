# Reshuffle Storage Feature

## Overview

The **Reshuffle Storage** feature allows you to redistribute all items in your ME network based on storage priority settings. This is useful when you've configured sticky cells, partitioned storage, or changed priority settings and want existing items to be moved to their optimal storage locations.

> ⚠️ **Important**: You must keep the terminal GUI open during the entire reshuffle operation. Closing the GUI will cancel the reshuffle!

## How It Works

### The Process

1. **Snapshot**: Before starting, the system takes a snapshot of all items currently in storage
2. **Extract & Re-inject**: For each item type in storage:
   - The item is **extracted** from wherever it currently resides
   - The item is **re-injected** into the network
   - The ME system's priority routing automatically places items in their optimal location
3. **Report**: After completion, a detailed report shows what changed

### Priority Routing

When items are re-injected, the ME network routes them based on:

1. **Sticky Cells** (highest priority) - Items configured in a cell's partition with "sticky" mode enabled
2. **Storage Priority** - Higher priority storage receives items first
3. **Partitioned Storage** - Cells configured to only accept specific items
4. **Default Storage** - Any remaining space in the network

## Using the Feature

### Button Location

The reshuffle button appears in the ME Terminal GUI, below the pins button (right side of the terminal).

### Controls

| Action | Effect |
|--------|--------|
| **Left-click** | Start the reshuffle operation |
| **Right-click** | Cycle through modes (All → Items → Fluids) |
| **Middle-click** | Toggle void protection ON/OFF |
| **Shift + Left-click** | Start immediately (skip confirmation for large networks) |
| **Ctrl + Left-click** | Enable overwrite protection for this operation |

### Tooltip

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

