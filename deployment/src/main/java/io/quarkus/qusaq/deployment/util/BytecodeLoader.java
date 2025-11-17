package io.quarkus.qusaq.deployment.util;

import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads class bytecode from application archives during build.
 */
public final class BytecodeLoader {

    private static final Logger log = Logger.getLogger(BytecodeLoader.class);

    private BytecodeLoader() {
    }

    /**
     * Loads class bytecode from application archives or classloader.
     */
    public static byte[] loadClassBytecode(String className, ApplicationArchivesBuildItem applicationArchives) {
        String classPath = className.replace('.', '/') + ".class";

        for (var archive : applicationArchives.getAllApplicationArchives()) {
            try {
                for (Path rootPath : archive.getRootDirectories()) {
                    Path classFilePath = rootPath.resolve(classPath);

                    if (Files.exists(classFilePath)) {
                        log.debugf("Found class %s in application archive at %s", className, classFilePath);
                        return Files.readAllBytes(classFilePath);
                    }
                }
            } catch (IOException e) {
                log.debugf("Could not load class %s from archive", className);
            }
        }

        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classPath)) {
            if (is != null) {
                log.debugf("Found class %s in classloader", className);
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.debugf(e, "Could not load class %s from classloader", className);
        }

        log.warnf("Could not find bytecode for class %s", className);
        return new byte[0];
    }
}
