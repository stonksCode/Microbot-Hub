package net.runelite.client.plugins.microbot.marketfactory;

import java.util.Objects;

/**
 * One ingredient of a recipe, expressed per unit of primary output.
 *
 * <p>Three kinds exist in practice:
 * <ul>
 *   <li>{@code CONSUMED} - consumed one-for-one with the output;</li>
 *   <li>{@code TOOL} - required in the inventory but never consumed (knife, chisel, needle);</li>
 *   <li>{@code FRACTIONAL} - consumed occasionally, such as one thread per five leather items.</li>
 * </ul>
 */
public final class RecipeInput
{
	public enum Kind
	{
		CONSUMED,
		TOOL,
		FRACTIONAL
	}

	private final String itemName;
	private final Kind kind;
	private final int numerator;
	private final int denominator;

	private RecipeInput(String itemName, Kind kind, int numerator, int denominator)
	{
		this.itemName = Objects.requireNonNull(itemName, "itemName");
		this.kind = kind;
		this.numerator = numerator;
		this.denominator = denominator;
	}

	public static RecipeInput consumed(String itemName, int quantityPerOutput)
	{
		if (quantityPerOutput <= 0)
		{
			throw new IllegalArgumentException("Consumed quantity must be positive");
		}
		return new RecipeInput(itemName, Kind.CONSUMED, quantityPerOutput, 1);
	}

	public static RecipeInput tool(String itemName)
	{
		return new RecipeInput(itemName, Kind.TOOL, 0, 1);
	}

	/** For inputs such as thread: {@code fractional("Thread", 1, 5)} is one per five outputs. */
	public static RecipeInput fractional(String itemName, int inputUnits, int outputUnits)
	{
		if (inputUnits <= 0 || outputUnits <= 0)
		{
			throw new IllegalArgumentException("Fractional quantities must be positive");
		}
		return new RecipeInput(itemName, Kind.FRACTIONAL, inputUnits, outputUnits);
	}

	public String getItemName()
	{
		return itemName;
	}

	public Kind getKind()
	{
		return kind;
	}

	public boolean isConsumed()
	{
		return kind != Kind.TOOL;
	}

	public boolean isTool()
	{
		return kind == Kind.TOOL;
	}

	/** Average units of this input consumed per unit of primary output. */
	public double unitsPerOutput()
	{
		if (kind == Kind.TOOL)
		{
			return 0D;
		}
		return numerator / (double) denominator;
	}

	@Override
	public String toString()
	{
		if (kind == Kind.TOOL)
		{
			return itemName + " (tool)";
		}
		if (kind == Kind.FRACTIONAL)
		{
			return itemName + " " + numerator + '/' + denominator;
		}
		return itemName + " x" + numerator;
	}
}
