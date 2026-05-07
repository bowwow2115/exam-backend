package com.psh.exam.exam;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * JSON 시험 임포트 직후 JVM을 종료해 스크립트에서 bootRun 한 번으로 DB만 채울 수 있게 합니다.
 * {@code app.import.shutdown-after-import=true} 일 때만 동작합니다.
 */
@Component
@ConditionalOnProperty(name = "app.import.shutdown-after-import", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
public class ImportShutdownApplicationRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;

    public ImportShutdownApplicationRunner(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(code);
    }
}
