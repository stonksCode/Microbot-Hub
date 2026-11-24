package net.runelite.client.plugins.microbot.spicecollector;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

import java.awt.Color;

/**
 * The four coloured spices guarded by the Hell-Rat Behemoths in Evil Dave's Basement of Doom.
 * The coordinates are the spice marker locations from the OSRS wiki map data.
 */
@Getter
public enum SpiceType {
    RED("Red spice", new WorldPoint(3078, 9901, 0),
            ItemID.HUNDRED_DAVE_SPICE_RED_4, new Color(220, 50, 50)),
    ORANGE("Orange spice", new WorldPoint(3069, 9895, 0),
            ItemID.HUNDRED_DAVE_SPICE_ORANGE_4, new Color(235, 145, 35)),
    YELLOW("Yellow spice", new WorldPoint(3070, 9881, 0),
            ItemID.HUNDRED_DAVE_SPICE_YELLOW_4, new Color(230, 210, 45)),
    BROWN("Brown spice", new WorldPoint(3081, 9875, 0),
            ItemID.HUNDRED_DAVE_SPICE_BROWN_4, new Color(140, 95, 60));

    private final String itemName;
    private final WorldPoint location;
    private final int fourDoseItemId;
    private final Color color;

    SpiceType(String itemName, WorldPoint location, int fourDoseItemId, Color color) {
        this.itemName = itemName;
        this.location = location;
        this.fourDoseItemId = fourDoseItemId;
        this.color = color;
    }

    @Override
    public String toString() {
        return itemName;
    }
}
