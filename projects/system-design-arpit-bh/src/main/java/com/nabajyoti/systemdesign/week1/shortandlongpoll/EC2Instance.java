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

    public void markAsRunning() {
        this.status = InstanceStatus.RUNNING;
    }
}
