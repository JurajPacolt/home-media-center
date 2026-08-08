package org.javerlabd.homecenter;

import org.javerlabd.homecenter.config.DataDirectory;
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
        // Dátový priečinok musí existovať skôr, než sa otvorí SQLite datasource.
        application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event ->
                DataDirectory.ensureExists(event.getEnvironment()));
        application.run(args);
    }
}
