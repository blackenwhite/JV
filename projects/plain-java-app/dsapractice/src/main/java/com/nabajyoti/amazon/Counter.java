package com.nabajyoti.amazon;

public class Counter {
    private long currentIntervalId;
    private long count;

    public long getCurrentIntervalId() {
        return currentIntervalId;
    }

    public void setCurrentIntervalId(long currentIntervalId) {
        this.currentIntervalId = currentIntervalId;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
