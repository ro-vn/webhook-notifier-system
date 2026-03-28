package com.rnvo.notifier.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = VirtualThreadConfig.class)
@ActiveProfiles("test")
class VirtualThreadConfigTest {

    @Autowired
    @Qualifier("virtualThreadExecutor")
    Executor executor;

    @Test
    void executorRunsTasksOnVirtualThreads() throws InterruptedException {
        AtomicBoolean isVirtual = new AtomicBoolean(false);
        executor.execute(() -> isVirtual.set(Thread.currentThread().isVirtual()));
        Thread.sleep(100);
        assertThat(isVirtual.get()).isTrue();
    }
}
