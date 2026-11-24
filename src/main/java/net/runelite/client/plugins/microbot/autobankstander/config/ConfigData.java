package net.runelite.client.plugins.microbot.autobankstander.config;

import com.google.gson.Gson;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.SuperheatOre;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarSpell;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.JewelleryType;
import net.runelite.client.plugins.microbot.autobankstander.skills.crafting.CraftingMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.cooking.CookingMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ArrowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ArrowShaftType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.JavelinShaftType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.CrossbowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.DartType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.JavelinType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ShieldType;

public class ConfigData {
    
    // General settings
    private SkillType skill = SkillType.MAGIC;
    
    // Magic settings
    private MagicMethod magicMethod = MagicMethod.ENCHANTING;
    private BoltType boltType = BoltType.SAPPHIRE;
    private JewelleryType jewelleryType = JewelleryType.SAPPHIRE_RING;
    private String alchItemName = "";
    private boolean highAlchemy = true;
    private SuperheatOre superheatOre = SuperheatOre.IRON;
    private LunarSpell lunarSpell = LunarSpell.PLANK_MAKE;
    private String lunarSelection = "";

    // Crafting settings
    private CraftingMethod craftingMethod = CraftingMethod.LEATHER;
    private String craftingRecipe = "";

    // Cooking settings
    private CookingMethod cookingMethod = CookingMethod.RAW_FOOD;
    private String cookingRecipe = "";
    
    // Herblore settings
    private Mode herbloreMode = Mode.CLEAN_HERBS;
    private CleanHerbMode cleanHerbMode = CleanHerbMode.ANY_AND_ALL;
    private UnfinishedPotionMode unfinishedPotionMode = UnfinishedPotionMode.ANY_AND_ALL;
    private HerblorePotion finishedPotion = HerblorePotion.ATTACK;
    private boolean useAmuletOfChemistry = false;
    private boolean herbloreTurboMode = false;
    private int herbloreTurboLimit = 0;
    private int herbloreSleepMin = 60;
    private int herbloreSleepMax = 300;
    private int herbloreSleepTarget = 150;
    
    // Fletching settings
    private FletchingMode fletchingMode = FletchingMode.DARTS;
    private DartType dartType = DartType.BRONZE;
    private net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType fletchingBoltType = net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType.BRONZE;
    private ArrowType arrowType = ArrowType.HEADLESS;
    private JavelinType javelinType = JavelinType.BRONZE;
    private ArrowShaftType arrowShaftType = ArrowShaftType.LOGS;
    private JavelinShaftType javelinShaftType = JavelinShaftType.LOGS;
    private BowType bowType = BowType.SHORTBOW_UNSTRUNG;
    private CrossbowType crossbowType = CrossbowType.WOOD_STOCK;
    private ShieldType shieldType = ShieldType.OAK_SHIELD;
    
    // Default constructor
    public ConfigData() {}
    
    // Copy constructor
    public ConfigData(ConfigData other) {
        this.skill = other.skill;
        this.magicMethod = other.magicMethod;
        this.boltType = other.boltType;
        this.jewelleryType = other.jewelleryType;
        this.alchItemName = other.alchItemName;
        this.highAlchemy = other.highAlchemy;
        this.superheatOre = other.superheatOre;
        this.lunarSpell = other.lunarSpell;
        this.lunarSelection = other.lunarSelection;
        this.craftingMethod = other.craftingMethod;
        this.craftingRecipe = other.craftingRecipe;
        this.cookingMethod = other.cookingMethod;
        this.cookingRecipe = other.cookingRecipe;
        this.herbloreMode = other.herbloreMode;
        this.cleanHerbMode = other.cleanHerbMode;
        this.unfinishedPotionMode = other.unfinishedPotionMode;
        this.finishedPotion = other.finishedPotion;
        this.useAmuletOfChemistry = other.useAmuletOfChemistry;
        this.herbloreTurboMode = other.herbloreTurboMode;
        this.herbloreTurboLimit = other.herbloreTurboLimit;
        this.herbloreSleepMin = other.herbloreSleepMin;
        this.herbloreSleepMax = other.herbloreSleepMax;
        this.herbloreSleepTarget = other.herbloreSleepTarget;
        this.fletchingMode = other.fletchingMode;
        this.dartType = other.dartType;
        this.fletchingBoltType = other.fletchingBoltType;
        this.arrowType = other.arrowType;
        this.javelinType = other.javelinType;
        this.arrowShaftType = other.arrowShaftType;
        this.javelinShaftType = other.javelinShaftType;
        this.bowType = other.bowType;
        this.crossbowType = other.crossbowType;
        this.shieldType = other.shieldType;
    }
    
