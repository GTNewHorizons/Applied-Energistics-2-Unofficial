package appeng.util.item;

import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import appeng.api.storage.data.IAEItemStack;

/**
 * A custom red-black tree that maintains its items sorted, while achieving O(1) lookup by keeping a hashmap of the item
 * entries with some extra space cost. No references are kept to the stacks that are added. They are copied first. <br/>
 * The set operations (split, union, etc) are algorithms from this paper. <a href="https://arxiv.org/abs/1602.02120">
 * "Parallel Ordered Sets Using Join." Guy B., et. al. 12 Nov. 2016. DOI: 10.1145/2935764.2935768 </a>.
 */
public class ItemTree implements Iterable<IAEItemStack> {

    @Nonnull
    private Comparator<IAEItemStack> comparator;
    private final HashMap<IAEItemStack, ItemEntry> items;
    private ItemEntry root;
    private ItemEntry min = null;
    private boolean dirtyCache;

    public ItemTree(@Nonnull Comparator<IAEItemStack> comparator) {
        this.comparator = comparator;
        this.items = new HashMap<>();
        this.root = null;
        this.dirtyCache = false;
    }

    public void setComparator(@Nonnull Comparator<IAEItemStack> comparator) {
        this.comparator = comparator;
        Set<IAEItemStack> stacks = new HashSet<>(this.items.keySet());
        this.clear();
        for (IAEItemStack s : stacks) {
            this.add(s);
        }
    }

    /**
     * Adds the stack into the tree. No action taken if stack exists. Performance O(log n) The stack is copied.
     *
     * @param stack must be nonnull.
     * @return whether the stack was added
     */
    public boolean add(@Nonnull IAEItemStack stack) {
        if (items.containsKey(stack)) {
            return false;
        }
        ItemEntry toAdd = new ItemEntry(stack.copy(), ItemEntry.RED, null);
        items.put(toAdd.realStack, toAdd);
        ItemEntry it = root;
        if (it == null) {
            root = toAdd;
            root.color = ItemEntry.BLACK;
        } else {
            ItemEntry parent;
            int cmp;
            do {
                cmp = comparator.compare(it.realStack, stack);
                if (cmp < 0) {
                    parent = it;
                    it = it.right;
                } else if (cmp > 0) {
                    parent = it;
                    it = it.left;
                } else {
                    throw new IllegalStateException("Added an equivalent stack when it shouldn't: " + stack);
                }
            } while (it != null);
            fillEntry(toAdd, parent, cmp > 0);
        }
        this.dirtyCache = true;
        return true;
    }

    private void fillEntry(ItemEntry newEntry, ItemEntry parent, boolean left) {
        if (left) {
            parent.left = newEntry;
        } else {
            parent.right = newEntry;
        }
        newEntry.parent = parent;
        fixRBViolations(newEntry);
    }

    public boolean contains(IAEItemStack stack) {
        return items.containsKey(stack);
    }

    /**
     * Update the stack in the tree by removing/inserting it. No action taken if stack not found. Performance O(log n),
     * but can cause 2 updates.
     *
     * @param stack used to find the stack.
     */
    public void update(@Nonnull IAEItemStack stack) {
        ItemEntry entry = this.items.get(stack);
        if (entry == null) {
            return;
        }
        remove(stack);
        add(stack);
    }

