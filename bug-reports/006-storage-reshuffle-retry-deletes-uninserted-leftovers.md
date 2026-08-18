# Storage Reshuffle retry deletes uninserted leftovers

- Report ID: BUG-006
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: If a Storage Reshuffle cannot reinsert some extracted items, its automatic retry can erase the only stored record of the leftover after about 12 seconds, permanently deleting those items.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Storage Reshuffle leftover persistence and periodic reinsertion retry

## Preconditions

- A powered ME network with a Storage Reshuffle and at least two item-cell storage handlers.
- A normal storage layout in which the reshuffle can extract a stack but later cannot reinsert all of it. A deterministic test setup can use a nearly full destination capacity and remove or disable the newly freed storage handler after its contents have been extracted but before the injection phase reaches that stack.
- BUILD permission if the network is secured.

The supported reshuffle, cell removal/channel loss, and server tick retry are sufficient. No modified client, packet forgery, corrupt data, or hostile inventory implementation is needed.

## Exact reproduction steps

1. Build a powered network with a Storage Reshuffle, multiple cell handlers, and little or no spare capacity outside the source cell that will be processed late in extraction.
2. Put a recognizable stack, such as 64 diamonds, in that source cell.
3. Open the Storage Reshuffle GUI and press Start.
4. After the reshuffle extracts the diamonds but before their injection completes, remove that source cell or make its Drive lose its channel. This ordinary topology change removes the capacity that extraction had temporarily freed.
5. Allow the reshuffle to finish. Its report/pending state should show the diamonds as unable to inject rather than present in network storage.
6. Keep the Storage Reshuffle loaded and leave the network without room for the diamonds.
7. Wait at least 241 tile ticks (about 12 seconds at 20 TPS) for `returnPendingItems` to run.
8. Restore ample storage capacity and wait again.

A focused deterministic test can bypass the timing setup: place one known stack in the tile's `cantInject` list, use the real network monitor configured to return that same full leftover, and tick the tile 241 times.

## Expected result

An unsuccessful retry must retain exactly the returned leftover for another attempt and for world-NBT persistence. Once capacity is restored, the full diamond stack should reappear in network storage. The pending record must not be cleared until insertion returns null.

## Actual predicted result

The retry obtains the `cantInject` iterator's current diamond entry and calls the monitor. If insertion returns a nonnull leftover of the same type, `cantInject.add(res)` finds the existing keyed record and adds the leftover amount into that same record. The code then calls `i.remove()`, whose item-list iterator removes that keyed record from the backing set. The entry—now containing the original plus returned count—is deleted entirely. Later retries see an empty list, NBT saves no leftover, and restoring capacity cannot recover the diamonds.

## Player-visible symptom

The reshuffle initially reports or retains items that could not be placed, then those items never return even after more cells are added. The loss occurs silently during the machine's background retry, not at the moment capacity is restored.

## Reachability proof

`ReshuffleTask` extracts actual stacks with `Actionable.MODULATE` and queues them for reinsertion. During its INJECTION phase, any returned remainder is added to the tile-owned `cantInject` list before the processed queue entry is removed. This is the intended durable ownership handoff. `TileStorageReshuffle.writeToNBT_TileStorageReshuffle` saves `cantInject`, and the read path restores it, so a leftover remains owned safely across a save/reload until retry logic changes it.

When no active task exists, the tile increments `count`. At `count >= 240`, a nonempty `cantInject` triggers `returnPendingItems`. That method iterates the same list it uses as the destination for a failed retry.

`cantInject` is an `IAEStackList`, which dispatches each stack type to a keyed `ItemList`/`FluidList`. `ItemList.add` does not create a second record for the same item: it looks up the existing record and mutates its amount with `st.add(option)`. The iterator used here snapshots references to those backing records, and its `remove` deletes the current record from the backing hash set. Consequently, add-then-remove is not replacement; it mutates and then deletes the sole keyed record.

A full insertion failure is sufficient and especially clear: for an entry of 64 diamonds, the network returns a 64-diamond leftover. `add` changes the stored record to 128, then iterator removal deletes it. A partial failure has the same loss property: the accepted portion enters storage, while the returned remainder is merged into and then removed with the current record.

Ordinary reshuffle execution can create `cantInject`. `ReshuffleTask.processNextBatch` explicitly adds any injection remainder to it. Capacity can change while the multi-tick task runs through normal cell removal, Drive channel loss, partition changes, or another permitted topology transition. The report/retry feature exists specifically to handle such failed placement; the candidate does not depend on an impossible state.

## Root cause

`TileStorageReshuffle.returnPendingItems` treats a keyed aggregate list like a queue of independent objects. It writes a failed result back into the same collection before removing the current key. Because same-type `add` merges into the current record, the subsequent iterator removal deletes both the old entry and the newly returned ownership record.

The same add-before-remove pattern also appears in `ReshuffleTask.returnPendingItems`, but there the source iterators normally refer to distinct `extracted`/`injectQueue` collections and the destination is `cantInject`; it does not alias in the ordinary task-cancel path. The destructive alias is the tile's periodic retry, where source and destination are exactly the same list.

