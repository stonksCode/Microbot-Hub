package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of one market scan. Immutable so the overlay and export thread can read it while
 * the script starts the next scan.
 */
public final class ScanReport
{
	private final long generatedAt;
	private final boolean membersAccount;
	private final boolean memberWorld;
	private final List<Candidate> flips;
	private final List<Candidate> processes;
	private final int itemsEvaluated;
	private final int itemsDeepScanned;
	private final int recipesEvaluated;
	private final String note;

	public ScanReport(long generatedAt, boolean membersAccount, boolean memberWorld,
		List<Candidate> flips, List<Candidate> processes, int itemsEvaluated,
		int itemsDeepScanned, int recipesEvaluated, String note)
	{
		this.generatedAt = generatedAt;
		this.membersAccount = membersAccount;
		this.memberWorld = memberWorld;
		this.flips = Collections.unmodifiableList(new ArrayList<>(flips));
		this.processes = Collections.unmodifiableList(new ArrayList<>(processes));
		this.itemsEvaluated = itemsEvaluated;
		this.itemsDeepScanned = itemsDeepScanned;
		this.recipesEvaluated = recipesEvaluated;
		this.note = note == null ? "" : note;
	}

	public static ScanReport empty()
	{
		return new ScanReport(0L, false, false, Collections.emptyList(), Collections.emptyList(), 0, 0, 0, "");
	}

	public long getGeneratedAt()
	{
		return generatedAt;
	}

	public boolean isMembersAccount()
	{
		return membersAccount;
	}

	public boolean isMemberWorld()
	{
		return memberWorld;
	}

	public List<Candidate> getFlips()
	{
		return flips;
	}

	public List<Candidate> getProcesses()
	{
		return processes;
	}

	public int getItemsEvaluated()
	{
		return itemsEvaluated;
	}

	public int getItemsDeepScanned()
	{
		return itemsDeepScanned;
	}

	public int getRecipesEvaluated()
	{
		return recipesEvaluated;
	}

	public String getNote()
	{
		return note;
	}

	/** All viable candidates, best score first, ignoring route type. */
	public List<Candidate> getCombined()
	{
		List<Candidate> combined = new ArrayList<>(flips.size() + processes.size());
		combined.addAll(flips);
		combined.addAll(processes);
		combined.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
		return combined;
	}
}