    /**
     * Fix RB violations caused by the entry.
     */
    private void fixRBViolations(@Nonnull ItemEntry entry) {
        entry.color = ItemEntry.RED;
        // Case 0: black parent; we're done -> exit loop.
        while (!isBlack(entry.parent)) {
            // only black nodes can be null
            @Nonnull
            ItemEntry parent = entry.parent;
            // First iteration, gp is nonnull (red cannot be root)
            ItemEntry gp = entry.parent.parent;
            if (gp == null) {
                break;
            }
            boolean leftEntry = isLeft(entry);
            boolean leftParent = isLeft(parent);
            // Case 1: red parent, grandparent = black, uncle = red
            if (!isBlack(gp, !leftParent) && gp.color == ItemEntry.BLACK) {
                // spotless:off
                // Case 1: "Double red":
                //=============================================================
                //              LEFT                        RIGHT
                //               [b] <---- grandparent ----> [b]
                //               / \                         / \
                //  parent --> [r] [r] <----- uncle -----> [r] [r] <-- parent
                //             /                                 \
                //  entry -> [r]                                 [r] <- entry
                //=============================================================
                // Solution: Make parent & uncle black, grandparent to red.
                // Propagate fix up to grandparent.
                // spotless:on
                parent.color = ItemEntry.BLACK;
                if (leftParent) {
                    gp.right.color = ItemEntry.BLACK;
                } else {
                    gp.left.color = ItemEntry.BLACK;
                }
                gp.color = ItemEntry.RED;
                entry = gp;
                continue;
            }
            // Case 2: red parent, grandparent = black, uncle = black
            // Some references split this into 2 and 3 for the < or \ cases.
            // But we'll split this into 2A, 2B.
            if (isBlack(gp) && isBlack(gp, !leftParent)) {
                if (leftEntry != leftParent) {
                    // spotless:off
                    // Case 2A: Inner node (<):
                    //=========================================================
                    //               [b] <---- grandparent
                    //               / \
                    //  parent --> [r] [b] <-- uncle
                    //             / \
                    //           [?] [r] <---- entry
                    //=========================================================
                    // Solution: rotate towards outer, then fall through to
                    // case 2B.
                    // spotless:on
                    rotate(entry, parent, !leftEntry);
                    // Update refs. we now point to the old parent, which has
                    // been rotated down.
                    entry = parent;
                    parent = entry.parent;
                    leftEntry = leftParent;
                }
                // spotless:off
                // Case 2B: In a line (/):
                //=========================================================
                //               [b] <---- grandparent
                //               / \
                //  parent --> [r] [b] <-- uncle
                //             / \
                //  entry -> [r] [1]
                //=========================================================
                // Solution: rotate, recolor parent + grandparent
                //=========================================================
                //               [b] <----- parent
                //               / \
                //   entry --> [r] [r] <--- grandparent
                //                 / \
                //               [1] [b] <- uncle
                //=========================================================
                // spotless:on
                rotate(parent, gp, !leftEntry);
                parent.color = ItemEntry.BLACK;
                gp.color = ItemEntry.RED;
            }
        }
        if (entry.parent == null) {
            root = entry;
        }
        root.color = ItemEntry.BLACK;
    }

    /**
     * Remove the stack from the tree. Performance O(log n)
     */
    public boolean remove(@Nonnull IAEItemStack stack) {
        ItemEntry entry = items.remove(stack);
        if (entry == null) {
            return false;
        }
        while (entry != null) {
            if (entry.left == entry.right) {
                if (entry.parent == null) {
                    // No parent, no children means tree is now empty
                    this.root = null;
                } else if (entry.color == ItemEntry.RED) {
                    // Case 1-A: Red leaf
                    // We can just remove it and we're done
                    if (isLeft(entry)) {
                        entry.parent.left = null;
                    } else {
                        entry.parent.right = null;
                    }
                } else {
                    // Case 1-B: Black leaf
                    deleteBlackLeaf(entry);
                }
                entry.parent = null;
                break;
            } else if (entry.right == null) {
                // Case 2-L: 1 child. Swap with the child and remove that.
                entry = swapToRemove(entry, entry.left);
            } else if (entry.left == null) {
                // Case 2-R: 1 child. Swap with the child and remove that.
                entry = swapToRemove(entry, entry.right);
            } else {
                // Case 3: 2 childs. Swap with the next largest node, then
                // remove that.
                ItemEntry successor = findMin(entry.right);
                entry = swapToRemove(entry, successor);
            }
        }
        this.dirtyCache = true;
        return true;
    }

    public IAEItemStack findMin() {
        if (root != null && dirtyCache) {
            min = findMin(root);
            dirtyCache = false;
        }
        return min.realStack;
    }

