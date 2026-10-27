package net.runelite.client.plugins.microbot.tormenteddemons;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LooterStyle {
    ITEM_LIST("Item List"),
    GE_PRICE_RANGE("Value Range"),
    MIXED("Mixed");

    private final String name;
}
