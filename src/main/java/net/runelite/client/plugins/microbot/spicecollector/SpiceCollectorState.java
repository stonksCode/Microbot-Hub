package net.runelite.client.plugins.microbot.spicecollector;

import lombok.Getter;

@Getter
public enum SpiceCollectorState {
    IDLE("Idle"),
    WALKING_TO_CURTAIN("Walking to curtain"),
    ENTERING("Entering alcove"),
    WAITING_FOR_FIGHT("Waiting for behemoth"),
    FIGHTING("Cat fighting"),
    FINISHING("Leaving alcove"),
    STOPPED("Stopped");

    private final String label;

    SpiceCollectorState(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