    /**
     * "Swaps" the nodes. No swapping actually takes place! Replaces {@code toRemain.realStack} with
     * {@code toDelete.realstack} and updates the hash map. Does not actually remove {@code toDelete} from the tree.
     *
     * @param toRemain after the swap, the node that will remain
     * @param toDelete after the swap, the node that will be deleted
     * @return toDelete - this node may violate RB tree invariants until it's actually removed.
     */
    private ItemEntry swapToRemove(ItemEntry toRemain, ItemEntry toDelete) {
        toRemain.realStack = toDelete.realStack;
        this.items.put(toDelete.realStack, toRemain);
        return toDelete;
    }

    /**
     * Removes the black leaf from the tree, propagating until RB invariants are restored.
     *
     * @param bLeaf the leaf
     */
    private void deleteBlackLeaf(@Nonnull ItemEntry bLeaf) {
        ItemEntry entry = bLeaf;
        ItemEntry parent = entry.parent;
        boolean entryLeft = isLeft(entry);
        // Cut off bLeaf
        if (bLeaf.parent == null) {
            return;
        } else {
            if (entryLeft) {
                bLeaf.parent.left = null;
            } else {
                bLeaf.parent.right = null;
            }
        }
        // Case D1: bLeaf is the root - we are done.
        while ((parent = bLeaf.parent) != null && bLeaf.color == ItemEntry.BLACK) {
            // Sibling cannot be null, by RB invariant.
            ItemEntry sibling = entryLeft ? parent.right : parent.left;
            if (sibling.color == ItemEntry.RED) {
                // spotless:off
                // Case D3: Sibling is red, so its parent and children
                // are black.
                //=============================================================
                //               [b] <---- parent
                //               / \
                //   bLeaf --> [b] [r] <-- sibling
                //                 / \
                //    nephews -> [b] [b]
                //=============================================================
                // Solution: Rotate left/right at S->P, recolor parent + sibling.
                //=============================================================
                //               [b] <---- sibling
                //               / \
                //  parent --> [r] [b] <-- nephew
                //             / \
                // entry --> [b] [b] <-- nephew
                //=============================================================
                // Fall through to next case
                // spotless:on
                parent.color = ItemEntry.RED;
                sibling.color = ItemEntry.BLACK;
                rotate(sibling, parent, entryLeft);
                sibling = entryLeft ? parent.right : parent.left;
            }
            if (isBlack(sibling.left) && isBlack(sibling.right)) {
                // spotless:off
                // Case D2: All is black
                //=============================================================
                //                 [b] <---- parent
                //                 / \
                //     bLeaf --> [b] [b] <-- sibling
                //                   / \
                // close nephew -> [b] [b] <-- far nephew
                //=============================================================
                // Solution: Recolor, then propagate up.
                //=============================================================
                //       bLeaf --> [b]
                //                 / \
                //               [b] [r]
                //                   / \
                //                 [b] [b]
                //=============================================================
                // spotless:on
                sibling.color = ItemEntry.RED;
                bLeaf = parent;
            } else {
                if (!isBlack(sibling, entryLeft)) {
                    // spotless:off
                    // Case D5: Sibling is black, and close nephew is red and
                    // far nephew is black
                    //=========================================================
                    //                 [?] <---- parent
                    //                 / \
                    //     bLeaf --> [b] [b] <-- sibling
                    //                   / \
                    // close nephew -> [r] [b] <-- far nephew
                    //=========================================================
                    // Solution: Rotate close nephew away from us and recolor.
                    // Fall through to Case D6
                    //=========================================================
                    //                 [?] <---- parent
                    //                 / \
                    //     bLeaf --> [b] [b] <-- close nephew
                    //                   / \
                    //                 [b] [r] <-- sibling
                    //                       \
                    //                       [b] <-- far nephew
                    //=========================================================
                    // spotless:on
                    sibling.color = ItemEntry.RED;
                    if (entryLeft) {
                        sibling.left.color = ItemEntry.BLACK;
                        rotateRight(sibling.left, sibling);
                        sibling = bLeaf.parent.right;
                    } else {
                        sibling.right.color = ItemEntry.BLACK;
                        rotateLeft(sibling.right, sibling);
                        sibling = bLeaf.parent.left;
                    }
                }
                // spotless:off
                // Case D6: Sibling is black, and close nephew is black and
                // far nephew is red
                //=========================================================
                //                 [?] <---- parent
                //                 / \
                //     bLeaf --> [b] [b] <-- sibling
                //                   / \
                // close nephew -> [b] [r] <-- far nephew
                //=========================================================
                // Solution: Rotate sibling towards us, then recolor. Done
                //=========================================================
                //                 [?] <---- sibling
                //                 / \
                //    parent --> [b] [b] <-- far nephew
                //               / \
                //    bLeaf -> [b] [b] <-- close nephew
                //=========================================================
                // spotless:on
                sibling.color = parent.color;
                parent.color = ItemEntry.BLACK;
                if (entryLeft) {
                    sibling.right.color = ItemEntry.BLACK;
                } else {
                    sibling.left.color = ItemEntry.BLACK;
                }
                rotate(sibling, parent, entryLeft);
                bLeaf = this.root;
            }
        }
        bLeaf.color = ItemEntry.BLACK;
        if (bLeaf.parent == null) {
            this.root = bLeaf;
        }
    }

