package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lookup helpers for {@link FactoryRecipe}. Kept separate from the enum so the catalog can
 * later be extended from configuration or a resource file without touching the enum.
 */
public final class RecipeCatalog
{
	private static final Map<String, FactoryRecipe> BY_NAME = buildIndex();

	private RecipeCatalog()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static List<FactoryRecipe> all()
	{
		return Collections.unmodifiableList(java.util.Arrays.asList(FactoryRecipe.values()));
	}

	public static FactoryRecipe byName(String displayName)
	{
		if (displayName == null)
		{
			return null;
		}
		return BY_NAME.get(displayName.trim().toLowerCase(Locale.ROOT));
	}

	public static List<String> displayNames()
	{
		List<String> names = new ArrayList<>();
		for (FactoryRecipe recipe : FactoryRecipe.values())
		{
			names.add(recipe.getDisplayName());
		}
		return names;
	}

	private static Map<String, FactoryRecipe> buildIndex()
	{
		Map<String, FactoryRecipe> index = new LinkedHashMap<>();
		for (FactoryRecipe recipe : FactoryRecipe.values())
		{
			index.put(recipe.getDisplayName().toLowerCase(Locale.ROOT), recipe);
		}
		return Collections.unmodifiableMap(index);
	}
}
