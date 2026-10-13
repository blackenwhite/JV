package com.nabajyoti.systemdesign.week1.shortandlongpoll;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Ec2InstanceServiceTest {
    @Test
    void instanceStartsCreatingAndLaterBecomesRunning()
            throws InterruptedException {
        InstanceStore instanceStore = new InstanceStore();
        Ec2InstanceService service =
                new Ec2InstanceService(instanceStore, 1);
        try {
            EC2Instance instance = service.createInstance();
            assertEquals(
                    InstanceStatus.CREATING,
                    instance.getStatus()
            );
            TimeUnit.MILLISECONDS.sleep(1_500);
            assertEquals(
                    InstanceStatus.RUNNING,
                    instance.getStatus()
            );
        } finally {
            service.shutdown();
        }
    }
}