    /**
     * Get the real stack in the tree. Performance O(1)
     *
     * @param stack the "model" stack.
     * @return the item stack that is actually stored in this list, or null if it doesn't exist
     */
    public IAEItemStack get(IAEItemStack stack) {
        ItemEntry lookup = items.get(stack);
        if (lookup != null) {
            return lookup.realStack;
        } else {
            return null;
        }
    }

    /**
     * Get the stack in this tree, else returns the default arg.
     *
     * @param stack query item
     * @param def   to be returned if stack is not present
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public IAEItemStack getOrDefault(IAEItemStack stack, IAEItemStack def) {
        IAEItemStack realStack = get(stack);
        return realStack == null ? def : realStack;
    }

    /**
     * Returns the real size. Note that this includes items that are not "meaningful" (i.e. 0 stack size). Cheaper than
     * getting the meaningful size.
     */
    public int size() {
        return items.size();
    }

    /**
     * Clears the root. (GC *should* be able to pick up on these nodes unless someone is holding a reference outside the
     * tree, which they shouldn't)
     */
    public void clear() {
        this.root = null;
        this.items.clear();
    }

    private static boolean isBlack(ItemEntry entry, boolean left) {
        if (left) {
            return isBlack(entry.left);
        } else {
            return isBlack(entry.right);
        }
    }

    private static boolean isBlack(ItemEntry entry) {
        return entry == null || entry.color == ItemEntry.BLACK;
    }

    private static boolean isLeft(@Nonnull ItemEntry entry) {
        return entry.parent != null && entry.parent.left == entry;
    }

    private void rotate(ItemEntry entry, ItemEntry parent, boolean left) {
        if (left) {
            rotateLeft(entry, parent);
        } else {
            rotateRight(entry, parent);
        }
    }

    private void rotateRight(ItemEntry entry, ItemEntry parent) {
        // spotless:off
        // [P] parent, can be left or right child of grandparent
        // [E] entry
        // [G] grandparent (Nullable)
        // [1], [2], [3] are subtrees (Nullable)
        //=====================================================================
        //                 [G]                          [G]
        //                 /                            /
        //     parent -> [P]                          [E]
        //               / \    rotateRight(E, P)     / \
        //  entry  --> [E] [3]  ===============>>   [1] [P]
        //             / \                              / \
        //           [1] [X]                          [X] [3]
        //=====================================================================
        // spotless:on
        boolean isLeft = isLeft(parent);
        entry.parent = parent.parent;
        if (entry.parent != null) {
            if (isLeft) {
                entry.parent.left = entry;
            } else {
                entry.parent.right = entry;
            }
        } else {
            // We are root
            this.root = entry;
        }
        // Update node X
        parent.left = entry.right;
        if (parent.left != null) {
            parent.left.parent = parent;
        }
        // Update parents
        entry.right = parent;
        parent.parent = entry;
    }

