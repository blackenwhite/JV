package com.nabajyoti.systemdesign.week1.shortandlongpoll;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class InstanceStore {
    private final ConcurrentMap<String, EC2Instance> instances = new ConcurrentHashMap<>();

    private final AtomicLong nextId = new AtomicLong(1);

    public EC2Instance create() {
        String id = "instance-" + nextId.getAndIncrement();
        EC2Instance instance = new EC2Instance(id);
        instances.put(id, instance);
        return instance;
    }

    public Optional<EC2Instance> findById(String id) {
        return Optional.ofNullable(instances.get(id));
    }

    public boolean markAsRunning(String id) {
        EC2Instance instance = instances.get(id);
        if (instance == null) {
            return false;
        }
        instance.markAsRunning();
        return true;
    }

}
