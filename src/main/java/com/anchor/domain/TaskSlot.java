package com.anchor.domain;

/** Where a task sits in the day's plan. */
public enum TaskSlot {
    ONE_THING("The One Thing"),
    NICE_TO_HAVE("Nice to have"),
    OTHER("Other");

    private final String label;

    TaskSlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