    private void rotateLeft(ItemEntry entry, ItemEntry parent) {
        // spotless:off
        // [P] parent, can be left or right child of grandparent
        // [E] entry
        // [G] grandparent (Nullable)
        // [1], [2], [3] are subtrees (Nullable)
        //=====================================================================
        //                 [G]                          [G]
        //                 /                            /
        //               [P]                          [E]
        //               / \    rotateLeft(E, P)      / \
        //             [1] [E]  ===============>>   [P] [3]
        //                 / \                      / \
        //               [X] [3]                  [1] [X]
        //=====================================================================
        // spotless:on
        boolean isLeft = isLeft(parent);
        entry.parent = parent.parent;
        if (entry.parent != null) {
            if (isLeft) {
                entry.parent.left = entry;
            } else {
                entry.parent.right = entry;
            }
        } else {
            // We are root
            this.root = entry;
        }
        // Update node X
        parent.right = entry.left;
        if (parent.right != null) {
            parent.right.parent = parent;
        }
        // Update parents
        entry.left = parent;
        parent.parent = entry;
    }

    /**
     * Finds the minimum node of a subtree.
     */
    private static ItemEntry findMin(@Nonnull ItemEntry entry) {
        while (entry.left != null) {
            entry = entry.left;
        }
        return entry;
    }

    /**
     * Finds the maximum node of a subtree.
     */
    private static ItemEntry findMax(@Nonnull ItemEntry entry) {
        while (entry.right != null) {
            entry = entry.right;
        }
        return entry;
    }

    public void resetItems() {
        items.forEach((item, entry) -> item.reset());
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    /**
     * Gets the items between the two stacks (if start > end, then undefined behavior)
     *
     * @param start low range of items, inclusive
     * @param end   high range of items, inclusive
     */
    public Collection<IAEItemStack> itemsBetween(IAEItemStack start, IAEItemStack end) {
        ItemEntry it = root;
        List<IAEItemStack> stackList = new ArrayList<>();
        if (root == null) {
            return stackList;
        }
        int cmp;
        // Check for 0 children, (null == null)
        while (it.left != it.right) {
            cmp = comparator.compare(it.realStack, start);
            if (cmp < 0) {
                if (it.right != null) {
                    it = it.right;
                } else {
                    return stackList;
                }
            } else {
                if (it.left != null) {
                    it = it.left;
                } else {
                    break;
                }
            }
        }
        // Crawl back up until the max. Notice that even if we didn't iterate
        // through an entry, the default scratch value of 0 is still true.
        TreeIterator treeIt = new TreeIterator(it);
        while (treeIt.hasNext()) {
            ItemEntry entry = treeIt.next;
            cmp = comparator.compare(entry.realStack, end);
            if (cmp <= 0) {
                stackList.add(treeIt.next());
            } else {
                break;
            }
        }
        return stackList;
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return new TreeIterator(this);
    }

    private static class ItemEntry {

        @Nonnull
        private IAEItemStack realStack;
        private boolean color;
        private ItemEntry parent;
        private ItemEntry left, right;
        private static final boolean RED = false;
        private static final boolean BLACK = true;

        ItemEntry(IAEItemStack stack, boolean color, @Nullable ItemEntry parent) {
            this.realStack = stack;
            this.color = color;
            this.parent = parent;
        }
    }

    /**
     * Iterates through the elements of the item tree.
     */
    private static class TreeIterator implements Iterator<IAEItemStack> {

        private ItemEntry next;

        TreeIterator(@Nonnull ItemTree tree) {
            if (tree.root == null) {
                this.next = null;
            } else {
                this.next = findMin(tree.root);
            }
        }

        TreeIterator(@Nonnull ItemEntry entry) {
            this.next = entry;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public IAEItemStack next() {
            IAEItemStack toReturn = next.realStack;
            if (next.right != null) {
                next = findMin(next.right);
            } else {
                while (next != null && !isLeft(next)) {
                    next = next.parent;
                }
                next = next != null ? next.parent : null;
            }
            return toReturn;
        }
    }
}
