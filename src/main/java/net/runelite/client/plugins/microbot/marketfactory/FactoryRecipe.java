package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;

/**
 * Declarative processing recipes. Item names are resolved against the wiki mapping at scan
 * time, so there are no hard-coded item ids to drift out of date.
 *
 * <p>This is the Phase 0 starter set: cooking and crafting staples, a few members herblore
 * recipes, tool-based recipes, a fractional input (thread) and container byproducts. The
 * mechanism is what matters; the catalog grows once the model is validated.
 */
public enum FactoryRecipe
{
	CHOCOLATE_DUST("Chocolate dust", Skill.COOKING, 1, false, "Chocolate dust", 1, 2_600,
		Collections.emptyList(),
		RecipeInput.tool("Knife"),
		RecipeInput.consumed("Chocolate bar", 1)),

	PIE_SHELL("Pie shells", Skill.COOKING, 1, false, "Pie shell", 1, 1_650,
		Collections.emptyList(),
		RecipeInput.consumed("Pie dish", 1),
		RecipeInput.consumed("Pastry dough", 1)),

	UNCOOKED_REDBERRY_PIE("Uncooked redberry pies", Skill.COOKING, 10, false, "Uncooked berry pie", 1, 1_650,
		Collections.emptyList(),
		RecipeInput.consumed("Pie shell", 1),
		RecipeInput.consumed("Redberries", 1)),

	UNCOOKED_MEAT_PIE("Uncooked meat pies", Skill.COOKING, 20, false, "Uncooked meat pie", 1, 1_650,
		Collections.emptyList(),
		RecipeInput.consumed("Pie shell", 1),
		RecipeInput.consumed("Cooked meat", 1)),

	UNCOOKED_APPLE_PIE("Uncooked apple pies", Skill.COOKING, 30, false, "Uncooked apple pie", 1, 1_650,
		Collections.emptyList(),
		RecipeInput.consumed("Pie shell", 1),
		RecipeInput.consumed("Cooking apple", 1)),

	CHOCOLATE_CAKE("Chocolate cakes", Skill.COOKING, 50, false, "Chocolate cake", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Cake", 1),
		RecipeInput.consumed("Chocolate bar", 1)),

	MEAT_PIZZA("Meat pizzas", Skill.COOKING, 45, false, "Meat pizza", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Plain pizza", 1),
		RecipeInput.consumed("Cooked meat", 1)),

	ANCHOVY_PIZZA("Anchovy pizzas", Skill.COOKING, 55, false, "Anchovy pizza", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Plain pizza", 1),
		RecipeInput.consumed("Anchovies", 1)),

	PASTRY_DOUGH("Pastry dough", Skill.COOKING, 1, false, "Pastry dough", 1, 1_500,
		Arrays.asList("Pot", "Jug"),
		RecipeInput.consumed("Pot of flour", 1),
		RecipeInput.consumed("Jug of water", 1)),

	BREAD_DOUGH("Bread dough", Skill.COOKING, 1, false, "Bread dough", 1, 1_500,
		Arrays.asList("Pot", "Jug"),
		RecipeInput.consumed("Pot of flour", 1),
		RecipeInput.consumed("Jug of water", 1)),

	PIZZA_BASE("Pizza bases", Skill.COOKING, 35, false, "Pizza base", 1, 1_500,
		Arrays.asList("Pot", "Jug"),
		RecipeInput.consumed("Pot of flour", 1),
		RecipeInput.consumed("Jug of water", 1)),

	SOFT_CLAY("Soft clay", Skill.CRAFTING, 1, false, "Soft clay", 1, 1_800,
		Collections.singletonList("Jug"),
		RecipeInput.consumed("Clay", 1),
		RecipeInput.consumed("Jug of water", 1)),

	SOFT_CLAY_BUCKET("Soft clay (bucket)", Skill.CRAFTING, 1, false, "Soft clay", 1, 1_800,
		Collections.singletonList("Bucket"),
		RecipeInput.consumed("Clay", 1),
		RecipeInput.consumed("Bucket of water", 1)),

	ORANGE_DYE("Orange dye", Skill.CRAFTING, 1, false, "Orange dye", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Red dye", 1),
		RecipeInput.consumed("Yellow dye", 1)),

	GREEN_DYE("Green dye", Skill.CRAFTING, 1, false, "Green dye", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Blue dye", 1),
		RecipeInput.consumed("Yellow dye", 1)),

	PURPLE_DYE("Purple dye", Skill.CRAFTING, 1, false, "Purple dye", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Red dye", 1),
		RecipeInput.consumed("Blue dye", 1)),

	RED_CAPE("Red capes", Skill.CRAFTING, 1, false, "Red cape", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Black cape", 1),
		RecipeInput.consumed("Red dye", 1)),

	GREEN_CAPE("Green capes", Skill.CRAFTING, 1, false, "Green cape", 1, 2_400,
		Collections.emptyList(),
		RecipeInput.consumed("Black cape", 1),
		RecipeInput.consumed("Green dye", 1)),

