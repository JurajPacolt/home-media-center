package org.javerland.homecenter;

import org.javerland.homecenter.config.DataDirectory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HomeCenterApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(HomeCenterApplication.class);
        // The data directory must exist before the H2 data source is opened.
        application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event ->
                DataDirectory.ensureExists(event.getEnvironment()));
        application.run(args);
    }
}
