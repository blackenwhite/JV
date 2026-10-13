package com.nabajyoti.systemdesign.week1.shortandlongpoll;

import java.time.Instant;

public class EC2Instance {
    private final String id;
    private final Instant createdAt;
    private volatile InstanceStatus status;

    public EC2Instance(String id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.status = InstanceStatus.CREATING;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public synchronized void markAsRunning() {
        this.status = InstanceStatus.RUNNING;
        notifyAll();
    }

    public synchronized InstanceStatus waitForCompletion(
            long timeoutMillis
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (status == InstanceStatus.CREATING) {
            long remainingMillis =
                    deadline - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                break;
            }
            wait(remainingMillis);
        }
        return status;
    }
}
