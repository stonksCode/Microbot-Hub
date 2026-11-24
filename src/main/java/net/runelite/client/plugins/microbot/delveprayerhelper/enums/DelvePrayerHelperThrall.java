package net.runelite.client.plugins.microbot.delveprayerhelper.enums;

import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;

/**
 * Thrall choices for the config dropdown, mapped to Microbot's {@link Rs2Thrall}.
 * {@link #toString()} is what the dropdown shows.
 */
public enum DelvePrayerHelperThrall {
    GREATER_GHOST("Greater Ghost (Magic)", Rs2Thrall.GREATER_GHOST),
    GREATER_SKELETON("Greater Skeleton (Ranged)", Rs2Thrall.GREATER_SKELETON),
    GREATER_ZOMBIE("Greater Zombie (Melee)", Rs2Thrall.GREATER_ZOMBIE),
    SUPERIOR_GHOST("Superior Ghost (Magic)", Rs2Thrall.SUPERIOR_GHOST),
    SUPERIOR_SKELETON("Superior Skeleton (Ranged)", Rs2Thrall.SUPERIOR_SKELETON),
    SUPERIOR_ZOMBIE("Superior Zombie (Melee)", Rs2Thrall.SUPERIOR_ZOMBIE),
    LESSER_GHOST("Lesser Ghost (Magic)", Rs2Thrall.LESSER_GHOST),
    LESSER_SKELETON("Lesser Skeleton (Ranged)", Rs2Thrall.LESSER_SKELETON),
    LESSER_ZOMBIE("Lesser Zombie (Melee)", Rs2Thrall.LESSER_ZOMBIE);

    private final String displayName;
    private final Rs2Thrall thrall;

    DelvePrayerHelperThrall(String displayName, Rs2Thrall thrall) {
        this.displayName = displayName;
        this.thrall = thrall;
    }

    public Rs2Thrall getThrall() {
        return thrall;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
