package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

import net.runelite.api.gameval.ItemID;

/** Log types that can be cut into arrow shafts with a knife. */
public enum ArrowShaftType {
    LOGS("Logs", 1, ItemID.LOGS, 15),
    OAK_LOGS("Oak logs", 15, ItemID.OAK_LOGS, 30),
    WILLOW_LOGS("Willow logs", 30, ItemID.WILLOW_LOGS, 45),
    MAPLE_LOGS("Maple logs", 45, ItemID.MAPLE_LOGS, 60),
    YEW_LOGS("Yew logs", 60, ItemID.YEW_LOGS, 75),
    MAGIC_LOGS("Magic logs", 75, ItemID.MAGIC_LOGS, 90),
    REDWOOD_LOGS("Redwood logs", 90, ItemID.REDWOOD_LOGS, 105);

    private final String displayName;
    private final int level;
    private final int logId;
    private final int shaftsPerLog;

    ArrowShaftType(String displayName, int level, int logId, int shaftsPerLog) {
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
        return ItemID.ARROW_SHAFT;
    }

    /** Both the item name and the plural dialogue wording have appeared in game. */
    public String[] getProductNames() {
        return new String[]{"Arrow shaft", "Arrow shafts"};
    }

    @Override
    public String toString() {
        return displayName;
    }
}
