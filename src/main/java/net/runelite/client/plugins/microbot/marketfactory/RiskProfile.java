package net.runelite.client.plugins.microbot.marketfactory;

import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;

/**
 * Everything the analyzer and allocator need to know about the user's risk appetite.
 * Presets are starting hypotheses: Phase 5 calibrates them against recorded prediction
 * error, so none of these constants should be treated as proven.
 */
public final class RiskProfile
{
	private final RiskLevel level;
	private final double minHourlyVolume;
	private final double minRoiPercent;
	private final double minProfitPerUnit;
	private final double maxVolatilityCv;
	private final double participationCapPercent;
	private final double maxFillHours;
	private final long maxPrintAgeMinutes;
	private final double maxSpreadRatio;
	private final double maxDowntrendPercentPerHour;
	private final boolean requireHistory;
	private final int minHistoryPoints;
	private final double priceBufferPercent;
	private final int repriceSteps;
	private final double stopLossPercent;
	private final double maxCapitalPerItemPercent;
	private final int maxConcurrentPositions;
	private final double cashReservePercent;
	private final long maxHoldCapMinutes;
	private final boolean allowOvernightHold;

	private RiskProfile(Builder builder)
	{
		this.level = builder.level;
		this.minHourlyVolume = builder.minHourlyVolume;
		this.minRoiPercent = builder.minRoiPercent;
		this.minProfitPerUnit = builder.minProfitPerUnit;
		this.maxVolatilityCv = builder.maxVolatilityCv;
		this.participationCapPercent = builder.participationCapPercent;
		this.maxFillHours = builder.maxFillHours;
		this.maxPrintAgeMinutes = builder.maxPrintAgeMinutes;
		this.maxSpreadRatio = builder.maxSpreadRatio;
		this.maxDowntrendPercentPerHour = builder.maxDowntrendPercentPerHour;
		this.requireHistory = builder.requireHistory;
		this.minHistoryPoints = builder.minHistoryPoints;
		this.priceBufferPercent = builder.priceBufferPercent;
		this.repriceSteps = builder.repriceSteps;
		this.stopLossPercent = builder.stopLossPercent;
		this.maxCapitalPerItemPercent = builder.maxCapitalPerItemPercent;
		this.maxConcurrentPositions = builder.maxConcurrentPositions;
		this.cashReservePercent = builder.cashReservePercent;
		this.maxHoldCapMinutes = builder.maxHoldCapMinutes;
		this.allowOvernightHold = builder.allowOvernightHold;
	}

	public static RiskProfile preset(RiskLevel level)
	{
		switch (level)
		{
			case CONSERVATIVE:
				return builder(RiskLevel.CONSERVATIVE)
					.minHourlyVolume(500)
					.minRoiPercent(3.0)
					.minProfitPerUnit(100)
					.maxVolatilityCv(0.08)
					.participationCapPercent(2.0)
					.maxFillHours(0.5)
					.maxPrintAgeMinutes(15)
					.maxSpreadRatio(1.5)
					.maxDowntrendPercentPerHour(0.5)
					.requireHistory(true)
					.minHistoryPoints(48)
					.priceBufferPercent(1.0)
					.repriceSteps(1)
					.stopLossPercent(2.0)
					.maxCapitalPerItemPercent(20.0)
					.maxConcurrentPositions(2)
					.cashReservePercent(25.0)
					.maxHoldCapMinutes(240)
					.allowOvernightHold(false)
					.build();
			case AGGRESSIVE:
				return builder(RiskLevel.AGGRESSIVE)
					.minHourlyVolume(50)
					.minRoiPercent(1.0)
					.minProfitPerUnit(20)
					.maxVolatilityCv(0.30)
					.participationCapPercent(10.0)
					.maxFillHours(6.0)
					.maxPrintAgeMinutes(60)
					.maxSpreadRatio(3.0)
					.maxDowntrendPercentPerHour(3.0)
					.requireHistory(false)
					.minHistoryPoints(12)
					.priceBufferPercent(0.0)
					.repriceSteps(3)
					.stopLossPercent(10.0)
					.maxCapitalPerItemPercent(60.0)
					.maxConcurrentPositions(6)
					.cashReservePercent(5.0)
					.maxHoldCapMinutes(1440)
					.allowOvernightHold(true)
					.build();
			case CUSTOM:
			case BALANCED:
			default:
				return builder(RiskLevel.BALANCED)
					.minHourlyVolume(200)
					.minRoiPercent(2.0)
					.minProfitPerUnit(50)
					.maxVolatilityCv(0.15)
					.participationCapPercent(5.0)
					.maxFillHours(2.0)
					.maxPrintAgeMinutes(30)
					.maxSpreadRatio(2.0)
					.maxDowntrendPercentPerHour(1.5)
					.requireHistory(true)
					.minHistoryPoints(24)
					.priceBufferPercent(0.5)
					.repriceSteps(2)
					.stopLossPercent(5.0)
					.maxCapitalPerItemPercent(35.0)
					.maxConcurrentPositions(4)
					.cashReservePercent(15.0)
					.maxHoldCapMinutes(720)
					.allowOvernightHold(true)
					.build();
		}
	}

	public static Builder builder(RiskLevel level)
	{
		return new Builder(level);
	}

	public RiskLevel getLevel()
	{
		return level;
	}

	public double getMinHourlyVolume()
	{
		return minHourlyVolume;
	}

	public double getMinRoiPercent()
	{
		return minRoiPercent;
	}

