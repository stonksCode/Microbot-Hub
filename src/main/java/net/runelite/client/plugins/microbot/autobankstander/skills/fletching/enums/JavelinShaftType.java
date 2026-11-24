package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

/** Javelin shafts are only cut from regular logs. */
public enum JavelinShaftType {
    LOGS("Logs", 3, ItemID.LOGS, 15);

    private final String displayName;
    private final int level;
    private final int logId;
    private final int shaftsPerLog;

    JavelinShaftType(String displayName, int level, int logId, int shaftsPerLog) {
        this.displayName = displayName;
        this.level = level;
        this.logId = logId;
        this.shaftsPerLog = shaftsPerLog;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevelRequired() {
        return level;
    }

    public int getLogId() {
        return logId;
    }

    public int getShaftsPerLog() {
        return shaftsPerLog;
    }

    public int getKnifeId() {
        return ItemID.KNIFE;
    }

    public int getProductId() {
        return ItemID.JAVELIN_SHAFT;
    }

    public String[] getProductNames() {
        return new String[]{"Javelin shaft", "Javelin shafts"};
    }

    @Override
    public String toString() {
        return displayName;
    }
}
