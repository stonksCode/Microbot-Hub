package net.runelite.client.plugins.microbot.autobankstander.skills.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.GenericCombineProcessor;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Bank-standing Crafting processor.
 *
 * <p>Selects one declarative recipe (from {@link #getRecipeNames(CraftingMethod)}) and
 * delegates the heavy lifting to {@link GenericCombineProcessor}. Recipes that do not
 * open a "make" dialogue (stringing jewellery) or that need a scenery object
 * (pottery on a potter's wheel) use a small dedicated delegate with the same
 * capacity-aware banking and exact-product-selection rules.</p>
 *
 * <p>Product names passed to the delegates are always exact in-game item names; there
 * is deliberately no generic "first option" fallback that could craft the wrong item.</p>
 */
@Slf4j
public class CraftingProcessor implements BankStandingProcessor {

    private static final long PROGRESS_GRACE_MS = 5000;
    private static final long STARTUP_GRACE_MS = 1500;

    private final CraftingMethod method;
    private final RecipeDef definition;
    private final BankStandingProcessor delegate;

    public CraftingProcessor(CraftingMethod method, String recipeName) {
        if (method == null) {
            throw new IllegalArgumentException("Crafting method is required");
        }
        this.method = method;
        this.definition = resolveRecipe(recipesFor(method), recipeName, getDefaultRecipe(method));

        if (definition.pottery) {
            this.delegate = new PotteryProcessor(definition);
        } else if (definition.noDialogue) {
            this.delegate = new StringingProcessor(toGenericRecipe(definition));
        } else {
            this.delegate = new GenericCombineProcessor(toGenericRecipe(definition),
                    "Crafting " + definition.name.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * @return the selectable recipe names for {@code method}, in display order.
     */
    public static String[] getRecipeNames(CraftingMethod method) {
        List<RecipeDef> recipes = recipesFor(method);
        String[] names = new String[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) {
            names[i] = recipes.get(i).name;
        }
        return names;
    }

    /**
     * @return a sensible default recipe name for {@code method}, or {@code null} if the
     *         method is not supported.
     */
    public static String getDefaultRecipe(CraftingMethod method) {
        switch (method) {
            case LEATHER:
                return "Leather body";
            case GEMS:
                return "Cut sapphire";
            case JEWELLERY:
                return "String gold amulet";
            case GLASSBLOWING:
                return "Vial";
            case POTTERY:
                return "Unfired pot";
            case BATTLESTAVES:
                return "Water battlestaff";
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------------
    // BankStandingProcessor contract - thin pass-through with one extra
    // consumable (thread) for leather recipes.
    // ---------------------------------------------------------------------

    @Override
    public boolean validate() {
        return delegate.validate();
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>(delegate.getBankingRequirements());
        if (definition.auxiliaryId > 0) {
            requirements.add("Thread for " + definition.name);
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        return delegate.hasRequiredItems()
                && (definition.auxiliaryId <= 0
                || Rs2Inventory.itemQuantity(definition.auxiliaryId) >= 1);
    }

    @Override
    public boolean performBanking() {
        if (!delegate.performBanking()) {
            return false;
        }
        if (definition.auxiliaryId <= 0) {
            return true;
        }
        if (!Rs2Bank.isOpen()) {
            log.info("Bank closed before withdrawing thread for {}", definition.name);
            return false;
        }
        if (Rs2Inventory.itemQuantity(definition.auxiliaryId) >= 1) {
            return true;
        }
        int bankThread = Rs2Bank.count(definition.auxiliaryId);
        if (bankThread < 1) {
            log.info("No thread in the bank for {}", definition.name);
            return false;
        }
        if (!Rs2Bank.withdrawAll(definition.auxiliaryId)) {
            log.info("Could not withdraw thread for {}", definition.name);
            return false;
        }
        return sleepUntil(() -> Rs2Inventory.itemQuantity(definition.auxiliaryId) >= 1, 3500);
    }

    @Override
    public boolean process() {
        return delegate.process();
    }

    @Override
    public boolean canContinueProcessing() {
        if (!delegate.canContinueProcessing()) {
            return false;
        }
        if (definition.auxiliaryId <= 0) {
            return true;
        }
        return Rs2Inventory.itemQuantity(definition.auxiliaryId) >= 1
                || Rs2Bank.count(definition.auxiliaryId) >= 1;
    }

    @Override
    public String getStatusMessage() {
        return delegate.getStatusMessage();
    }

    @Override
    public boolean isActivelyProcessing() {
        return delegate.isActivelyProcessing();
    }

    /** Exposed for diagnostics/logging by the wiring code. */
    public CraftingMethod getMethod() {
        return method;
    }

    /** Exposed for diagnostics/logging by the wiring code. */
    public String getRecipeName() {
        return definition.name;
    }

    // ---------------------------------------------------------------------
    // Recipe catalogue
    // ---------------------------------------------------------------------

    private static final class RecipeDef {
        final String name;
        final int level;
        final int primaryId;
        final int secondaryId;
        final int productId;
        final boolean secondaryIsTool;
        final int primaryPerProduct;
        final int auxiliaryId;
        final boolean pottery;
        final boolean noDialogue;
        final boolean pressSpace;
        final boolean productionWidget;
        final String[] productNames;

        RecipeDef(String name, int level, int primaryId, int secondaryId, int productId,
                  boolean secondaryIsTool, int primaryPerProduct, int auxiliaryId,
                  boolean pottery, boolean noDialogue, boolean pressSpace,
                  boolean productionWidget, String... productNames) {
            this.name = name;
            this.level = level;
            this.primaryId = primaryId;
            this.secondaryId = secondaryId;
            this.productId = productId;
            this.secondaryIsTool = secondaryIsTool;
            this.primaryPerProduct = Math.max(1, primaryPerProduct);
            this.auxiliaryId = auxiliaryId;
            this.pottery = pottery;
            this.noDialogue = noDialogue;
            this.pressSpace = pressSpace;
            this.productionWidget = productionWidget;
            this.productNames = productNames != null && productNames.length > 0
                    ? productNames : new String[]{name};
        }
    }

    /** Item-on-item recipe that opens a make/production dialogue. */
    private static RecipeDef combine(String name, int level, int primaryId, int secondaryId,
                                     int productId, boolean tool, int perProduct,
                                     String... productNames) {
        return new RecipeDef(name, level, primaryId, secondaryId, productId, tool,
                perProduct, -1, false, false, false, false, productNames);
    }

    /** Cutting a gem with a chisel requires a Space press, not a make dialogue. */
    private static RecipeDef cut(String name, int level, int uncutId, int productId,
                                 String... productNames) {
        return new RecipeDef(name, level, uncutId, ItemID.CHISEL, productId, true,
                1, -1, false, false, true, false, productNames);
    }

    /** Recipe confirmed through the "How many do you wish to make?" production widget. */
    private static RecipeDef production(String name, int level, int primaryId, int secondaryId,
                                        int productId, boolean tool, int perProduct,
                                        String... productNames) {
        return new RecipeDef(name, level, primaryId, secondaryId, productId, tool,
                perProduct, -1, false, false, false, true, productNames);
    }

    /** Leather recipe: needle is a tool, one thread is consumed per five leathers. */
    private static RecipeDef leather(String name, int level, int leatherId, int productId,
                                     int perProduct, String... productNames) {
        return new RecipeDef(name, level, leatherId, ItemID.NEEDLE, productId, true,
                perProduct, ItemID.THREAD, false, false, false, false, productNames);
    }

    /** Stringing an unstrung item with a ball of wool does not open a make dialogue. */
    private static RecipeDef string(String name, int unstrungId, int productId,
                                    String... productNames) {
        return new RecipeDef(name, 1, unstrungId, ItemID.BALL_OF_WOOL, productId, false,
                1, -1, false, true, false, false, productNames);
    }

    /** Soft clay on a potter's wheel (a scenery object, not an inventory item). */
    private static RecipeDef pottery(String name, int level, int productId,
                                     String... productNames) {
        return new RecipeDef(name, level, ItemID.SOFTCLAY, -1, productId, false,
                1, -1, true, false, false, false, productNames);
    }

    private static List<RecipeDef> recipesFor(CraftingMethod method) {
        if (method == null) {
            return Collections.emptyList();
        }
        switch (method) {
            case LEATHER:
                return leatherRecipes();
            case GEMS:
                return gemRecipes();
            case JEWELLERY:
                return jewelleryRecipes();
            case GLASSBLOWING:
                return glassblowingRecipes();
            case POTTERY:
                return potteryRecipes();
            case BATTLESTAVES:
                return battlestaveRecipes();
            default:
                return Collections.emptyList();
        }
    }

    private static List<RecipeDef> leatherRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(leather("Leather gloves", 1, ItemID.LEATHER, ItemID.LEATHER_GLOVES,
                1, "Leather gloves", "Gloves"));
        recipes.add(leather("Leather boots", 7, ItemID.LEATHER, ItemID.LEATHER_BOOTS,
                1, "Leather boots", "Boots"));
        recipes.add(leather("Leather cowl", 9, ItemID.LEATHER, ItemID.LEATHER_COWL,
                1, "Leather cowl", "Cowl"));
        recipes.add(leather("Leather vambraces", 11, ItemID.LEATHER, ItemID.LEATHER_VAMBRACES,
                1, "Leather vambraces", "Vambraces"));
        recipes.add(leather("Leather body", 14, ItemID.LEATHER, ItemID.LEATHER_ARMOUR,
                1, "Leather body", "Body"));
        recipes.add(leather("Leather chaps", 18, ItemID.LEATHER, ItemID.LEATHER_CHAPS,
                1, "Leather chaps", "Chaps"));
        recipes.add(leather("Hardleather body", 28, ItemID.HARD_LEATHER,
                ItemID.HARDLEATHER_BODY, 1, "Hardleather body", "Hard leather body", "Body"));

        // Dragon leather: only vambraces (1 hide), chaps (2 hides) and bodies (3 hides).
        recipes.add(leather("Green d'hide vambraces", 57, ItemID.DRAGON_LEATHER,
                ItemID.DRAGON_VAMBRACES, 1, "Green d'hide vambraces", "Vambraces"));
        recipes.add(leather("Green d'hide chaps", 60, ItemID.DRAGON_LEATHER,
                ItemID.DRAGONHIDE_CHAPS, 2, "Green d'hide chaps", "Chaps"));
        recipes.add(leather("Green d'hide body", 63, ItemID.DRAGON_LEATHER,
                ItemID.DRAGONHIDE_BODY, 3, "Green d'hide body", "Body"));
        recipes.add(leather("Blue d'hide vambraces", 66, ItemID.DRAGON_LEATHER_BLUE,
                ItemID.BLUE_DRAGON_VAMBRACES, 1, "Blue d'hide vambraces", "Vambraces"));
        recipes.add(leather("Blue d'hide chaps", 68, ItemID.DRAGON_LEATHER_BLUE,
                ItemID.BLUE_DRAGONHIDE_CHAPS, 2, "Blue d'hide chaps", "Chaps"));
        recipes.add(leather("Blue d'hide body", 71, ItemID.DRAGON_LEATHER_BLUE,
                ItemID.BLUE_DRAGONHIDE_BODY, 3, "Blue d'hide body", "Body"));
        recipes.add(leather("Red d'hide vambraces", 73, ItemID.DRAGON_LEATHER_RED,
                ItemID.RED_DRAGON_VAMBRACES, 1, "Red d'hide vambraces", "Vambraces"));
        recipes.add(leather("Red d'hide chaps", 75, ItemID.DRAGON_LEATHER_RED,
                ItemID.RED_DRAGONHIDE_CHAPS, 2, "Red d'hide chaps", "Chaps"));
        recipes.add(leather("Red d'hide body", 77, ItemID.DRAGON_LEATHER_RED,
                ItemID.RED_DRAGONHIDE_BODY, 3, "Red d'hide body", "Body"));
        recipes.add(leather("Black d'hide vambraces", 79, ItemID.DRAGON_LEATHER_BLACK,
                ItemID.BLACK_DRAGON_VAMBRACES, 1, "Black d'hide vambraces", "Vambraces"));
        recipes.add(leather("Black d'hide chaps", 82, ItemID.DRAGON_LEATHER_BLACK,
                ItemID.BLACK_DRAGONHIDE_CHAPS, 2, "Black d'hide chaps", "Chaps"));
        recipes.add(leather("Black d'hide body", 84, ItemID.DRAGON_LEATHER_BLACK,
                ItemID.BLACK_DRAGONHIDE_BODY, 3, "Black d'hide body", "Body"));
        return recipes;
    }

    private static List<RecipeDef> gemRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(cut("Cut opal", 1, ItemID.UNCUT_OPAL, ItemID.OPAL, "Opal"));
        recipes.add(cut("Cut jade", 13, ItemID.UNCUT_JADE, ItemID.JADE, "Jade"));
        recipes.add(cut("Cut red topaz", 16, ItemID.UNCUT_RED_TOPAZ,
                ItemID.RED_TOPAZ, "Red topaz", "Red Topaz"));
        recipes.add(cut("Cut sapphire", 20, ItemID.UNCUT_SAPPHIRE, ItemID.SAPPHIRE, "Sapphire"));
        recipes.add(cut("Cut emerald", 27, ItemID.UNCUT_EMERALD, ItemID.EMERALD, "Emerald"));
        recipes.add(cut("Cut ruby", 34, ItemID.UNCUT_RUBY, ItemID.RUBY, "Ruby"));
        recipes.add(cut("Cut diamond", 43, ItemID.UNCUT_DIAMOND, ItemID.DIAMOND, "Diamond"));
        recipes.add(cut("Cut dragonstone", 55, ItemID.UNCUT_DRAGONSTONE,
                ItemID.DRAGONSTONE, "Dragonstone"));
        recipes.add(cut("Cut onyx", 67, ItemID.UNCUT_ONYX, ItemID.ONYX, "Onyx"));
        // Amethyst is cut straight into Fletching tips, never into a cut gem.
        recipes.add(production("Cut amethyst bolt tips", 83, ItemID.AMETHYST, ItemID.CHISEL,
                ItemID.XBOWS_BOLT_TIPS_AMETHYST, true, 1, "Amethyst bolt tips"));
        recipes.add(production("Cut amethyst arrowtips", 85, ItemID.AMETHYST, ItemID.CHISEL,
                ItemID.AMETHYST_ARROWHEADS, true, 1, "Amethyst arrowtips"));
        recipes.add(production("Cut amethyst javelin heads", 87, ItemID.AMETHYST, ItemID.CHISEL,
                ItemID.AMETHYST_JAVELIN_HEAD, true, 1, "Amethyst javelin heads"));
        recipes.add(production("Cut amethyst dart tips", 89, ItemID.AMETHYST, ItemID.CHISEL,
                ItemID.AMETHYST_DART_TIP, true, 1, "Amethyst dart tips"));
        recipes.add(cut("Cut zenyte", 89, ItemID.UNCUT_ZENYTE, ItemID.ZENYTE, "Zenyte"));
        return recipes;
    }

    private static List<RecipeDef> jewelleryRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(string("String gold amulet", ItemID.UNSTRUNG_GOLD_AMULET,
                ItemID.STRUNG_GOLD_AMULET, "Gold amulet"));
        recipes.add(string("String sapphire amulet", ItemID.UNSTRUNG_SAPPHIRE_AMULET,
                ItemID.STRUNG_SAPPHIRE_AMULET, "Sapphire amulet"));
        recipes.add(string("String emerald amulet", ItemID.UNSTRUNG_EMERALD_AMULET,
                ItemID.STRUNG_EMERALD_AMULET, "Emerald amulet"));
        recipes.add(string("String ruby amulet", ItemID.UNSTRUNG_RUBY_AMULET,
                ItemID.STRUNG_RUBY_AMULET, "Ruby amulet"));
        recipes.add(string("String diamond amulet", ItemID.UNSTRUNG_DIAMOND_AMULET,
                ItemID.STRUNG_DIAMOND_AMULET, "Diamond amulet"));
        recipes.add(string("String dragonstone amulet", ItemID.UNSTRUNG_DRAGONSTONE_AMULET,
                ItemID.STRUNG_DRAGONSTONE_AMULET, "Dragonstone amulet"));
        recipes.add(string("String onyx amulet", ItemID.UNSTRUNG_ONYX_AMULET,
                ItemID.STRUNG_ONYX_AMULET, "Onyx amulet"));
        recipes.add(string("String zenyte amulet", ItemID.UNSTRUNG_ZENYTE_AMULET,
                ItemID.ZENYTE_AMULET, "Zenyte amulet"));
        recipes.add(string("String unstrung symbol", ItemID.NOSTRINGSTAR,
                ItemID.BLESSEDSTAR, "Unblessed symbol", "Holy symbol"));
        return recipes;
    }

    private static List<RecipeDef> glassblowingRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(combine("Beer glass", 1, ItemID.MOLTEN_GLASS, ItemID.GLASSBLOWINGPIPE,
                ItemID.BEER_GLASS, true, 1, "Beer glass"));
        recipes.add(combine("Empty candle lantern", 4, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.CANDLE_LANTERN_EMPTY, true, 1,
                "Empty candle lantern", "Candle lantern"));
        recipes.add(combine("Empty oil lamp", 12, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.OIL_LAMP_EMPTY, true, 1,
                "Empty oil lamp", "Oil lamp"));
        recipes.add(combine("Vial", 33, ItemID.MOLTEN_GLASS, ItemID.GLASSBLOWINGPIPE,
                ItemID.VIAL_EMPTY, true, 1, "Vial"));
        recipes.add(combine("Empty fishbowl", 42, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.FISHBOWL_EMPTY, true, 1,
                "Empty fishbowl", "Fishbowl"));
        recipes.add(combine("Unpowered orb", 46, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.STAFFORB, true, 1, "Unpowered orb"));
        recipes.add(combine("Lantern lens", 49, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.BULLSEYE_LANTERN_LENS, true, 1,
                "Lantern lens"));
        recipes.add(combine("Empty light orb", 87, ItemID.MOLTEN_GLASS,
                ItemID.GLASSBLOWINGPIPE, ItemID.DORGESH_LIGHT_BULB, true, 1,
                "Empty light orb", "Light orb"));
        return recipes;
    }

    private static List<RecipeDef> potteryRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(pottery("Unfired pot", 1, ItemID.POT_UNFIRED, "Unfired pot", "Pot"));
        recipes.add(pottery("Unfired pie dish", 7, ItemID.PIEDISH_UNFIRED,
                "Unfired pie dish", "Pie dish"));
        recipes.add(pottery("Unfired bowl", 8, ItemID.BOWL_UNFIRED, "Unfired bowl", "Bowl"));
        // Pot lids additionally require partial completion of One Small Favour.
        recipes.add(pottery("Unfired pot lid", 19, ItemID.POTLID_UNFIRED,
                "Unfired pot lid", "Pot lid"));
        return recipes;
    }

    private static List<RecipeDef> battlestaveRecipes() {
        List<RecipeDef> recipes = new ArrayList<>();
        recipes.add(combine("Water battlestaff", 54, ItemID.BATTLESTAFF, ItemID.WATER_ORB,
                ItemID.WATER_BATTLESTAFF, false, 1, "Water battlestaff"));
        recipes.add(combine("Earth battlestaff", 58, ItemID.BATTLESTAFF, ItemID.EARTH_ORB,
                ItemID.EARTH_BATTLESTAFF, false, 1, "Earth battlestaff"));
        recipes.add(combine("Fire battlestaff", 62, ItemID.BATTLESTAFF, ItemID.FIRE_ORB,
                ItemID.FIRE_BATTLESTAFF, false, 1, "Fire battlestaff"));
        recipes.add(combine("Air battlestaff", 66, ItemID.BATTLESTAFF, ItemID.AIR_ORB,
                ItemID.AIR_BATTLESTAFF, false, 1, "Air battlestaff"));
        return recipes;
    }

    private static RecipeDef resolveRecipe(List<RecipeDef> recipes, String recipeName,
                                           String defaultName) {
        if (recipeName != null) {
            for (RecipeDef recipe : recipes) {
                if (recipe.name.equals(recipeName)) {
                    return recipe;
                }
            }
            for (RecipeDef recipe : recipes) {
                if (recipe.name.equalsIgnoreCase(recipeName.trim())) {
                    return recipe;
                }
            }
            throw new IllegalArgumentException("Unknown crafting recipe: " + recipeName);
        }
        for (RecipeDef recipe : recipes) {
            if (recipe.name.equals(defaultName)) {
                return recipe;
            }
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("No recipes for this crafting method");
        }
        return recipes.get(0);
    }

    private static GenericCombineProcessor.Recipe toGenericRecipe(RecipeDef def) {
        return new GenericCombineProcessor.Recipe(def.name, Skill.CRAFTING, def.level,
                def.primaryId, def.secondaryId, def.productId, def.secondaryIsTool,
                def.primaryPerProduct, def.pressSpace, def.productionWidget, def.productNames);
    }

    // ---------------------------------------------------------------------
    // Stringing jewellery - item-on-item without a make dialogue.
    // ---------------------------------------------------------------------

    private static final class StringingProcessor extends GenericCombineProcessor {
        private final GenericCombineProcessor.Recipe recipe;
        private boolean making;
        private int materialAtStart;
        private int productAtStart;
        private long startedAt;
        private long lastProgress;

        StringingProcessor(GenericCombineProcessor.Recipe recipe) {
            super(recipe, "Stringing " + recipe.getName().toLowerCase(Locale.ROOT));
            this.recipe = recipe;
        }

        @Override
        public boolean process() {
            if (making) {
                if (tracksProgress()) {
                    return true;
                }
                making = false;
            }
            if (!hasRequiredItems()) {
                return false;
            }
            if (!Rs2Inventory.combine(recipe.getPrimaryId(), recipe.getSecondaryId())) {
                return false;
            }

            // Some clients still show a make/quantity dialogue when stringing. Handle
            // it only when the exact advertised product is offered - never a fallback.
            if (sleepUntil(Rs2Dialogue::isInDialogue, 700)) {
                String match = findExactProductOption();
                if (match != null && Rs2Dialogue.clickOption(match)) {
                    if (Rs2Dialogue.sleepUntilHasQuestion("How many")) {
                        Rs2Dialogue.clickOption("Make All");
                    }
                }
            }

            startBatchTracking();
            Rs2Random.waitEx(160, 80);
            return true;
        }

        @Override
        public boolean isActivelyProcessing() {
            if (!making) {
                return false;
            }
            if (Rs2Dialogue.isInDialogue() || Rs2Player.isAnimating(1000)) {
                return true;
            }
            long now = System.currentTimeMillis();
            return now - startedAt < STARTUP_GRACE_MS || now - lastProgress < PROGRESS_GRACE_MS;
        }

        private String findExactProductOption() {
            for (String candidate : recipe.getProductNames()) {
                if (Rs2Dialogue.hasCombinationDialogue()
                        && Rs2Dialogue.getCombinationOption(candidate) != null) {
                    return candidate;
                }
                if (Rs2Dialogue.hasDialogueOption(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private boolean tracksProgress() {
            int material = Rs2Inventory.itemQuantity(recipe.getPrimaryId());
            int product = Rs2Inventory.itemQuantity(recipe.getProductId());
            if (material < materialAtStart || product > productAtStart) {
                lastProgress = System.currentTimeMillis();
            }
            boolean canMakeAnother = material >= recipe.getPrimaryPerProduct()
                    && Rs2Inventory.itemQuantity(recipe.getSecondaryId()) >= 1;
            return canMakeAnother && System.currentTimeMillis() - lastProgress < PROGRESS_GRACE_MS;
        }

        private void startBatchTracking() {
            making = true;
            materialAtStart = Rs2Inventory.itemQuantity(recipe.getPrimaryId());
            productAtStart = Rs2Inventory.itemQuantity(recipe.getProductId());
            startedAt = System.currentTimeMillis();
            lastProgress = startedAt;
        }
    }

    // ---------------------------------------------------------------------
    // Pottery - soft clay on a scenery potter's wheel.
    // ---------------------------------------------------------------------

    private static final class PotteryProcessor implements BankStandingProcessor {
        private final RecipeDef definition;
        private boolean making;
        private int clayAtStart;
        private int productAtStart;
        private long startedAt;
        private long lastProgress;

        PotteryProcessor(RecipeDef definition) {
            this.definition = definition;
        }

        @Override
        public boolean validate() {
            return Rs2Player.getRealSkillLevel(Skill.CRAFTING) >= definition.level;
        }

        @Override
        public List<String> getBankingRequirements() {
            return Collections.singletonList("Soft clay (with a reachable potter's wheel)");
        }

        @Override
        public boolean hasRequiredItems() {
            return Rs2Inventory.itemQuantity(ItemID.SOFTCLAY) >= 1;
        }

        @Override
        public boolean performBanking() {
            if (!Rs2Bank.isOpen()) {
                log.info("Bank not open");
                return false;
            }
            if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
                return false;
            }
            if (!Rs2Inventory.isEmpty()) {
                Rs2Bank.depositAll();
                if (!sleepUntil(Rs2Inventory::isEmpty, 3000)) {
                    return false;
                }
            }
            int bankClay = Rs2Bank.count(ItemID.SOFTCLAY);
            if (bankClay < 1) {
                log.info("No soft clay in bank for {}", definition.name);
                return false;
            }
            if (!Rs2Bank.withdrawAll(ItemID.SOFTCLAY)) {
                return false;
            }
            return sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.SOFTCLAY) >= bankClay, 3500);
        }

        @Override
        public boolean process() {
            if (making) {
                if (tracksProgress()) {
                    return true;
                }
                making = false;
            }
            if (!hasRequiredItems()) {
                return false;
            }
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            }

            if (!openPotterWheel()) {
                return false;
            }
            if (!clickExactProduct()) {
                log.info("Product '{}' was not offered for {} - refusing fallback",
                        definition.productNames[0], definition.name);
                return false;
            }
            confirmQuantityIfNeeded();

            startBatchTracking();
            Rs2Random.waitEx(160, 80);
            return true;
        }

        @Override
        public boolean canContinueProcessing() {
            return hasRequiredItems()
                    || (Rs2Bank.isNearBank(12) && Rs2Bank.count(ItemID.SOFTCLAY) >= 1);
        }

        @Override
        public String getStatusMessage() {
            return making
                    ? "Making " + definition.name.toLowerCase(Locale.ROOT) + "..."
                    : "Pottery: " + definition.name;
        }

        @Override
        public boolean isActivelyProcessing() {
            if (!making) {
                return false;
            }
            if (Rs2Dialogue.isInDialogue() || Rs2Player.isAnimating(1000)) {
                return true;
            }
            long now = System.currentTimeMillis();
            return now - startedAt < STARTUP_GRACE_MS || now - lastProgress < PROGRESS_GRACE_MS;
        }

        /**
         * Pottery is only bank-standable next to a wheel. We do not walk to one; if no
         * reachable wheel exists we log a clear message and stop instead of guessing.
         */
        private boolean openPotterWheel() {
            GameObject wheel = Rs2GameObject.get("Potter's wheel");
            if (wheel == null) {
                wheel = Rs2GameObject.get("Potters wheel");
            }
            if (wheel == null) {
                log.info("No reachable potter's wheel for {}. Stand beside a bank with a "
                        + "potter's wheel; walking to one is not supported.", definition.name);
                return false;
            }
            boolean interacted = Rs2GameObject.interact(wheel, "Mould")
                    || Rs2GameObject.interact(wheel, "Use");
            if (!interacted) {
                log.info("Could not use the potter's wheel for {}", definition.name);
                return false;
            }
            return sleepUntil(() -> Rs2Widget.getWidget(17694734) != null
                    || Rs2Widget.hasWidgetText("How many do you wish to make?", 270, 5, false)
                    || Rs2Widget.hasWidgetText("What would you like to make?", 270, 5, false),
                    5000);
        }

        /** Exact product text only - no "first option" fallback. */
        private boolean clickExactProduct() {
            Widget[] containers = new Widget[]{
                    Rs2Widget.getWidget(17694734),
                    Rs2Widget.getWidget(270, 5)
            };
            for (Widget container : containers) {
                if (container == null) {
                    continue;
                }
                Widget[] children = container.getChildren();
                if (children == null) {
                    continue;
                }
                for (Widget child : children) {
                    if (child == null) {
                        continue;
                    }
                    String text = normalize(child.getText());
                    if (text.isEmpty()) {
                        continue;
                    }
                    for (String candidate : definition.productNames) {
                        if (text.equalsIgnoreCase(normalize(candidate))
                                && Rs2Widget.clickWidget(child)) {
                            return true;
                        }
                    }
                }
            }
            for (String candidate : definition.productNames) {
                if (Rs2Dialogue.hasDialogueOption(candidate)
                        && Rs2Dialogue.clickOption(candidate)) {
                    return true;
                }
            }
            return false;
        }

        private void confirmQuantityIfNeeded() {
            if (Rs2Dialogue.sleepUntilHasQuestion("How many")) {
                Rs2Dialogue.clickOption("Make All");
            }
        }

        private boolean tracksProgress() {
            int clay = Rs2Inventory.itemQuantity(ItemID.SOFTCLAY);
            int product = Rs2Inventory.itemQuantity(definition.productId);
            if (clay < clayAtStart || product > productAtStart) {
                lastProgress = System.currentTimeMillis();
            }
            return clay >= 1 && System.currentTimeMillis() - lastProgress < PROGRESS_GRACE_MS;
        }

        private void startBatchTracking() {
            making = true;
            clayAtStart = Rs2Inventory.itemQuantity(ItemID.SOFTCLAY);
            productAtStart = Rs2Inventory.itemQuantity(definition.productId);
            startedAt = System.currentTimeMillis();
            lastProgress = startedAt;
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "").replace("\u00a0", " ").trim();
    }
}
