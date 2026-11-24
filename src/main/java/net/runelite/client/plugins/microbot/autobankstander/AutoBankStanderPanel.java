package net.runelite.client.plugins.microbot.autobankstander;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.autobankstander.config.ConfigData;
import net.runelite.client.plugins.microbot.autobankstander.processors.SkillType;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.MagicMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.SuperheatOre;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.BoltType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.JewelleryType;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars.LunarSpell;
import net.runelite.client.plugins.microbot.autobankstander.skills.crafting.CraftingMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.crafting.CraftingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.cooking.CookingMethod;
import net.runelite.client.plugins.microbot.autobankstander.skills.cooking.CookingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ArrowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ArrowShaftType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.JavelinShaftType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.CrossbowType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.DartType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.JavelinType;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.ShieldType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class AutoBankStanderPanel extends PluginPanel {

    // available skills for bank standing
    private enum BankStandingSkill {
        CRAFTING(Skill.CRAFTING),
        MAGIC(Skill.MAGIC),
        FLETCHING(Skill.FLETCHING),
        HERBLORE(Skill.HERBLORE),
        COOKING(Skill.COOKING);
        
        private final Skill skill;
        
        BankStandingSkill(Skill skill) {
            this.skill = skill;
        }
        
        public Skill getSkill() {
            return skill;
        }
    }

    private final AutoBankStanderPlugin plugin;
    private final SkillIconManager iconManager;
    
    // ui components
    private JLabel titleLabel;
    private MaterialTabGroup skillTabGroup;
    private MaterialTab currentTab;
    private JPanel methodButtonPanel;
    private JPanel configurationPanel;
    private JButton startStopButton;
    private JLabel statusLabel;
    
    // current configuration state
    private ConfigData currentConfig = new ConfigData();
    private BankStandingSkill selectedBankSkill = BankStandingSkill.MAGIC;
    private JButton selectedMethodButton = null;

    @Inject
    public AutoBankStanderPanel(AutoBankStanderPlugin plugin, SkillIconManager iconManager) {
        super();
        this.plugin = plugin;
        this.iconManager = iconManager;
        
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new GridBagLayout());
        
        // load current configuration from plugin
        loadCurrentConfiguration();
        
        initializeComponents();
        layoutComponents();
        
        // select initial skill tab and update display
        if (currentTab != null) {
            skillTabGroup.select(currentTab);
        }
        selectSkillTab(selectedBankSkill);
    }
    
    private void loadCurrentConfiguration() {
        // get current configuration from plugin (may be default if none set)
        ConfigData pluginConfig = plugin.getCurrentConfig();
        if (pluginConfig != null) {
            currentConfig = new ConfigData(pluginConfig);
            // map SkillType to BankStandingSkill
            if (currentConfig.getSkill() == SkillType.MAGIC) {
                selectedBankSkill = BankStandingSkill.MAGIC;
            } else if (currentConfig.getSkill() == SkillType.HERBLORE) {
                selectedBankSkill = BankStandingSkill.HERBLORE;
            } else if (currentConfig.getSkill() == SkillType.FLETCHING) {
                selectedBankSkill = BankStandingSkill.FLETCHING;
            } else if (currentConfig.getSkill() == SkillType.CRAFTING) {
                selectedBankSkill = BankStandingSkill.CRAFTING;
            } else if (currentConfig.getSkill() == SkillType.COOKING) {
                selectedBankSkill = BankStandingSkill.COOKING;
            }
            log.info("Loaded configuration from plugin: {}", currentConfig);
        }
    }

    private void initializeComponents() {
        // title label
        titleLabel = new JLabel("Auto Bankstander");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(FontManager.getRunescapeBoldFont().getSize() * 1.5f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        // skill icon tabs (like skill calculator)
        skillTabGroup = new MaterialTabGroup();
        skillTabGroup.setLayout(new GridLayout(1, 5, 7, 7)); // 5 skills in 1 row
        
        // add skill tabs for the 5 selected skills
        for (BankStandingSkill bankSkill : BankStandingSkill.values()) {
            ImageIcon icon = new ImageIcon(iconManager.getSkillImage(bankSkill.getSkill(), true));
            MaterialTab tab = new MaterialTab(icon, skillTabGroup, null);
            tab.setOnSelectEvent(() -> {
                selectSkillTab(bankSkill);
                return true;
            });
            skillTabGroup.addTab(tab);
            
            // select initial tab
            if (bankSkill == selectedBankSkill) {
                currentTab = tab;
            }
        }
        
        // method button panel for 2-column grid
        methodButtonPanel = new JPanel();
        methodButtonPanel.setBorder(new EmptyBorder(15, 0, 10, 0));
        
        // configuration panel for method-specific options
        configurationPanel = new JPanel();
        configurationPanel.setLayout(new BoxLayout(configurationPanel, BoxLayout.Y_AXIS));
        configurationPanel.setBorder(new EmptyBorder(10, 0, 15, 0));
        configurationPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // start/stop button
        startStopButton = new JButton("Start");
        startStopButton.setFont(FontManager.getRunescapeBoldFont());
        startStopButton.setBackground(ColorScheme.BRAND_ORANGE);
        startStopButton.setForeground(Color.WHITE);
        startStopButton.setFocusPainted(false);
        startStopButton.addActionListener(e -> onStartStopClicked());
        
        // status label
        statusLabel = new JLabel("");
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private void layoutComponents() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;

        // title at top
        add(titleLabel, c);
        c.gridy++;
        
        // skill tabs
        add(skillTabGroup, c);
        c.gridy++;
        
        // method button panel
        add(methodButtonPanel, c);
        c.gridy++;
        
        // configuration panel
        add(configurationPanel, c);
        c.gridy++;
        
        // spacer to push status/button to bottom
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(Box.createVerticalGlue(), c);
        c.gridy++;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        
        // status label
        add(statusLabel, c);
        c.gridy++;
        
        // start/stop button
        add(startStopButton, c);
    }
    
    private void selectSkillTab(BankStandingSkill bankSkill) {
        selectedBankSkill = bankSkill;
        
        // map to our SkillType enum for supported skills
        if (bankSkill == BankStandingSkill.MAGIC) {
            currentConfig.setSkill(SkillType.MAGIC);
            updateMethodAndConfigArea();
        } else if (bankSkill == BankStandingSkill.HERBLORE) {
            currentConfig.setSkill(SkillType.HERBLORE);
            updateMethodAndConfigArea();
        } else if (bankSkill == BankStandingSkill.FLETCHING) {
            currentConfig.setSkill(SkillType.FLETCHING);
            updateMethodAndConfigArea();
        } else if (bankSkill == BankStandingSkill.CRAFTING) {
            currentConfig.setSkill(SkillType.CRAFTING);
            updateMethodAndConfigArea();
        } else if (bankSkill == BankStandingSkill.COOKING) {
            currentConfig.setSkill(SkillType.COOKING);
            updateMethodAndConfigArea();
        }
        
        log.info("Selected skill: {}", bankSkill);
    }
    
    private void updateMethodAndConfigArea() {
        methodButtonPanel.removeAll();
        configurationPanel.removeAll();
        selectedMethodButton = null;
        
        if (selectedBankSkill == BankStandingSkill.MAGIC) {
            createMagicMethodButtons();
        } else if (selectedBankSkill == BankStandingSkill.HERBLORE) {
            createHerbloreMethodButtons();
        } else if (selectedBankSkill == BankStandingSkill.FLETCHING) {
            createFletchingMethodButtons();
        } else if (selectedBankSkill == BankStandingSkill.CRAFTING) {
            createCraftingMethodButtons();
        } else if (selectedBankSkill == BankStandingSkill.COOKING) {
            createCookingMethodButtons();
        }


        methodButtonPanel.revalidate();
        methodButtonPanel.repaint();
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }
    
    private void createMagicMethodButtons() {
        MagicMethod[] methods = MagicMethod.values();
        setupMethodButtonGrid(methodButtonPanel, methods.length);
        
        for (MagicMethod method : methods) {
            JButton methodButton = createMethodButton(method.getDisplayName());
            methodButton.addActionListener(e -> {
                selectMethodButton(methodButton);
                currentConfig.setMagicMethod(method);
                updateMagicConfiguration(method);
                log.info("Selected magic method: {}", method);
            });
            methodButtonPanel.add(methodButton);
        }
        
        // auto-select first method if none selected
        if (currentConfig.getMagicMethod() != null) {
            // find and select the current method button
            Component[] components = methodButtonPanel.getComponents();
            for (int i = 0; i < methods.length && i < components.length; i++) {
                if (methods[i] == currentConfig.getMagicMethod() && components[i] instanceof JButton) {
                    selectMethodButton((JButton) components[i]);
                    updateMagicConfiguration(methods[i]);
                    break;
                }
            }
        }
    }
    
    private void createHerbloreMethodButtons() {
        Mode[] methods = Mode.values();
        setupMethodButtonGrid(methodButtonPanel, methods.length);
        
        for (Mode method : methods) {
            JButton methodButton = createMethodButton(method.toString());
            methodButton.addActionListener(e -> {
                selectMethodButton(methodButton);
                currentConfig.setHerbloreMode(method);
                updateHerbloreConfiguration(method);
                log.info("Selected herblore method: {}", method);
            });
            methodButtonPanel.add(methodButton);
        }
        
        // auto-select first method if none selected
        if (currentConfig.getHerbloreMode() != null) {
            // find and select the current method button
            Component[] components = methodButtonPanel.getComponents();
            for (int i = 0; i < methods.length && i < components.length; i++) {
                if (methods[i] == currentConfig.getHerbloreMode() && components[i] instanceof JButton) {
                    selectMethodButton((JButton) components[i]);
                    updateHerbloreConfiguration(methods[i]);
                    break;
                }
            }
        }
    }
    
    private void updateMagicConfiguration(MagicMethod method) {
        // remove existing config components
        removeConfigComponents();
        
        switch (method) {
            case ENCHANTING:
                JComboBox<BoltType> boltDropdown = new JComboBox<>(BoltType.values());
                boltDropdown.setSelectedItem(currentConfig.getBoltType());
                boltDropdown.addActionListener(e -> {
                    currentConfig.setBoltType((BoltType) boltDropdown.getSelectedItem());
                });
                
                JPanel boltPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                boltPanel.add(boltDropdown);
                configurationPanel.add(boltPanel);
                break;

            case JEWELRY_ENCHANTING:
                JComboBox<JewelleryType> jewelleryDropdown = new JComboBox<>(JewelleryType.values());
                jewelleryDropdown.setSelectedItem(currentConfig.getJewelleryType());
                jewelleryDropdown.addActionListener(e -> currentConfig.setJewelleryType(
                        (JewelleryType) jewelleryDropdown.getSelectedItem()));

                JPanel jewelleryPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                jewelleryPanel.add(jewelleryDropdown);
                configurationPanel.add(jewelleryPanel);
                break;

            case ALCHING:
                JPanel alchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
                JTextField alchItemField = new JTextField(currentConfig.getAlchItemName(), 16);
                alchItemField.setToolTipText("Exact name of the item to alch (e.g. Runite platebody)");
                alchItemField.addActionListener(e -> currentConfig.setAlchItemName(alchItemField.getText().trim()));
                alchItemField.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        currentConfig.setAlchItemName(alchItemField.getText().trim());
                    }
                });

                JComboBox<String> alchTypeDropdown = new JComboBox<>(new String[]{"High alchemy", "Low alchemy"});
                alchTypeDropdown.setSelectedIndex(currentConfig.isHighAlchemy() ? 0 : 1);
                alchTypeDropdown.addActionListener(e -> currentConfig.setHighAlchemy(alchTypeDropdown.getSelectedIndex() == 0));

                alchPanel.add(alchItemField);
                alchPanel.add(alchTypeDropdown);
                configurationPanel.add(alchPanel);
                break;

            case SUPERHEATING:
                JComboBox<SuperheatOre> oreDropdown = new JComboBox<>(SuperheatOre.values());
                oreDropdown.setSelectedItem(currentConfig.getSuperheatOre());
                oreDropdown.addActionListener(e -> currentConfig.setSuperheatOre(
                        (SuperheatOre) oreDropdown.getSelectedItem()));

                JPanel orePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                orePanel.add(oreDropdown);
                configurationPanel.add(orePanel);
                break;

            case LUNARS:
                JComboBox<LunarSpell> lunarSpellDropdown = new JComboBox<>(LunarSpell.values());
                lunarSpellDropdown.setSelectedItem(currentConfig.getLunarSpell());

                String[] lunarSelections = LunarSpell.getSelections(currentConfig.getLunarSpell());
                JComboBox<String> lunarSelectionDropdown = new JComboBox<>(lunarSelections);
                String currentLunarSelection = currentConfig.getLunarSelection();
                if (currentLunarSelection == null
                        || !java.util.Arrays.asList(lunarSelections).contains(currentLunarSelection)) {
                    currentLunarSelection = LunarSpell.getDefaultSelection(currentConfig.getLunarSpell());
                    currentConfig.setLunarSelection(currentLunarSelection);
                }
                lunarSelectionDropdown.setSelectedItem(currentLunarSelection);

                lunarSpellDropdown.addActionListener(e -> {
                    LunarSpell spell = (LunarSpell) lunarSpellDropdown.getSelectedItem();
                    currentConfig.setLunarSpell(spell);
                    lunarSelectionDropdown.setModel(new DefaultComboBoxModel<>(LunarSpell.getSelections(spell)));
                    currentConfig.setLunarSelection(LunarSpell.getDefaultSelection(spell));
                    lunarSelectionDropdown.setSelectedItem(currentConfig.getLunarSelection());
                });
                lunarSelectionDropdown.addActionListener(e -> currentConfig.setLunarSelection(
                        (String) lunarSelectionDropdown.getSelectedItem()));

                JPanel lunarPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
                lunarPanel.add(lunarSpellDropdown);
                lunarPanel.add(lunarSelectionDropdown);
                configurationPanel.add(lunarPanel);
                break;
        }
        
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }
    
    private void updateHerbloreConfiguration(Mode mode) {
        // remove existing config components
        removeConfigComponents();
        
        switch (mode) {
            case CLEAN_HERBS:
                JComboBox<CleanHerbMode> herbModeDropdown = new JComboBox<>(CleanHerbMode.values());
                herbModeDropdown.setSelectedItem(currentConfig.getCleanHerbMode());
                herbModeDropdown.addActionListener(e -> {
                    currentConfig.setCleanHerbMode((CleanHerbMode) herbModeDropdown.getSelectedItem());
                });
                
                JPanel herbPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                herbPanel.add(herbModeDropdown);
                configurationPanel.add(herbPanel);
                break;
            case UNFINISHED_POTIONS:
                JComboBox<UnfinishedPotionMode> potionModeDropdown = new JComboBox<>(UnfinishedPotionMode.values());
                potionModeDropdown.setSelectedItem(currentConfig.getUnfinishedPotionMode());
                potionModeDropdown.addActionListener(e -> {
                    currentConfig.setUnfinishedPotionMode((UnfinishedPotionMode) potionModeDropdown.getSelectedItem());
                });
                
                JPanel potionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                potionPanel.add(potionModeDropdown);
                configurationPanel.add(potionPanel);
                break;
            case FINISHED_POTIONS:
                JComboBox<HerblorePotion> potionDropdown = new JComboBox<>(HerblorePotion.values());
                potionDropdown.setSelectedItem(currentConfig.getFinishedPotion());
                potionDropdown.addActionListener(e -> {
                    currentConfig.setFinishedPotion((HerblorePotion) potionDropdown.getSelectedItem());
                });
                
                JPanel finishedPotionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                finishedPotionPanel.add(potionDropdown);
                configurationPanel.add(finishedPotionPanel);
                configurationPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                
                JCheckBox amuletCheckbox = new JCheckBox("Use amulet of chemistry");
                amuletCheckbox.setSelected(currentConfig.isUseAmuletOfChemistry());
                amuletCheckbox.addActionListener(e -> {
                    currentConfig.setUseAmuletOfChemistry(amuletCheckbox.isSelected());
                });
                
                JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                checkboxPanel.add(amuletCheckbox);
                configurationPanel.add(checkboxPanel);
                break;
        }
        
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }
    
    private void createFletchingMethodButtons() {
        FletchingMode[] methods = FletchingMode.values();
        setupMethodButtonGrid(methodButtonPanel, methods.length);
        
        for (FletchingMode method : methods) {
            JButton methodButton = createMethodButton(method.getDisplayName());
            methodButton.addActionListener(e -> {
                selectMethodButton(methodButton);
                log.info("=== USER SELECTED FLETCHING METHOD: {} ===", method);
                currentConfig.setFletchingMode(method);
                updateFletchingConfiguration(method);
                log.info("Config updated - fletching mode: {}", currentConfig.getFletchingMode());
            });
            methodButtonPanel.add(methodButton);
        }
        
        // auto-select current method if set
        if (currentConfig.getFletchingMode() != null) {
            Component[] components = methodButtonPanel.getComponents();
            for (int i = 0; i < methods.length && i < components.length; i++) {
                if (methods[i] == currentConfig.getFletchingMode() && components[i] instanceof JButton) {
                    selectMethodButton((JButton) components[i]);
                    updateFletchingConfiguration(methods[i]);
                    break;
                }
            }
        }
    }
    
    private void updateFletchingConfiguration(FletchingMode mode) {
        removeConfigComponents();
        
        switch (mode) {
            case DARTS:
                JComboBox<DartType> dartDropdown = new JComboBox<>(DartType.values());
                dartDropdown.setSelectedItem(currentConfig.getDartType());
                dartDropdown.addActionListener(e -> {
                    currentConfig.setDartType((DartType) dartDropdown.getSelectedItem());
                });
                
                JPanel dartPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                dartPanel.add(dartDropdown);
                configurationPanel.add(dartPanel);
                break;
                
            case BOLTS:
                JComboBox<net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType> fletchingBoltDropdown = 
                    new JComboBox<>(net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType.values());
                fletchingBoltDropdown.setSelectedItem(currentConfig.getFletchingBoltType());
                fletchingBoltDropdown.addActionListener(e -> {
                    currentConfig.setFletchingBoltType((net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.BoltType) fletchingBoltDropdown.getSelectedItem());
                });
                
                JPanel fletchingBoltPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                fletchingBoltPanel.add(fletchingBoltDropdown);
                configurationPanel.add(fletchingBoltPanel);
                break;
                
            case ARROWS:
                JComboBox<ArrowType> arrowDropdown = new JComboBox<>(ArrowType.values());
                arrowDropdown.setSelectedItem(currentConfig.getArrowType());
                arrowDropdown.addActionListener(e -> {
                    currentConfig.setArrowType((ArrowType) arrowDropdown.getSelectedItem());
                });
                
                JPanel arrowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                arrowPanel.add(arrowDropdown);
                configurationPanel.add(arrowPanel);
                break;
                
            case JAVELINS:
                JComboBox<JavelinType> javelinDropdown = new JComboBox<>(JavelinType.values());
                javelinDropdown.setSelectedItem(currentConfig.getJavelinType());
                javelinDropdown.addActionListener(e -> {
                    currentConfig.setJavelinType((JavelinType) javelinDropdown.getSelectedItem());
                });
                
                JPanel javelinPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                javelinPanel.add(javelinDropdown);
                configurationPanel.add(javelinPanel);
                break;
                
            case ARROW_SHAFTS:
                JComboBox<ArrowShaftType> arrowShaftDropdown = new JComboBox<>(ArrowShaftType.values());
                arrowShaftDropdown.setSelectedItem(currentConfig.getArrowShaftType());
                arrowShaftDropdown.addActionListener(e -> {
                    currentConfig.setArrowShaftType((ArrowShaftType) arrowShaftDropdown.getSelectedItem());
                });

                JPanel arrowShaftPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                arrowShaftPanel.add(arrowShaftDropdown);
                configurationPanel.add(arrowShaftPanel);
                break;

            case JAVELIN_SHAFTS:
                JComboBox<JavelinShaftType> javelinShaftDropdown = new JComboBox<>(JavelinShaftType.values());
                javelinShaftDropdown.setSelectedItem(currentConfig.getJavelinShaftType());
                javelinShaftDropdown.addActionListener(e -> {
                    currentConfig.setJavelinShaftType((JavelinShaftType) javelinShaftDropdown.getSelectedItem());
                });

                JPanel javelinShaftPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                javelinShaftPanel.add(javelinShaftDropdown);
                configurationPanel.add(javelinShaftPanel);
                break;

            case BOWS:
                JComboBox<BowType> bowDropdown = new JComboBox<>(BowType.values());
                bowDropdown.setSelectedItem(currentConfig.getBowType());
                bowDropdown.addActionListener(e -> {
                    BowType selected = (BowType) bowDropdown.getSelectedItem();
                    log.info("=== USER SELECTED BOW TYPE: {} ===", selected);
                    currentConfig.setBowType(selected);
                    log.info("Config updated - bow type: {}", currentConfig.getBowType());
                });
                
                JPanel bowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                bowPanel.add(bowDropdown);
                configurationPanel.add(bowPanel);
                break;
                
            case CROSSBOWS:
                JComboBox<CrossbowType> crossbowDropdown = new JComboBox<>(CrossbowType.values());
                crossbowDropdown.setSelectedItem(currentConfig.getCrossbowType());
                crossbowDropdown.addActionListener(e -> {
                    currentConfig.setCrossbowType((CrossbowType) crossbowDropdown.getSelectedItem());
                });
                
                JPanel crossbowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                crossbowPanel.add(crossbowDropdown);
                configurationPanel.add(crossbowPanel);
                break;
                
            case SHIELDS:
                JComboBox<ShieldType> shieldDropdown = new JComboBox<>(ShieldType.values());
                shieldDropdown.setSelectedItem(currentConfig.getShieldType());
                shieldDropdown.addActionListener(e -> {
                    currentConfig.setShieldType((ShieldType) shieldDropdown.getSelectedItem());
                });
                
                JPanel shieldPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                shieldPanel.add(shieldDropdown);
                configurationPanel.add(shieldPanel);
                break;
        }
        
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }
    
    private void createCraftingMethodButtons() {
        CraftingMethod[] methods = CraftingMethod.values();
        setupMethodButtonGrid(methodButtonPanel, methods.length);

        for (CraftingMethod method : methods) {
            JButton methodButton = createMethodButton(method.getDisplayName());
            methodButton.addActionListener(e -> {
                selectMethodButton(methodButton);
                currentConfig.setCraftingMethod(method);
                currentConfig.setCraftingRecipe(CraftingProcessor.getDefaultRecipe(method));
                updateCraftingConfiguration(method);
                log.info("Selected crafting method: {}", method);
            });
            methodButtonPanel.add(methodButton);
        }

        if (currentConfig.getCraftingMethod() != null) {
            Component[] components = methodButtonPanel.getComponents();
            for (int i = 0; i < methods.length && i < components.length; i++) {
                if (methods[i] == currentConfig.getCraftingMethod() && components[i] instanceof JButton) {
                    selectMethodButton((JButton) components[i]);
                    updateCraftingConfiguration(methods[i]);
                    break;
                }
            }
        }
    }

    private void updateCraftingConfiguration(CraftingMethod method) {
        removeConfigComponents();
        String[] recipes = CraftingProcessor.getRecipeNames(method);
        if (recipes.length == 0) {
            return;
        }
        String selected = currentConfig.getCraftingRecipe();
        if (selected == null || !java.util.Arrays.asList(recipes).contains(selected)) {
            selected = recipes[0];
            currentConfig.setCraftingRecipe(selected);
        }

        JComboBox<String> recipeDropdown = new JComboBox<>(new DefaultComboBoxModel<>(recipes));
        recipeDropdown.setSelectedItem(selected);
        recipeDropdown.addActionListener(e -> currentConfig.setCraftingRecipe(
                (String) recipeDropdown.getSelectedItem()));

        JPanel recipePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        recipePanel.add(recipeDropdown);
        configurationPanel.add(recipePanel);
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }

    private void createCookingMethodButtons() {
        CookingMethod[] methods = CookingMethod.values();
        setupMethodButtonGrid(methodButtonPanel, methods.length);

        for (CookingMethod method : methods) {
            JButton methodButton = createMethodButton(method.getDisplayName());
            methodButton.addActionListener(e -> {
                selectMethodButton(methodButton);
                currentConfig.setCookingMethod(method);
                currentConfig.setCookingRecipe(CookingProcessor.getDefaultRecipe(method));
                updateCookingConfiguration(method);
                log.info("Selected cooking method: {}", method);
            });
            methodButtonPanel.add(methodButton);
        }

        if (currentConfig.getCookingMethod() != null) {
            Component[] components = methodButtonPanel.getComponents();
            for (int i = 0; i < methods.length && i < components.length; i++) {
                if (methods[i] == currentConfig.getCookingMethod() && components[i] instanceof JButton) {
                    selectMethodButton((JButton) components[i]);
                    updateCookingConfiguration(methods[i]);
                    break;
                }
            }
        }
    }

    private void updateCookingConfiguration(CookingMethod method) {
        removeConfigComponents();
        String[] recipes = CookingProcessor.getRecipeNames(method);
        if (recipes.length == 0) {
            return;
        }
        String selected = currentConfig.getCookingRecipe();
        if (selected == null || !java.util.Arrays.asList(recipes).contains(selected)) {
            selected = recipes[0];
            currentConfig.setCookingRecipe(selected);
        }

        JComboBox<String> recipeDropdown = new JComboBox<>(new DefaultComboBoxModel<>(recipes));
        recipeDropdown.setSelectedItem(selected);
        recipeDropdown.addActionListener(e -> currentConfig.setCookingRecipe(
                (String) recipeDropdown.getSelectedItem()));

        JPanel recipePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        recipePanel.add(recipeDropdown);
        configurationPanel.add(recipePanel);
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }

    private void removeConfigComponents() {
        configurationPanel.removeAll();
    }
    
    private void showComingSoon(String skillName) {
        methodButtonPanel.removeAll();
        configurationPanel.removeAll();
        
        JLabel comingSoonLabel = new JLabel(skillName + " configuration coming soon");
        comingSoonLabel.setFont(FontManager.getRunescapeFont());
        comingSoonLabel.setHorizontalAlignment(SwingConstants.CENTER);
        methodButtonPanel.add(comingSoonLabel);
        
        methodButtonPanel.revalidate();
        methodButtonPanel.repaint();
        configurationPanel.revalidate();
        configurationPanel.repaint();
    }
    
    private JButton createMethodButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeFont());
        button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return button;
    }
    
    private void setupMethodButtonGrid(JPanel panel, int buttonCount) {
        int columns = 2;
        int rows = (buttonCount + 1) / 2; // rounds up for odd numbers
        panel.setLayout(new GridLayout(rows, columns, 10, 5));
    }
    
    private void selectMethodButton(JButton button) {
        // deselect previous button
        if (selectedMethodButton != null) {
            selectedMethodButton.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        }
        
        // select new button
        selectedMethodButton = button;
        button.setBackground(ColorScheme.BRAND_ORANGE);
    }
    

    private void onStartStopClicked() {
        if (plugin.getScript().isRunning()) {
            // stop the script
            plugin.getScript().shutdown();
            startStopButton.setText("Start");
            startStopButton.setBackground(ColorScheme.BRAND_ORANGE);
            statusLabel.setText("Stopped");
            log.info("Script stopped from panel");
        } else {
            log.info("=== USER CLICKED START BUTTON ===");
            log.info("Selected skill: {}", selectedBankSkill);
            log.info("Current config skill: {}", currentConfig.getSkill());
            log.info("Current config fletching mode: {}", currentConfig.getFletchingMode());
            log.info("Current config bow type: {}", currentConfig.getBowType());
            
            // validate configuration before starting
            if (!currentConfig.isValid()) {
                statusLabel.setText("Invalid configuration");
                log.info("Cannot start: invalid configuration - {}", currentConfig);
                return;
            }
            
            // start the script using plugin helper method
            plugin.runScriptWithConfig(currentConfig);
            startStopButton.setText("Stop");
            startStopButton.setBackground(new Color(255, 55, 40)); // red color
            statusLabel.setText("Running");
            log.info("Script start requested from panel");
        }
    }

    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            
            // update button state based on script status
            if (plugin.getScript().isRunning()) {
                startStopButton.setText("Stop");
                startStopButton.setBackground(new Color(255, 55, 40));
            } else {
                startStopButton.setText("Start");
                startStopButton.setBackground(ColorScheme.BRAND_ORANGE);
            }
        });
    }
    
    public void ensureStoppedState() {
        SwingUtilities.invokeLater(() -> {
            startStopButton.setText("Start");
            startStopButton.setBackground(ColorScheme.BRAND_ORANGE);
            statusLabel.setText("");
        });
    }

    public ConfigData getCurrentConfig() {
        return new ConfigData(currentConfig); // return a copy
    }
}