    // Getters and Setters
    public SkillType getSkill() { return skill; }
    public void setSkill(SkillType skill) { this.skill = skill; }
    
    public MagicMethod getMagicMethod() { return magicMethod; }
    public void setMagicMethod(MagicMethod magicMethod) { this.magicMethod = magicMethod; }
    
    public BoltType getBoltType() { return boltType; }
    public void setBoltType(BoltType boltType) { this.boltType = boltType; }

    public JewelleryType getJewelleryType() { return jewelleryType; }
    public void setJewelleryType(JewelleryType jewelleryType) { this.jewelleryType = jewelleryType; }

    public String getAlchItemName() { return alchItemName; }
    public void setAlchItemName(String alchItemName) { this.alchItemName = alchItemName; }

    public boolean isHighAlchemy() { return highAlchemy; }
    public void setHighAlchemy(boolean highAlchemy) { this.highAlchemy = highAlchemy; }

    public SuperheatOre getSuperheatOre() { return superheatOre; }
    public void setSuperheatOre(SuperheatOre superheatOre) { this.superheatOre = superheatOre; }

    public LunarSpell getLunarSpell() { return lunarSpell; }
    public void setLunarSpell(LunarSpell lunarSpell) { this.lunarSpell = lunarSpell; }

    public String getLunarSelection() { return lunarSelection; }
    public void setLunarSelection(String lunarSelection) { this.lunarSelection = lunarSelection; }

    public CraftingMethod getCraftingMethod() { return craftingMethod; }
    public void setCraftingMethod(CraftingMethod craftingMethod) { this.craftingMethod = craftingMethod; }

    public String getCraftingRecipe() { return craftingRecipe; }
    public void setCraftingRecipe(String craftingRecipe) { this.craftingRecipe = craftingRecipe; }

    public CookingMethod getCookingMethod() { return cookingMethod; }
    public void setCookingMethod(CookingMethod cookingMethod) { this.cookingMethod = cookingMethod; }

    public String getCookingRecipe() { return cookingRecipe; }
    public void setCookingRecipe(String cookingRecipe) { this.cookingRecipe = cookingRecipe; }

    public Mode getHerbloreMode() { return herbloreMode; }
    public void setHerbloreMode(Mode herbloreMode) { this.herbloreMode = herbloreMode; }
    
    public CleanHerbMode getCleanHerbMode() { return cleanHerbMode; }
    public void setCleanHerbMode(CleanHerbMode cleanHerbMode) { this.cleanHerbMode = cleanHerbMode; }
    
    public UnfinishedPotionMode getUnfinishedPotionMode() { return unfinishedPotionMode; }
    public void setUnfinishedPotionMode(UnfinishedPotionMode unfinishedPotionMode) { this.unfinishedPotionMode = unfinishedPotionMode; }
    
    public HerblorePotion getFinishedPotion() { return finishedPotion; }
    public void setFinishedPotion(HerblorePotion finishedPotion) { this.finishedPotion = finishedPotion; }
    
    public boolean isUseAmuletOfChemistry() { return useAmuletOfChemistry; }
    public void setUseAmuletOfChemistry(boolean useAmuletOfChemistry) { this.useAmuletOfChemistry = useAmuletOfChemistry; }

    public boolean isHerbloreTurboMode() { return herbloreTurboMode; }
    public void setHerbloreTurboMode(boolean herbloreTurboMode) { this.herbloreTurboMode = herbloreTurboMode; }

    public int getHerbloreTurboLimit() { return herbloreTurboLimit; }
    public void setHerbloreTurboLimit(int herbloreTurboLimit) { this.herbloreTurboLimit = herbloreTurboLimit; }

    public int getHerbloreSleepMin() { return herbloreSleepMin; }
    public void setHerbloreSleepMin(int herbloreSleepMin) { this.herbloreSleepMin = herbloreSleepMin; }

    public int getHerbloreSleepMax() { return herbloreSleepMax; }
    public void setHerbloreSleepMax(int herbloreSleepMax) { this.herbloreSleepMax = herbloreSleepMax; }

    public int getHerbloreSleepTarget() { return herbloreSleepTarget; }
    public void setHerbloreSleepTarget(int herbloreSleepTarget) { this.herbloreSleepTarget = herbloreSleepTarget; }

