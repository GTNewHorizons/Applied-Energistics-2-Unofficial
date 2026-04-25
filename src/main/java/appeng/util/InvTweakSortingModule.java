package appeng.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Level;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import appeng.core.AELog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class InvTweakSortingModule {

    private static InvTweaksItemTree tree = null;
    public static final File MINECRAFT_DIR = Minecraft.getMinecraft().mcDataDir;
    public static final File MINECRAFT_CONFIG_DIR = new File(MINECRAFT_DIR, "config/AppliedEnergistics2");
    public static final File CONFIG_TREE_FILE = new File(MINECRAFT_CONFIG_DIR, "InvTweaksTree.txt");

    public static void init() {
        try {
            if (CONFIG_TREE_FILE.exists()) {
                tree = InvTweaksItemTreeLoader.load(CONFIG_TREE_FILE);
            } else {
                AELog.info("Tree file is missing!");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isLoaded() {
        return tree != null;
    }

    public static int compareItems(ItemStack i, ItemStack j) {
        return InvTweaks.compareItems(i, j, InvTweaks.getItemOrder(i), InvTweaks.getItemOrder(j));
    }

    private static class InvTweaks {

        private static int getItemOrder(ItemStack itemStack) {
            if (itemStack == null) {
                return Integer.MAX_VALUE;
            }

            return tree
                    .getItemOrder(Item.itemRegistry.getNameForObject(itemStack.getItem()), itemStack.getItemDamage());
        }

        static int compareItems(ItemStack i, ItemStack j, int orderI, int orderJ) {
            if (j == null) {
                return -1;
            } else if (i == null || orderI == -1) {
                return 1;
            } else {
                if (orderI == orderJ) {
                    // Items of same keyword orders can have different IDs,
                    // in the case of categories defined by a range of IDs
                    if (i.getItem() == j.getItem()) {
                        boolean iHasName = i.hasDisplayName();
                        boolean jHasName = j.hasDisplayName();
                        if (iHasName || jHasName) {
                            if (!iHasName) {
                                return -1;
                            } else if (!jHasName) {
                                return 1;
                            } else {
                                String iDisplayName = i.getDisplayName();
                                String jDisplayName = j.getDisplayName();

                                if (!iDisplayName.equals(jDisplayName)) {
                                    return iDisplayName.compareTo(jDisplayName);
                                }
                            }
                        }

                        @SuppressWarnings("unchecked")
                        Map<Integer, Integer> iEnchs = EnchantmentHelper.getEnchantments(i);
                        @SuppressWarnings("unchecked")
                        Map<Integer, Integer> jEnchs = EnchantmentHelper.getEnchantments(j);
                        if (iEnchs.size() == jEnchs.size()) {
                            int iEnchMaxId = 0, iEnchMaxLvl = 0;
                            int jEnchMaxId = 0, jEnchMaxLvl = 0;

                            for (Map.Entry<Integer, Integer> ench : iEnchs.entrySet()) {
                                if (ench.getValue() > iEnchMaxLvl) {
                                    iEnchMaxId = ench.getKey();
                                    iEnchMaxLvl = ench.getValue();
                                } else if (ench.getValue() == iEnchMaxLvl && ench.getKey() > iEnchMaxId) {
                                    iEnchMaxId = ench.getKey();
                                }
                            }

                            for (Map.Entry<Integer, Integer> ench : jEnchs.entrySet()) {
                                if (ench.getValue() > jEnchMaxLvl) {
                                    jEnchMaxId = ench.getKey();
                                    jEnchMaxLvl = ench.getValue();
                                } else if (ench.getValue() == jEnchMaxLvl && ench.getKey() > jEnchMaxId) {
                                    jEnchMaxId = ench.getKey();
                                }
                            }

                            if (iEnchMaxId == jEnchMaxId) {
                                if (iEnchMaxLvl == jEnchMaxLvl) {
                                    if (i.getItemDamage() != j.getItemDamage()) {
                                        if (i.isItemStackDamageable()) {
                                            return j.getItemDamage() - i.getItemDamage();
                                        } else {
                                            return i.getItemDamage() - j.getItemDamage();
                                        }
                                    } else {
                                        return j.stackSize - i.stackSize;
                                    }
                                } else {
                                    return jEnchMaxLvl - iEnchMaxLvl;
                                }
                            } else {
                                return jEnchMaxId - iEnchMaxId;
                            }
                        } else {
                            return jEnchs.size() - iEnchs.size();
                        }
                    } else {
                        return ObjectUtils.compare(
                                Item.itemRegistry.getNameForObject(i.getItem()),
                                Item.itemRegistry.getNameForObject(j.getItem()));
                    }
                } else {
                    return orderI - orderJ;
                }
            }
        }
    }

    /**
     * Loads the item tree by parsing the XML file.
     *
     * @author Jimeo Wan
     */
    private static class InvTweaksItemTreeLoader extends DefaultHandler {

        private final static String ATTR_ID = "id";
        private final static String ATTR_DAMAGE = "damage";
        private final static String ATTR_RANGE_DMIN = "dmin"; // Damage ranges
        private final static String ATTR_RANGE_DMAX = "dmax";
        private final static String ATTR_OREDICT_NAME = "oreDictName"; // OreDictionary names

        private static InvTweaksItemTree tree;

        private static int itemOrder;
        private static LinkedList<String> categoryStack;

        private static void init() {
            tree = new InvTweaksItemTree();
            itemOrder = 0;
            categoryStack = new LinkedList<>();
        }

        public synchronized static InvTweaksItemTree load(File file) throws Exception {
            init();

            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();
            parser.parse(file, new InvTweaksItemTreeLoader());

            return tree;
        }

        @Override
        public synchronized void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {

            String rangeDMinAttr = attributes.getValue(ATTR_RANGE_DMIN);
            String oreDictNameAttr = attributes.getValue(ATTR_OREDICT_NAME);

            // Category
            if (attributes.getLength() == 0 || rangeDMinAttr != null) {

                if (categoryStack.isEmpty()) {
                    // Root category
                    tree.setRootCategory(new InvTweaksItemTreeCategory(name));
                } else {
                    // Normal category
                    tree.addCategory(categoryStack.getLast(), new InvTweaksItemTreeCategory(name));
                }

                // Handle damage ranges
                if (rangeDMinAttr != null) {
                    String id = attributes.getValue(ATTR_ID);
                    int rangeDMin = Integer.parseInt(rangeDMinAttr);
                    int rangeDMax = Integer.parseInt(attributes.getValue(ATTR_RANGE_DMAX));
                    tree.addItem(
                            name,
                            new InvTweaksItemTreeItem(
                                    (name + id + "-" + rangeDMin + "-" + rangeDMax).toLowerCase(),
                                    id,
                                    rangeDMin,
                                    rangeDMax,
                                    itemOrder++));
                }

                categoryStack.add(name);
            }

            // Item
            else if (attributes.getValue(ATTR_ID) != null) {
                String id = attributes.getValue(ATTR_ID);
                int damage = OreDictionary.WILDCARD_VALUE;
                if (attributes.getValue(ATTR_DAMAGE) != null) {
                    damage = Integer.parseInt(attributes.getValue(ATTR_DAMAGE));
                }
                tree.addItem(
                        categoryStack.getLast(),
                        new InvTweaksItemTreeItem(name.toLowerCase(), id, damage, itemOrder++));
            } else if (oreDictNameAttr != null) {
                tree.registerOre(categoryStack.getLast(), name.toLowerCase(), oreDictNameAttr, itemOrder++);
            }
        }

        @Override
        public synchronized void endElement(String uri, String localName, String name) throws SAXException {
            if (!categoryStack.isEmpty() && name.equals(categoryStack.getLast())) {
                categoryStack.removeLast();
            }
        }
    }

    /**
     * Contains the whole hierarchy of categories and items, as defined in the XML item tree. Is used to recognize
     * keywords and store item orders.
     *
     * @author Jimeo Wan
     */
    private static class InvTweaksItemTree {

        public static final String UNKNOWN_ITEM = "unknown";

        /**
         * All categories, stored by name
         */
        private final Map<String, InvTweaksItemTreeCategory> categories = new HashMap<>();

        /**
         * Items stored by ID. A same ID can hold several names.
         */
        private final Map<String, Vector<InvTweaksItemTreeItem>> itemsById = new HashMap<>(500);
        private static Vector<InvTweaksItemTreeItem> defaultItems = null;

        /**
         * Items stored by name. A same name can match several IDs.
         */
        private final Map<String, Vector<InvTweaksItemTreeItem>> itemsByName = new HashMap<>(500);

        private String rootCategory;

        public InvTweaksItemTree() {
            reset();
        }

        public void reset() {

            if (defaultItems == null) {
                defaultItems = new Vector<>();
                defaultItems.add(
                        new InvTweaksItemTreeItem(UNKNOWN_ITEM, null, OreDictionary.WILDCARD_VALUE, Integer.MAX_VALUE));
            }

            // Reset tree
            categories.clear();
            itemsByName.clear();
            itemsById.clear();

        }

        /**
         * Checks if given item ID matches a given keyword (either the item's name is the keyword, or it is in the
         * keyword category)
         *
         * @param items
         * @param keyword
         */
        public boolean matches(List<InvTweaksItemTreeItem> items, String keyword) {

            if (items == null) {
                return false;
            }

            // The keyword is an item
            for (InvTweaksItemTreeItem item : items) {
                if (item.getName() != null && item.getName().equals(keyword)) {
                    return true;
                }
            }

            // The keyword is a category
            InvTweaksItemTreeCategory category = getCategory(keyword);
            if (category != null) {
                for (InvTweaksItemTreeItem item : items) {
                    if (category.contains(item)) {
                        return true;
                    }
                }
            }

            // Everything is stuff
            return keyword.equals(rootCategory);

        }

        public InvTweaksItemTreeCategory getRootCategory() {
            return categories.get(rootCategory);
        }

        public InvTweaksItemTreeCategory getCategory(String keyword) {
            return categories.get(keyword);
        }

        /**
         * @param id     Item ID
         * @param damage Item damage value
         * @return Order index
         */
        public int getItemOrder(String id, int damage) {
            if (id == null) {
                return 0;
            }

            List<InvTweaksItemTreeItem> items = itemsById.get(id);
            if (items != null) {
                for (InvTweaksItemTreeItem item : items) {
                    if (item.matchesDamage(damage)) {
                        return item.getOrder();
                    }
                }
            }

            List<InvTweaksItemTreeItem> addedItems = addUnrecognizedItem(id, damage);

            if (!addedItems.isEmpty()) {
                return addedItems.get(0).getOrder();
            }
            return 0;
        }

        public void setRootCategory(InvTweaksItemTreeCategory category) {
            rootCategory = category.getName();
            categories.put(rootCategory, category);
        }

        public void addCategory(String parentCategory, InvTweaksItemTreeCategory newCategory)
                throws NullPointerException {
            // Build tree
            categories.get(parentCategory.toLowerCase()).addCategory(newCategory);

            // Register category
            categories.put(newCategory.getName(), newCategory);
        }

        public void addItem(String parentCategory, InvTweaksItemTreeItem newItem) throws NullPointerException {
            // Build tree
            categories.get(parentCategory.toLowerCase()).addItem(newItem);

            // Register item
            if (itemsByName.containsKey(newItem.getName())) {
                itemsByName.get(newItem.getName()).add(newItem);
            } else {
                Vector<InvTweaksItemTreeItem> list = new Vector<>();
                list.add(newItem);
                itemsByName.put(newItem.getName(), list);
            }
            if (itemsById.containsKey(newItem.getId())) {
                itemsById.get(newItem.getId()).add(newItem);
            } else {
                Vector<InvTweaksItemTreeItem> list = new Vector<>();
                list.add(newItem);
                itemsById.put(newItem.getId(), list);
            }
        }

        @javax.annotation.Nonnull
        private List<InvTweaksItemTreeItem> addUnrecognizedItem(String id, int damage) {
            InvTweaksItemTreeItem newItemId = new InvTweaksItemTreeItem(
                    String.format("%s-%d", id, damage),
                    id,
                    damage,
                    5000/* TODO: What to do here with non-int IDs + id * 16 */ + damage);
            InvTweaksItemTreeItem newItemDamage = new InvTweaksItemTreeItem(
                    id,
                    id,
                    OreDictionary.WILDCARD_VALUE,
                    /* TODO: What to do here with non-int IDs + id * 16 */5000);
            addItem(getRootCategory().getName(), newItemId);
            addItem(getRootCategory().getName(), newItemDamage);

            return Arrays.asList(newItemId, newItemDamage);
        }

        private static class OreDictInfo {

            String category;
            String name;
            String oreName;
            int order;

            OreDictInfo(String category, String name, String oreName, int order) {
                this.category = category;
                this.name = name;
                this.oreName = oreName;
                this.order = order;
            }
        }

        public void registerOre(String category, String name, String oreName, int order) {
            for (ItemStack i : OreDictionary.getOres(oreName)) {
                if (i != null) {
                    addItem(
                            category,
                            new InvTweaksItemTreeItem(
                                    name,
                                    Item.itemRegistry.getNameForObject(i.getItem()),
                                    i.getItemDamage(),
                                    order));
                } else {
                    AELog.logSimple(Level.WARN, String.format("An OreDictionary entry for %s is null", oreName));
                }
            }
            oresRegistered.add(new OreDictInfo(category, name, oreName, order));
        }

        private List<InvTweaksItemTree.OreDictInfo> oresRegistered = new ArrayList<>();

        @SubscribeEvent
        public void oreRegistered(OreDictionary.OreRegisterEvent ev) {
            for (InvTweaksItemTree.OreDictInfo ore : oresRegistered) {
                if (ore.oreName.equals(ev.Name)) {
                    if (ev.Ore.getItem() != null) {
                        addItem(
                                ore.category,
                                new InvTweaksItemTreeItem(
                                        ore.name,
                                        Item.itemRegistry.getNameForObject(ev.Ore.getItem()),
                                        ev.Ore.getItemDamage(),
                                        ore.order));
                    } else {
                        AELog.logSimple(Level.WARN, String.format("An OreDictionary entry for %s is null", ev.Name));
                    }
                }
            }
        }
    }

    /**
     * Representation of a category in the item tree, i.e. a group of items.
     *
     * @author Jimeo Wan
     */
    private static class InvTweaksItemTreeCategory {

        private final Map<String, List<InvTweaksItemTreeItem>> items = new HashMap<>();
        private final Vector<String> matchingItems = new Vector<String>();
        private final Vector<InvTweaksItemTreeCategory> subCategories = new Vector<>();
        private String name;
        private int order = -1;

        public InvTweaksItemTreeCategory(String name) {
            this.name = (name != null) ? name.toLowerCase() : null;
        }

        public boolean contains(InvTweaksItemTreeItem item) {
            List<InvTweaksItemTreeItem> storedItems = items.get(item.getId());
            if (storedItems != null) {
                for (InvTweaksItemTreeItem storedItem : storedItems) {
                    if (storedItem.equals(item)) {
                        return true;
                    }
                }
            }
            for (InvTweaksItemTreeCategory category : subCategories) {
                if (category.contains(item)) {
                    return true;
                }
            }
            return false;
        }

        public void addCategory(InvTweaksItemTreeCategory category) {
            subCategories.add(category);
        }

        public void addItem(InvTweaksItemTreeItem item) {

            // Add item to category
            if (items.get(item.getId()) == null) {
                List<InvTweaksItemTreeItem> itemList = new ArrayList<>();
                itemList.add(item);
                items.put(item.getId(), itemList);
            } else {
                items.get(item.getId()).add(item);
            }
            matchingItems.add(item.getName());

            // Categorie's order is defined by its lowest item order
            if (order == -1 || order > item.getOrder()) {
                order = item.getOrder();
            }
        }

        /**
         * @return all categories contained in this one.
         */
        public Collection<InvTweaksItemTreeCategory> getSubCategories() {
            return subCategories;
        }

        public Collection<List<InvTweaksItemTreeItem>> getItems() {
            return items.values();
        }

        public String getName() {
            return name;
        }

        public String toString() {
            return name + " (" + subCategories.size() + " cats, " + items.size() + " items)";
        }

    }

    /**
     * Representation of an item in the item tree.
     *
     * @author Jimeo Wan
     */
    private static class InvTweaksItemTreeItem {

        private String name;
        private String id;
        private int damageMin;
        private int damageMax;
        private int order;

        /**
         * @param name   The item name
         * @param id     The item ID
         * @param damage The item variant or InvTweaksConst.DAMAGE_WILDCARD
         * @param order  The item order while sorting
         */
        public InvTweaksItemTreeItem(String name, String id, int damage, int order) {
            this.name = name;
            this.id = getNamespacedID(id);
            this.damageMin = damage;
            this.damageMax = damage;
            this.order = order;
        }

        /**
         * @param name      The item name
         * @param id        The item ID
         * @param damageMin The lowest value of the item variant or InvTweaksConst.DAMAGE_WILDCARD
         * @param damageMax The highest value of the item variant or InvTweaksConst.DAMAGE_WILDCARD
         * @param order     The item order while sorting
         */
        public InvTweaksItemTreeItem(String name, String id, int damageMin, int damageMax, int order) {
            this.name = name;
            this.id = getNamespacedID(id);
            this.damageMin = damageMin;
            this.damageMax = damageMax;
            this.order = order;
        }

        public static String getNamespacedID(String id) {
            if (id == null) {
                return null;
            } else if (id.indexOf(':') == -1) {
                return "minecraft:" + id;
            }
            return id;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public int getDamage() {
            // Not an ideal solution, but handles DAMAGE_WILDCARD cases nicely
            return damageMin;
        }

        public boolean matchesDamage(int damage) {
            if (damage == OreDictionary.WILDCARD_VALUE || this.damageMin == OreDictionary.WILDCARD_VALUE
                    || this.damageMax == OreDictionary.WILDCARD_VALUE) {
                return true;
            }
            return damage >= this.damageMin && damage <= this.damageMax;
        }

        public int getOrder() {
            return order;
        }

        /**
         * Warning: the item equality is not reflective. They are equal if "o" matches the item constraints (the
         * opposite can be false).
         */
        public boolean equals(Object o) {
            if (!(o instanceof InvTweaksItemTreeItem item)) {
                return false;
            }
            return ObjectUtils.equals(id, item.getId()) && (damageMin == OreDictionary.WILDCARD_VALUE
                    || (damageMin <= item.getDamage() && damageMax >= item.getDamage()));
        }

        public String toString() {
            return name;
        }

        public int compareTo(InvTweaksItemTreeItem item) {
            return item.getOrder() - getOrder();
        }

    }

}