	STRING_UNBLESSED_SYMBOL("String unblessed symbols", Skill.CRAFTING, 1, false, "Unblessed symbol", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Unstrung symbol", 1),
		RecipeInput.consumed("Ball of wool", 1)),

	STRING_GOLD_AMULET("String gold amulets", Skill.CRAFTING, 1, false, "Gold amulet", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Gold amulet (u)", 1),
		RecipeInput.consumed("Ball of wool", 1)),

	STRING_SAPPHIRE_AMULET("String sapphire amulets", Skill.CRAFTING, 1, true, "Sapphire amulet", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Sapphire amulet (u)", 1),
		RecipeInput.consumed("Ball of wool", 1)),

	STRING_DIAMOND_AMULET("String diamond amulets", Skill.CRAFTING, 1, true, "Diamond amulet", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Diamond amulet (u)", 1),
		RecipeInput.consumed("Ball of wool", 1)),

	CUT_SAPPHIRE("Cut sapphires", Skill.CRAFTING, 20, false, "Sapphire", 1, 2_780,
		Collections.emptyList(),
		RecipeInput.tool("Chisel"),
		RecipeInput.consumed("Uncut sapphire", 1)),

	CUT_EMERALD("Cut emeralds", Skill.CRAFTING, 27, false, "Emerald", 1, 2_780,
		Collections.emptyList(),
		RecipeInput.tool("Chisel"),
		RecipeInput.consumed("Uncut emerald", 1)),

	CUT_RUBY("Cut rubies", Skill.CRAFTING, 34, false, "Ruby", 1, 2_780,
		Collections.emptyList(),
		RecipeInput.tool("Chisel"),
		RecipeInput.consumed("Uncut ruby", 1)),

	CUT_DIAMOND("Cut diamonds", Skill.CRAFTING, 43, false, "Diamond", 1, 2_780,
		Collections.emptyList(),
		RecipeInput.tool("Chisel"),
		RecipeInput.consumed("Uncut diamond", 1)),

	LEATHER_GLOVES("Leather gloves", Skill.CRAFTING, 1, false, "Leather gloves", 1, 1_800,
		Collections.emptyList(),
		RecipeInput.tool("Needle"),
		RecipeInput.consumed("Leather", 1),
		RecipeInput.fractional("Thread", 1, 5)),

	GUAM_POTION_UNF("Guam potion (unf)", Skill.HERBLORE, 3, true, "Guam potion (unf)", 1, 2_500,
		Collections.emptyList(),
		RecipeInput.consumed("Guam leaf", 1),
		RecipeInput.consumed("Vial of water", 1)),

	CLEAN_IRIT("Clean irit", Skill.HERBLORE, 40, true, "Irit leaf", 1, 5_000,
		Collections.emptyList(),
		RecipeInput.consumed("Grimy irit leaf", 1)),

	CLEAN_TORSTOL("Clean torstol", Skill.HERBLORE, 75, true, "Torstol", 1, 5_000,
		Collections.emptyList(),
		RecipeInput.consumed("Grimy torstol", 1));

	private final String displayName;
	private final Skill skill;
	private final int level;
	private final boolean membersOnly;
	private final String outputName;
	private final int outputQuantity;
	private final int estimatedUnitsPerHour;
	private final List<String> byproducts;
	private final List<RecipeInput> inputs;

	FactoryRecipe(String displayName, Skill skill, int level, boolean membersOnly,
		String outputName, int outputQuantity, int estimatedUnitsPerHour,
		List<String> byproducts, RecipeInput... inputs)
	{
		if (inputs == null || inputs.length < 1)
		{
			throw new IllegalArgumentException("A recipe needs at least one input");
		}
		this.displayName = displayName;
		this.skill = skill;
		this.level = level;
		this.membersOnly = membersOnly;
		this.outputName = outputName;
		this.outputQuantity = outputQuantity;
		this.estimatedUnitsPerHour = estimatedUnitsPerHour;
		this.byproducts = Collections.unmodifiableList(new ArrayList<>(byproducts));
		this.inputs = Collections.unmodifiableList(Arrays.asList(inputs));
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Skill getSkill()
	{
		return skill;
	}

	public int getLevel()
	{
		return level;
	}

	public boolean isMembersOnly()
	{
		return membersOnly;
	}

	public String getOutputName()
	{
		return outputName;
	}

	public int getOutputQuantity()
	{
		return outputQuantity;
	}

	public int getEstimatedUnitsPerHour()
	{
		return estimatedUnitsPerHour;
	}

	public List<String> getByproducts()
	{
		return byproducts;
	}

	public List<RecipeInput> getInputs()
	{
		return inputs;
	}

	/** Primary output first, then container byproducts that are also sold. */
	public List<String> getSaleOutputs()
	{
		List<String> outputs = new ArrayList<>();
		outputs.add(outputName);
		outputs.addAll(byproducts);
		return Collections.unmodifiableList(outputs);
	}

	public boolean isEligibleFor(int skillLevel, boolean membersAccount, boolean memberWorld)
	{
		if (membersOnly && (!membersAccount || !memberWorld))
		{
			return false;
		}
		return skillLevel >= level;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