	public double getMinProfitPerUnit()
	{
		return minProfitPerUnit;
	}

	public double getMaxVolatilityCv()
	{
		return maxVolatilityCv;
	}

	/** Fraction of hourly volume the plugin is willing to represent, 0..1. */
	public double getParticipationCap()
	{
		return participationCapPercent / 100D;
	}

	public double getParticipationCapPercent()
	{
		return participationCapPercent;
	}

	public double getMaxFillHours()
	{
		return maxFillHours;
	}

	public long getMaxPrintAgeMinutes()
	{
		return maxPrintAgeMinutes;
	}

	public double getMaxSpreadRatio()
	{
		return maxSpreadRatio;
	}

	public double getMaxDowntrendPercentPerHour()
	{
		return maxDowntrendPercentPerHour;
	}

	public boolean isRequireHistory()
	{
		return requireHistory;
	}

	public int getMinHistoryPoints()
	{
		return minHistoryPoints;
	}

	/** Fraction added to the buy reference and subtracted from the sell reference, 0..1. */
	public double getPriceBuffer()
	{
		return priceBufferPercent / 100D;
	}

	public double getPriceBufferPercent()
	{
		return priceBufferPercent;
	}

	public int getRepriceSteps()
	{
		return repriceSteps;
	}

	public double getStopLossPercent()
	{
		return stopLossPercent;
	}

	public double getMaxCapitalPerItemPercent()
	{
		return maxCapitalPerItemPercent;
	}

	public int getMaxConcurrentPositions()
	{
		return maxConcurrentPositions;
	}

	public double getCashReservePercent()
	{
		return cashReservePercent;
	}

	public long getMaxHoldCapMinutes()
	{
		return maxHoldCapMinutes;
	}

	public boolean isAllowOvernightHold()
	{
		return allowOvernightHold;
	}

	public static final class Builder
	{
		private RiskLevel level;
		private double minHourlyVolume = 200;
		private double minRoiPercent = 2.0;
		private double minProfitPerUnit = 50;
		private double maxVolatilityCv = 0.15;
		private double participationCapPercent = 5.0;
		private double maxFillHours = 2.0;
		private long maxPrintAgeMinutes = 30;
		private double maxSpreadRatio = 2.0;
		private double maxDowntrendPercentPerHour = 1.5;
		private boolean requireHistory = true;
		private int minHistoryPoints = 24;
		private double priceBufferPercent = 0.5;
		private int repriceSteps = 2;
		private double stopLossPercent = 5.0;
		private double maxCapitalPerItemPercent = 35.0;
		private int maxConcurrentPositions = 4;
		private double cashReservePercent = 15.0;
		private long maxHoldCapMinutes = 720;
		private boolean allowOvernightHold = true;

		private Builder(RiskLevel level)
		{
			this.level = level == null ? RiskLevel.BALANCED : level;
		}

		public Builder minHourlyVolume(double value)
		{
			this.minHourlyVolume = Math.max(0, value);
			return this;
		}

		public Builder minRoiPercent(double value)
		{
			this.minRoiPercent = Math.max(0, value);
			return this;
		}

		public Builder minProfitPerUnit(double value)
		{
			this.minProfitPerUnit = Math.max(0, value);
			return this;
		}

		public Builder maxVolatilityCv(double value)
		{
			this.maxVolatilityCv = Math.max(0.001, value);
			return this;
		}

		public Builder participationCapPercent(double value)
		{
			this.participationCapPercent = clamp(value, 0.1, 100);
			return this;
		}

		public Builder maxFillHours(double value)
		{
			this.maxFillHours = Math.max(0.05, value);
			return this;
		}

		public Builder maxPrintAgeMinutes(long value)
		{
			this.maxPrintAgeMinutes = Math.max(1, value);
			return this;
		}

		public Builder maxSpreadRatio(double value)
		{
			this.maxSpreadRatio = Math.max(0.1, value);
			return this;
		}

		public Builder maxDowntrendPercentPerHour(double value)
		{
			this.maxDowntrendPercentPerHour = Math.max(0, value);
			return this;
		}

		public Builder requireHistory(boolean value)
		{
			this.requireHistory = value;
			return this;
		}

		public Builder minHistoryPoints(int value)
		{
			this.minHistoryPoints = Math.max(0, value);
			return this;
		}

		public Builder priceBufferPercent(double value)
		{
			this.priceBufferPercent = clamp(value, 0, 25);
			return this;
		}

		public Builder repriceSteps(int value)
		{
			this.repriceSteps = Math.max(0, value);
			return this;
		}

		public Builder stopLossPercent(double value)
		{
			this.stopLossPercent = clamp(value, 0, 100);
			return this;
		}

		public Builder maxCapitalPerItemPercent(double value)
		{
			this.maxCapitalPerItemPercent = clamp(value, 1, 100);
			return this;
		}

		public Builder maxConcurrentPositions(int value)
		{
			this.maxConcurrentPositions = Math.max(1, value);
			return this;
		}

		public Builder cashReservePercent(double value)
		{
			this.cashReservePercent = clamp(value, 0, 100);
			return this;
		}

		public Builder maxHoldCapMinutes(long value)
		{
			this.maxHoldCapMinutes = Math.max(30, value);
			return this;
		}

		public Builder allowOvernightHold(boolean value)
		{
			this.allowOvernightHold = value;
			return this;
		}

		public RiskProfile build()
		{
			return new RiskProfile(this);
		}

		private static double clamp(double value, double min, double max)
		{
			return Math.max(min, Math.min(max, value));
		}
	}
}
