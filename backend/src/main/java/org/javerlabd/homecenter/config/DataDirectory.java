package org.javerlabd.homecenter.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.env.Environment;

/**
 * H2 creates the database file but not its parent directory. This step must run before
 * Spring builds the data source, so it is called from an environment listener.
 */
public final class DataDirectory {

    public static final String PROPERTY = "homecenter.data-dir";
    public static final String DEFAULT = "./data";

    private DataDirectory() {
    }

    public static Path ensureExists(Environment environment) {
        Path directory = Path.of(environment.getProperty(PROPERTY, DEFAULT));
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Nepodarilo sa vytvoriť dátový priečinok " + directory.toAbsolutePath(), ex);
        }
        return directory;
    }
}
