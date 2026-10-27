package net.runelite.client.plugins.microbot.gauntlethelper;

import com.google.inject.Inject;
import net.runelite.api.HeadIcon;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GauntletHelperScript extends Script {

    public static volatile State gh_state = State.idle;

    private volatile Rs2PrayerEnum nextPrayer = null;
    private volatile HeadIcon bossHeadIcon = null;
    private volatile Rs2NpcModel hunllef = null;
    private volatile Rs2NpcModel tornado = null;

    private static final int CG_TORNADO = 9039;
    private static final int PADDLEFISH_HEAL_VALUE = 20;

    private static final int CD_NPC = 450;
    private static final int CD_PRAYOFFENCE = 100;
    private static final int CD_EAT = 600;
    private static final int CD_DRINK = 600;
    private static final int CD_WEAPON = 600;
    private static final int CD_RECENTLY = 25;
    private static final int CD_STEEL = 300;
    private static final int CD_ATTACK = 750;
    // Minimum time between clicks of the same prayer: must outlast a server tick so a pending
    // activation is not clicked again (which would toggle the protection straight back off).
    private static final int CD_PRAYER_CONFIRM = 800;
    // How long to keep retrying to re-engage the boss after a click that may have been dropped.
    private static final int REATTACK_WINDOW = 2500;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );

    private static final int[] BOW_IDS = {
            ItemID.GAUNTLET_RANGED_T3_HM, ItemID.GAUNTLET_RANGED_T3,
            ItemID.GAUNTLET_RANGED_T2_HM, ItemID.GAUNTLET_RANGED_T2,
            ItemID.GAUNTLET_RANGED_T1_HM, ItemID.GAUNTLET_RANGED_T1
    };

    private static final int[] STAFF_IDS = {
            ItemID.GAUNTLET_MAGIC_T3_HM, ItemID.GAUNTLET_MAGIC_T3,
            ItemID.GAUNTLET_MAGIC_T2_HM, ItemID.GAUNTLET_MAGIC_T2,
            ItemID.GAUNTLET_MAGIC_T1_HM, ItemID.GAUNTLET_MAGIC_T1
    };

    private static final int[] HALBERD_IDS = {
            ItemID.GAUNTLET_MELEE_T3_HM, ItemID.GAUNTLET_MELEE_T3,
            ItemID.GAUNTLET_MELEE_T2_HM, ItemID.GAUNTLET_MELEE_T2,
            ItemID.GAUNTLET_MELEE_T1_HM, ItemID.GAUNTLET_MELEE_T1
    };

    private final GauntletHelperConfig config;

    private long now = -1;
    private long TIME_NPC = -1;
    private long TIME_PRAYOFFENCE = -1;
    private long TIME_EAT_ATTEMPTED = -1;
    private long TIME_DRINK = -1;
    private long TIME_WEAPON = -1;
    private long TIME_STEEL = -1;
    private long TIME_ATTACK = -1;
    private long TIME_ATTACK_CONTINUE = -1;

    private final AtomicBoolean attackNeeded = new AtomicBoolean(false);
    private final Map<Rs2PrayerEnum, Long> pendingPrayerClicks = new ConcurrentHashMap<>();
    private volatile boolean attackClickIssued = false;
    private volatile boolean shutdownRequested = false;

    @Inject
    public GauntletHelperScript(GauntletHelperConfig config) {
        this.config = config;
    }

    public boolean run() {
        shutdownRequested = false;
        now = System.currentTimeMillis();
        gh_state = config.startstate();
        nextPrayer = null;
        bossHeadIcon = null;
        hunllef = null;
        tornado = null;
        // Backdate so the very first loop iteration runs checkState() before any fighting logic,
        // preventing a one-tick prayer toggle during the prep phase.
        TIME_NPC = now - CD_NPC;
        TIME_PRAYOFFENCE = now;
        TIME_EAT_ATTEMPTED = now;
        TIME_DRINK = now;
        TIME_WEAPON = now;
        TIME_STEEL = now;
        TIME_ATTACK = now;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (shutdownRequested) return;
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                now = System.currentTimeMillis();

                if (now - TIME_NPC > CD_NPC) checkState();

                if (gh_state != State.fighting) return;

                // Fast section: protection prayer reacts to the boss attack style.
                checkProtectPrayers();

                if (now - TIME_PRAYOFFENCE > CD_PRAYOFFENCE) checkAttackPrayers();
                if (now - TIME_STEEL > CD_STEEL) checkSteelSkin();

                // Slow section: consumables and weapon swaps.
                if (now - TIME_EAT_ATTEMPTED > CD_EAT) checkFood();
                if (now - TIME_DRINK > CD_DRINK) checkPrayerPotions();
                if (now - TIME_WEAPON > CD_WEAPON) checkWeapon();

                checkAttack();

                logVerbose("Loop took " + (System.currentTimeMillis() - now) + "ms");
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    } // End of run

    // ----------------------------------------------------------------------------------------------------------------
    // --- State / NPC tracking

    private void checkState() {
        TIME_NPC = now;

        Rs2NpcModel found = Microbot.getRs2NpcCache().query()
                .where(npc -> HUNLLEF_IDS.contains(npc.getId()))
                .nearest();
        hunllef = found;

        if (found == null) {
            if (gh_state == State.fighting) {
                logVerbose("Hunllef lost - fight ended.");
                gh_state = State.boss_dead;
                nextPrayer = null;
                bossHeadIcon = null;
                tornado = null;
                Rs2Prayer.disableAllPrayers();
            }
            return;
        }

        if (gh_state != State.fighting) {
            logVerbose("Hunllef found - entering fight.");
            gh_state = State.fighting;
        }

        tornado = Microbot.getRs2NpcCache().query().withId(CG_TORNADO).nearest();
        bossHeadIcon = Microbot.getClientThread().runOnClientThreadOptional(found::getHeadIcon).orElse(null);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Event hooks (called from the client thread by the plugin)

    public void onEnemyMagic() {
        nextPrayer = Rs2PrayerEnum.PROTECT_MAGIC;
    }

    public void onEnemyRange() {
        nextPrayer = Rs2PrayerEnum.PROTECT_RANGE;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Protection prayer

    private void checkProtectPrayers() {
        activatePrayer(nextPrayer);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Weapon switching (head icon = boss protection prayer; use a style it is not protecting)

    private void checkWeapon() {
        HeadIcon icon = bossHeadIcon;
        if (icon == null) return;
        switch (icon) {
            case MELEE:
                handleMeleeHeadIcon();
                break;
            case RANGED:
                handleRangedHeadIcon();
                break;
            case MAGIC:
                handleMagicHeadIcon();
                break;
            default:
                break;
        }
    }

    private void handleMeleeHeadIcon() {
        if (!isStaffEquipped() && hasStaffInInventory()) {
            equipStaff();
        } else if (!isBowEquipped() && !isStaffEquipped() && hasBowInInventory()) {
            equipBow();
        }
    }

    private void handleRangedHeadIcon() {
        if (!isStaffEquipped() && hasStaffInInventory()) {
            equipStaff();
        } else if (!isHalberdEquipped() && !isStaffEquipped() && hasHalberdInInventory()) {
            equipHalberd();
        }
    }

    private void handleMagicHeadIcon() {
        if (!isBowEquipped() && hasBowInInventory()) {
            equipBow();
        } else if (!isHalberdEquipped() && !isBowEquipped() && hasHalberdInInventory()) {
            equipHalberd();
        }
    }

    private boolean hasWeaponInInventory(int[] ids) {
        return Rs2Inventory.contains(ids);
    }

    private boolean isWeaponEquipped(int[] ids) {
        return Rs2Equipment.isWearing(ids);
    }

    private void equipBestAvailable(int[] ids) {
        for (int id : ids) {
            if (Rs2Inventory.contains(id)) {
                Rs2Inventory.equip(id);
                logVerbose("weapon change attempted");
                armAttack();
                break;
            }
        }
    }

    private boolean hasBowInInventory() { return hasWeaponInInventory(BOW_IDS); }
    private boolean hasStaffInInventory() { return hasWeaponInInventory(STAFF_IDS); }
    private boolean hasHalberdInInventory() { return hasWeaponInInventory(HALBERD_IDS); }
    private boolean isBowEquipped() { return isWeaponEquipped(BOW_IDS); }
    private boolean isStaffEquipped() { return isWeaponEquipped(STAFF_IDS); }
    private boolean isHalberdEquipped() { return isWeaponEquipped(HALBERD_IDS); }

    private void equipBow() { equipBestAvailable(BOW_IDS); TIME_WEAPON = now; }
    private void equipStaff() { equipBestAvailable(STAFF_IDS); TIME_WEAPON = now; }
    private void equipHalberd() { equipBestAvailable(HALBERD_IDS); TIME_WEAPON = now; }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Offensive / defensive prayers

    private void checkAttackPrayers() {
        TIME_PRAYOFFENCE = now;
        if (isBowEquipped() && !isAnyPrayerActive(
                Rs2PrayerEnum.RIGOUR, Rs2PrayerEnum.DEAD_EYE, Rs2PrayerEnum.EAGLE_EYE, Rs2PrayerEnum.HAWK_EYE)) {
            activatePrayer(rangeAttackPrayer());
        } else if (isStaffEquipped() && !isAnyPrayerActive(
                Rs2PrayerEnum.AUGURY, Rs2PrayerEnum.MYSTIC_VIGOUR, Rs2PrayerEnum.MYSTIC_MIGHT, Rs2PrayerEnum.MYSTIC_LORE)) {
            activatePrayer(magicAttackPrayer());
        } else if (isHalberdEquipped() && !isAnyPrayerActive(
                Rs2PrayerEnum.PIETY, Rs2PrayerEnum.CHIVALRY, Rs2PrayerEnum.ULTIMATE_STRENGTH,
                Rs2PrayerEnum.SUPERHUMAN_STRENGTH)) {
            activatePrayer(Rs2PrayerEnum.PIETY);
        }
    }

    private Rs2PrayerEnum rangeAttackPrayer() {
        if (config.HigherPrayers()) return Rs2PrayerEnum.RIGOUR;
        if (config.TitansPrayers()) return Rs2PrayerEnum.DEAD_EYE;
        return Rs2PrayerEnum.EAGLE_EYE;
    }

    private Rs2PrayerEnum magicAttackPrayer() {
        if (config.HigherPrayers()) return Rs2PrayerEnum.AUGURY;
        if (config.TitansPrayers()) return Rs2PrayerEnum.MYSTIC_VIGOUR;
        return Rs2PrayerEnum.MYSTIC_MIGHT;
    }

    private boolean isAnyPrayerActive(Rs2PrayerEnum... prayers) {
        for (Rs2PrayerEnum prayer : prayers) {
            if (Rs2Prayer.isPrayerActive(prayer)) return true;
        }
        return false;
    }

    private void checkSteelSkin() {
        if (config.HigherPrayers()) return;
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.STEEL_SKIN) && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            TIME_STEEL = now;
            activatePrayer(Rs2PrayerEnum.STEEL_SKIN);
        }
    }

    /**
     * Issues a single, non-blocking prayer click.
     * <p>
     * {@link Rs2Prayer#toggle(Rs2PrayerEnum, boolean)} blocks until the server confirms the change
     * (up to a full tick). In the corrupted Gauntlet a stalled loop means a protection swap is issued
     * late and the hit lands. Here the click is fired immediately instead, so the loop keeps polling
     * every 100ms. The per-prayer confirmation cooldown prevents clicking the same prayer twice before
     * the server applies it, which would toggle the protection straight back off.
     */
    private void activatePrayer(Rs2PrayerEnum prayer) {
        if (prayer == null) return;
        if (Rs2Prayer.isPrayerActive(prayer)) {
            pendingPrayerClicks.remove(prayer);
            return;
        }
        Long lastClick = pendingPrayerClicks.get(prayer);
        if (lastClick != null && now - lastClick < CD_PRAYER_CONFIRM) return;
        pendingPrayerClicks.put(prayer, now);
        Rs2Prayer.toggle(prayer);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Food / drink

    private void checkFood() {
        if (!config.enablefood()) return;

        int currentHp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (currentHp <= 0) return;
        int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int missingHp = maxHp - currentHp;
        if (missingHp < (PADDLEFISH_HEAL_VALUE - config.eatoverhealvalue())) return;

        if (config.eatFoodChain()) {
            EatFood();
            return;
        }
        if (currentHp < config.lowhpeatvalue()) {
            EatFood();
            return;
        }
        if (config.TornadoCheck() && tornado == null) return;
        if (config.eatFoodMoving() && Rs2Player.isMoving()) {
            EatFood();
        }
    }

    private void EatFood() {
        if (!config.enablefood()) return;
        if (now - TIME_EAT_ATTEMPTED < CD_RECENTLY) return;

        boolean ate = Rs2Inventory.interact(item -> {
            String name = item.getName();
            return name != null && name.equalsIgnoreCase("Paddlefish");
        }, "Eat");

        if (!ate) return;

        logVerbose("Eat attempted");
        TIME_EAT_ATTEMPTED = now;
        // Re-arm the attack even while moving: eating interrupts combat, and once we stop we must
        // resume attacking instead of sitting idle until auto-retaliate happens to fire.
        armAttack();
    }

    private void checkPrayerPotions() {
        if (!config.enabledrink()) return;

        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        if (currentPrayer >= config.ppotvalue()) return;

        boolean drank = Rs2Inventory.interact(item -> {
            String name = item.getName();
            return name != null && name.toLowerCase().contains("egniol");
        }, "Drink");

        if (drank) {
            logVerbose("Drink attempted");
            TIME_DRINK = now;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Auto attack

    private void armAttack() {
        attackNeeded.set(true);
        attackClickIssued = false;
        TIME_ATTACK_CONTINUE = now + REATTACK_WINDOW;
    }

    private void checkAttack() {
        if (!config.autoattack()) return;
        if (hunllef == null) return;
        if (!attackNeeded.get()) return;

        // Re-attack landed (our own attack animation is running) or the retry window closed.
        if (attackClickIssued && now - TIME_ATTACK > CD_ATTACK && Rs2Player.isAnimating()) {
            attackNeeded.set(false);
            return;
        }
        if (TIME_ATTACK_CONTINUE > 0 && now > TIME_ATTACK_CONTINUE) {
            attackNeeded.set(false);
            return;
        }
        if (Rs2Player.isMoving()) return;
        // Eating locks us out of attacking for ~3 ticks, so wait that long after a bite.
        if (now - TIME_EAT_ATTEMPTED <= CD_EAT * 3) return;
        if (now - TIME_ATTACK <= CD_ATTACK) return;

        logVerbose("Attempting attack");
        hunllef.click("attack");
        TIME_ATTACK = now;
        // Do not clear attackNeeded here: if this click is dropped (still eating, briefly out of
        // range) it is retried on a later tick instead of leaving the player idle.
        attackClickIssued = true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // --- Misc

    private void logVerbose(String msg) {
        if (config.verboselog()) {
            Microbot.log(msg);
        }
    }

    public Rs2PrayerEnum getNextPrayer() {
        return nextPrayer;
    }

    @Override
    public void shutdown() {
        shutdownRequested = true;
        gh_state = State.idle;
        nextPrayer = null;
        bossHeadIcon = null;
        super.shutdown();
        // Disable prayers off the client thread so shutDown() never blocks on menu waits.
        Thread cleanup = new Thread(() -> {
            try {
                Rs2Prayer.disableAllPrayers();
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, "GauntletHelper-Cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }
} // End of Script
