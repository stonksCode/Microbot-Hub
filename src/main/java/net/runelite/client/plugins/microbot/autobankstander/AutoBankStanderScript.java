package net.runelite.client.plugins.microbot.autobankstander;

import java.util.concurrent.TimeUnit;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.SuperheatOre;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.EnchantingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.JewelleryType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarSpell;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarsProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.crafting.CraftingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.cooking.CookingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.HerbloreProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.FletchingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ArrowShaftType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.JavelinShaftType;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class AutoBankStanderScript extends Script {
    private static final long STATE_TIMEOUT_MS = 30_000;
    private static final int MAX_BANK_FAILURES = 3;

    private AutoBankStanderState state = AutoBankStanderState.INITIALIZING;
    private ConfigData configData;
    private long stateStartTime = System.currentTimeMillis(); // remember when we started this state for timeout checking
    private int consecutiveBankFailures;
    private BankStandingProcessor processor;
    private AutoBankStanderPlugin plugin;

    public boolean run(ConfigData configData) {
        this.configData = configData; // save the config data so we can use it later
        this.state = AutoBankStanderState.INITIALIZING; // reset state to beginning
        this.stateStartTime = System.currentTimeMillis(); // reset state timer
        this.consecutiveBankFailures = 0;
        this.processor = null; // clear any existing processor
        log.info("Starting Auto Bank Stander script with config: {}", configData);
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false, stopping");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in, waiting");
                    return;
                }
                if (Rs2Player.isMoving() || Rs2Player.isAnimating(3000)) {
                    log.info("Player is moving or animating, waiting (extended check for crafting)");
                    return;
                }

                long startTime = System.currentTimeMillis(); // remember when this loop started

                // A Make All batch can legitimately run far longer than the normal state
                // timeout. Keep the state alive while the processor reports real progress;
                // once progress stops, the regular timeout applies.
                boolean activelyProcessing = state == AutoBankStanderState.PROCESSING
                        && processor != null && processor.isActivelyProcessing();
                if (activelyProcessing) {
                    stateStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - stateStartTime > STATE_TIMEOUT_MS) {
                    log.info("State timeout after {} ms, resetting to INITIALIZING", STATE_TIMEOUT_MS);
                    changeState(AutoBankStanderState.INITIALIZING);
                    return;
                }

                switch (state) {
                    case INITIALIZING: handleInitializing(); break; // handle the setup phase
                    case BANKING: handleBanking(); break; // handle banking operations
                    case PROCESSING: handleProcessing(); break; // handle the processing activity
                    case ERROR_RECOVERY: handleErrorRecovery(); break; // handle error situations
                }

                long endTime = System.currentTimeMillis(); // remember when this loop ended
                long totalTime = endTime - startTime; // calculate how long this loop took
                log.info("Total time for loop: {}ms", totalTime);
            } catch (Exception ex) {
                log.error("Error in main script loop: {}", ex.getMessage(), ex);
                changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery on exception
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleInitializing() {
        log.info("State: INITIALIZING");
        Microbot.status = "Initializing..."; // tell the user we are starting up
        
        // Keep the existing processor when re-initialising after a recovery. It holds
        // batch and material state that must not be lost while the inventory is untouched.
        if (processor == null) {
            processor = createProcessor();
        }
        if (processor == null) {
            log.info("Failed to create processor for skill: {}", configData.getSkill());
            shutdown(); // stop the plugin
            return;
        }
        
        // validate processor configuration
        if (!processor.validate()) {
            log.info("Processor validation failed");
            shutdown(); // stop the plugin
            return;
        }
        
        log.info("Initialization complete - switching to banking");
        changeState(AutoBankStanderState.BANKING); // switch to banking to get our supplies
    }

    private void handleBanking() {
        log.info("State: BANKING");
        Microbot.status = "Banking..."; // tell the user we are handling banking
        
        if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
            log.info("Not near bank - walking to bank");
            Rs2Bank.walkToBank(); // walk to the nearest bank
            return;
        }
        
        if (!Rs2Bank.isOpen()) { // if the bank interface isn't open yet
            log.info("Opening bank");
            Rs2Bank.openBank(); // click to open the bank
            boolean opened = sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank opens
            if (!opened) {
                log.info("Failed to open bank within timeout");
            }
            return;
        }
        
        // check if we already have all the required items
        if (processor.hasRequiredItems()) {
            log.info("Have all required items - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
            return;
        }
        
        // perform banking operations via processor
        boolean bankingSuccess = processor.performBanking();
        if (bankingSuccess) {
            consecutiveBankFailures = 0;
            log.info("Banking complete - switching to processing");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBankStanderState.PROCESSING); // switch to processing mode
        } else {
            consecutiveBankFailures++;
            log.info("Banking failed (attempt {}/{})", consecutiveBankFailures, MAX_BANK_FAILURES);
            Microbot.status = "Waiting for materials...";
            if (consecutiveBankFailures >= MAX_BANK_FAILURES) {
                log.info("No materials available after {} attempts - shutting down", consecutiveBankFailures);
                shutdown(); // stop the plugin
            } else {
                changeState(AutoBankStanderState.ERROR_RECOVERY); // retry after a short pause
            }
        }
    }

    private void handleProcessing() {
        log.info("State: PROCESSING");
        Microbot.status = processor.getStatusMessage(); // tell the user what we are doing
        
        // An active Make All batch must never be interrupted for a banking trip, even
        // when its ingredients have temporarily hit zero.
        if (processor.isActivelyProcessing()) {
            log.info("Processor is actively making items - waiting");
            return;
        }

        // check if we can continue processing
        if (!processor.canContinueProcessing()) {
            consecutiveBankFailures++;
            log.info("Cannot continue processing (attempt {}/{})", consecutiveBankFailures, MAX_BANK_FAILURES);
            if (consecutiveBankFailures >= MAX_BANK_FAILURES) {
                log.info("No way to continue after {} attempts - shutting down", consecutiveBankFailures);
                shutdown(); // stop the plugin
            } else {
                changeState(AutoBankStanderState.ERROR_RECOVERY);
            }
            return;
        }
        
        // check if we need to go back to banking
        if (!processor.hasRequiredItems()) {
            log.info("Missing required items - going back to banking");
            changeState(AutoBankStanderState.BANKING); // go back to banking to get more items
            return;
        }
        
        // perform the processing activity
        boolean processingSuccess = processor.process();
        if (processingSuccess) {
            consecutiveBankFailures = 0;
        } else {
            log.info("Processing failed - going to error recovery");
            changeState(AutoBankStanderState.ERROR_RECOVERY); // switch to error recovery
        }
    }

    private void handleErrorRecovery() {
        log.info("State: ERROR_RECOVERY");
        Microbot.status = "Recovering from error..."; // tell the user we are fixing issues
        
        // check for timeout
        if (System.currentTimeMillis() - stateStartTime > 60000) { // if we've been stuck for more than 60 seconds
            log.info("State timeout - resetting to initializing");
            changeState(AutoBankStanderState.INITIALIZING); // go back to the beginning
            return;
        }
        
        // Vary the recovery pause instead of retrying on a rigid cadence.
        Rs2Random.waitEx(700, 350);
        log.info("Attempting recovery - going to banking");
        changeState(AutoBankStanderState.BANKING); // try to recover by going to banking
    }

    private BankStandingProcessor createProcessor() {
        SkillType skill = configData.getSkill();
        log.info("Creating processor for skill: {}", skill);
        switch (skill) {
            case MAGIC:
                return createMagicProcessor();
            case HERBLORE:
                log.info("Creating herblore processor for mode: {}", configData.getHerbloreMode());
                return new HerbloreProcessor(
                    configData.getHerbloreMode(),
                    configData.getCleanHerbMode(),
                    configData.getUnfinishedPotionMode(),
                    configData.getFinishedPotion(),
                    configData.isUseAmuletOfChemistry(),
                    configData.isHerbloreTurboMode(),
                    configData.getHerbloreTurboLimit(),
                    configData.getHerbloreSleepMin(),
                    configData.getHerbloreSleepMax(),
                    configData.getHerbloreSleepTarget()
                );
            case FLETCHING:
                log.info("Entering fletching processor creation");
                return createFletchingProcessor();
            case CRAFTING:
                log.info("Creating crafting processor for {} / {}",
                        configData.getCraftingMethod(), configData.getCraftingRecipe());
                return new CraftingProcessor(configData.getCraftingMethod(), configData.getCraftingRecipe());
            case COOKING:
                log.info("Creating cooking processor for {} / {}",
                        configData.getCookingMethod(), configData.getCookingRecipe());
                return new CookingProcessor(configData.getCookingMethod(), configData.getCookingRecipe());
            default:
                log.info("Skill not yet implemented: {}", skill);
                return null;
        }
    }
    
    private BankStandingProcessor createMagicProcessor() {
        MagicMethod method = configData.getMagicMethod();
        switch (method) {
            case ENCHANTING:
                log.info("Creating enchanting processor for bolt type: {}", configData.getBoltType());
                return new EnchantingProcessor(configData.getBoltType());
            case JEWELRY_ENCHANTING:
                log.info("Creating jewellery enchanting processor for {}", configData.getJewelleryType());
                return new MagicItemProcessor(configData.getJewelleryType().toRecipe());
            case LUNARS:
                log.info("Creating lunars processor for {} / {}",
                        configData.getLunarSpell(), configData.getLunarSelection());
                return new LunarsProcessor(configData.getLunarSpell(), configData.getLunarSelection());
            case ALCHING:
                log.info("Creating alchemy processor for {} ({})", configData.getAlchItemName(),
                        configData.isHighAlchemy() ? "high" : "low");
                return new MagicItemProcessor(new MagicItemProcessor.Recipe(
                        "Alchemy: " + configData.getAlchItemName(),
                        configData.isHighAlchemy() ? 55 : 21,
                        net.runelite.client.plugins.skillcalculator.skills.MagicAction.HIGH_LEVEL_ALCHEMY,
                        configData.isHighAlchemy()
                                ? MagicItemProcessor.CastMode.ALCH_HIGH
                                : MagicItemProcessor.CastMode.ALCH_LOW,
                        new int[]{net.runelite.api.gameval.ItemID.FIRERUNE,
                                net.runelite.api.gameval.ItemID.NATURERUNE},
                        configData.isHighAlchemy() ? new int[]{5, 1} : new int[]{3, 1},
                        new MagicItemProcessor.Input[]{MagicItemProcessor.Input.name(configData.getAlchItemName())},
                        0));
            case SUPERHEATING:
                log.info("Creating superheating processor for {}", configData.getSuperheatOre());
                return new MagicItemProcessor(configData.getSuperheatOre().toRecipe());
            default:
                log.info("Unknown magic method: {}", method);
                return null;
        }
    }
    
    private BankStandingProcessor createFletchingProcessor() {
        FletchingMode mode = configData.getFletchingMode();
        log.info("Creating fletching processor for mode: {}", mode);
        log.info("Fletching config details - dart: {}, bolt: {}, arrow: {}, javelin: {}, bow: {}, crossbow: {}, shield: {}", 
            configData.getDartType(), configData.getFletchingBoltType(), configData.getArrowType(), 
            configData.getJavelinType(), configData.getBowType(), configData.getCrossbowType(), configData.getShieldType());
        
        FletchingProcessor processor = new FletchingProcessor(
            mode,
            configData.getDartType(),
            configData.getFletchingBoltType(),
            configData.getArrowType(),
            configData.getJavelinType(),
            configData.getArrowShaftType(),
            configData.getJavelinShaftType(),
            configData.getBowType(),
            configData.getCrossbowType(),
            configData.getShieldType()
        );
        
        log.info("Fletching processor created successfully");
        return processor;
    }

    // helper method to change state with timeout reset
    private void changeState(AutoBankStanderState newState) {
        if (newState != state) { // if we are actually changing to a different state
            log.info("State change: {} -> {}", state, newState);
            state = newState; // update our current state
            stateStartTime = System.currentTimeMillis(); // reset our timeout timer for the new state
        }
    }

    public void setPlugin(AutoBankStanderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void shutdown() {
        if (!isRunning()) {
            log.info("Script already shutdown, ignoring");
            return;
        }
        log.info("Shutting down Auto Bank Stander script");
        super.shutdown(); // clean up the script properly
        
        // notify plugin to update panel state
        if (plugin != null) {
            plugin.updatePanelState();
        }
        
        log.info("Auto Bank Stander script shutdown complete");
    }
}