## Execution path

1. A normal reshuffle extracts the diamond stack from a cell into a task buffer.
2. Available network capacity changes before that stack's reinsertion.
3. `ReshuffleTask.processNextBatch` calls the network monitor; it returns a nonnull diamond remainder.
4. The task adds that remainder to tile-owned `cantInject` and removes the processed `injectQueue` entry.
5. After the task finishes, `TileStorageReshuffle.Tick_TileStorageReshuffle` keeps the pending list and counts idle ticks.
6. At 240 ticks, the tile calls `returnPendingItems` and retrieves the diamond entry from `cantInject.iterator()`.
7. The monitor again returns a same-type nonnull leftover because storage remains full.
8. `cantInject.add(res)` dispatches to `ItemList.add`, which finds and increments the existing record.
9. `i.remove()` dispatches back through `IAEStackList` to the current `ItemList` iterator and removes that record from the backing set.
10. `cantInject` becomes empty; later retries and NBT saves contain no diamond ownership record.

## Code evidence

- `src/main/java/appeng/helpers/ReshuffleTask.java`, INJECTION phase, lines 219-248: every network insertion remainder is transferred into `cantInject` before its task-queue entry is removed.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, NBT write/read, lines 106-120: `cantInject` is the durable owner of failed results across world reloads.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, tick handler, lines 124-144: a nonempty pending list is retried automatically after the idle counter reaches 240.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, `returnPendingItems`, lines 243-258: on a nonnull result the method adds it to `cantInject` and then removes the currently iterated `cantInject` entry.
- `src/main/java/appeng/util/item/IAEStackList.java`, `add`, lines 39-45: the aggregate list delegates same-type additions to that type's keyed list.
- `src/main/java/appeng/util/item/IAEStackList.java`, `iterator`, lines 111-140: iterator removal delegates to the current underlying type-list iterator.
- `src/main/java/appeng/util/item/ItemList.java`, `add`, lines 46-61: an equal item updates the existing record with `st.add(option)` rather than installing a separate leftover record.
- `src/main/java/appeng/util/item/ItemList.java`, iterator/remove, lines 193-250: removal deletes the current record from the backing item set. `FluidList` follows the same keyed merge/remove behavior for fluid leftovers.
- `src/main/java/appeng/me/cache/NetworkMonitor.java`, `injectItems`, lines 184 onward: a legitimate network insertion returns unaccepted input and permits a `ReshuffleActionSource` through the task lock.

## Why existing validation does not prevent it

The network storage API behaves as designed: it returns exactly what it cannot accept. The reshuffle also correctly captures that remainder on the first task insertion. The failure is entirely in ownership bookkeeping during a later retry.

The broad `try/catch` around `returnPendingItems` does not help because the item-list operations need not throw. The iterator is specifically implemented to support backing-set removal while iterating its snapshot. The destructive sequence completes normally and silently.

World NBT protects the list only before the faulty retry. Once `i.remove()` deletes the entry, the next save faithfully records an empty list. The block has no independent inventory containing the missing stack.

## Minimal fix direction

Do not add a retry remainder back to the collection being iterated. Remove or decrement the old record first, then add a copy of the returned remainder after iteration, or update the current record's amount in place without calling iterator removal. A simple safe structure is a fresh `IAEStackList stillPending`: inject every old entry, add each nonnull remainder to the new list, then replace `cantInject` after the loop.

Use the same helper for retry and cancellation bookkeeping so partial acceptance, null monitors, exceptions, and all registered stack types preserve the invariant `old pending = accepted + new pending`.

## Regression-test proposal

- Test setup: Construct a `TileStorageReshuffle` on a real or mock storage grid whose item monitor returns the complete input for injection. Seed `cantInject` through the normal task-remainder handoff or NBT with exactly 64 diamonds.
- Initial state: Assert the tile reports one pending type and 64 pending items.
- Triggering action: Advance 241 tile ticks so the automatic retry runs while the monitor still rejects the full stack.
- Expected assertion: Network storage accepted zero and the pending list still contains exactly 64 diamonds; after making the monitor accept and advancing another retry interval, storage contains 64 and pending is empty.
- Incorrect behavior the test must prevent: The failed retry clearing the pending list even though the network accepted nothing.
- Suggested style: A focused unit test for full and partial remainders across item and fluid list types, plus a HorizonQA workflow that removes capacity during a live reshuffle and restores it after the retry.

## Runtime confirmation

Not runtime-confirmed. Static proof covers normal creation and persistence of `cantInject`, the deterministic retry schedule, legitimate insertion leftovers, exact same-key `add` behavior, and exact iterator removal from the backing collection.

## Remaining uncertainty

The easiest manual timing for creating a task remainder depends on network size and handler order. The core failure is deterministic once any ordinary reshuffle remainder exists. A focused test can establish that state without relying on manual timing, while a multi-handler game test should confirm the end-to-end player workflow.
