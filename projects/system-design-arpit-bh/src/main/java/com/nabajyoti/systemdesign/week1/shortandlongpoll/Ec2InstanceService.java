package com.nabajyoti.systemdesign.week1.shortandlongpoll;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Ec2InstanceService {
    private final InstanceStore instanceStore;
    private final ExecutorService creationExecutor;
    private final long creationDelaySeconds;

    public Ec2InstanceService(
            InstanceStore instanceStore,
            long creationDelaySeconds
    ) {
        this.instanceStore = instanceStore;
        this.creationDelaySeconds = creationDelaySeconds;
        this.creationExecutor = Executors.newCachedThreadPool();
    }

    public EC2Instance createInstance() {
        EC2Instance instance = instanceStore.create();
        creationExecutor.submit(() -> simulateCreation(instance.getId()));
        return instance;
    }

    private void simulateCreation(String instanceId) {
        try {
            System.out.println("Starting creation of " + instanceId);
            TimeUnit.SECONDS.sleep(creationDelaySeconds);
            instanceStore.markAsRunning(instanceId);
            System.out.println(instanceId + " is now RUNNING");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println(
                    "Creation interrupted for " + instanceId
            );
        }
    }

    public void shutdown() {
        creationExecutor.shutdown();
    }


}