    public FletchingMode getFletchingMode() { return fletchingMode; }
    public void setFletchingMode(FletchingMode fletchingMode) { this.fletchingMode = fletchingMode; }
    
    public DartType getDartType() { return dartType; }
    public void setDartType(DartType dartType) { this.dartType = dartType; }
    
    public net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType getFletchingBoltType() { return fletchingBoltType; }
    public void setFletchingBoltType(net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType fletchingBoltType) { this.fletchingBoltType = fletchingBoltType; }
    
    public ArrowType getArrowType() { return arrowType; }
    public void setArrowType(ArrowType arrowType) { this.arrowType = arrowType; }
    
    public JavelinType getJavelinType() { return javelinType; }
    public void setJavelinType(JavelinType javelinType) { this.javelinType = javelinType; }

    public ArrowShaftType getArrowShaftType() { return arrowShaftType; }
    public void setArrowShaftType(ArrowShaftType arrowShaftType) { this.arrowShaftType = arrowShaftType; }

    public JavelinShaftType getJavelinShaftType() { return javelinShaftType; }
    public void setJavelinShaftType(JavelinShaftType javelinShaftType) { this.javelinShaftType = javelinShaftType; }
    
    public BowType getBowType() { return bowType; }
    public void setBowType(BowType bowType) { this.bowType = bowType; }
    
    public CrossbowType getCrossbowType() { return crossbowType; }
    public void setCrossbowType(CrossbowType crossbowType) { this.crossbowType = crossbowType; }
    
    public ShieldType getShieldType() { return shieldType; }
    public void setShieldType(ShieldType shieldType) { this.shieldType = shieldType; }
    
    // JSON serialization
    public String toJson() {
        return new Gson().toJson(this);
    }
    
    public static ConfigData fromJson(String json) {
        try {
            return new Gson().fromJson(json, ConfigData.class);
        } catch (Exception e) {
            return new ConfigData(); // return default config if parsing fails
        }
    }
    
    // Validation
    public boolean isValid() {
        if (skill == null) return false;
        
        switch (skill) {
            case MAGIC:
                return magicMethod != null && validateMagicConfig();
            case HERBLORE:
                return herbloreMode != null && validateHerbloreConfig();
            case FLETCHING:
                return fletchingMode != null && validateFletchingConfig();
            case CRAFTING:
                return craftingMethod != null && craftingRecipe != null && !craftingRecipe.isEmpty();
            case COOKING:
                return cookingMethod != null && cookingRecipe != null && !cookingRecipe.isEmpty();
            default:
                return false;
        }
    }

    private boolean validateMagicConfig() {
        switch (magicMethod) {
            case ENCHANTING:
                return boltType != null;
            case JEWELRY_ENCHANTING:
                return jewelleryType != null;
            case ALCHING:
                return alchItemName != null && !alchItemName.trim().isEmpty();
            case SUPERHEATING:
                return superheatOre != null;
            case LUNARS:
                return lunarSpell != null && lunarSelection != null && !lunarSelection.isEmpty();
            default:
                return false;
        }
    }
    
    private boolean validateHerbloreConfig() {
        switch (herbloreMode) {
            case CLEAN_HERBS:
                return cleanHerbMode != null;
            case UNFINISHED_POTIONS:
                return unfinishedPotionMode != null;
            case FINISHED_POTIONS:
                return finishedPotion != null;
            default:
                return false;
        }
    }
    
    private boolean validateFletchingConfig() {
        switch (fletchingMode) {
            case DARTS:
                return dartType != null;
            case BOLTS:
                return fletchingBoltType != null;
            case ARROWS:
                return arrowType != null;
            case JAVELINS:
                return javelinType != null;
            case ARROW_SHAFTS:
                return arrowShaftType != null;
            case JAVELIN_SHAFTS:
                return javelinShaftType != null;
            case BOWS:
                return bowType != null;
            case CROSSBOWS:
                return crossbowType != null;
            case SHIELDS:
                return shieldType != null;
            default:
                return false;
        }
    }
    
    @Override
    public String toString() {
        String method = "";
        switch (skill) {
            case MAGIC:
                method = magicMethod.toString();
                break;
            case HERBLORE:
                method = herbloreMode.toString();
                break;
            case FLETCHING:
                method = fletchingMode.toString();
                break;
            case CRAFTING:
                method = craftingMethod.toString();
                break;
            case COOKING:
                method = cookingMethod.toString();
                break;
        }
        return String.format("ConfigData{skill=%s, method=%s}", skill, method);
    }
}