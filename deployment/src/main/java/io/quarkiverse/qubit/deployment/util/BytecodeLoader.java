package io.quarkiverse.qubit.deployment.util;

import io.quarkiverse.qubit.deployment.analysis.AnalysisException;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads class bytecode from application archives (user classes) or classloader (library classes). */
public final class BytecodeLoader {

    private BytecodeLoader() {
    }

    /** Loads class bytecode from application archives or classloader; throws AnalysisException if not found. */
    public static byte[] loadClassBytecode(String className, ApplicationArchivesBuildItem applicationArchives) {
        String classPath = className.replace('.', '/') + ".class";
        List<String> searchedLocations = new ArrayList<>();
        List<String> ioErrors = new ArrayList<>();

        // Search in application archives first (user-compiled classes)
        for (var archive : applicationArchives.getAllApplicationArchives()) {
            try {
                for (Path rootPath : archive.getRootDirectories()) {
                    Path classFilePath = rootPath.resolve(classPath);
                    searchedLocations.add(classFilePath.toString());

                    if (Files.exists(classFilePath)) {
                        Log.debugf("Found class %s in application archive at %s", className, classFilePath);
                        return Files.readAllBytes(classFilePath);
                    }
                }
            } catch (IOException e) {
                ioErrors.add(String.format("Archive read error for %s: %s", className, e.getMessage()));
                Log.debugf(e, "IO error reading class %s from archive", className);
            }
        }

        // Fall back to classloader (library/dependency classes)
        searchedLocations.add("classloader:" + classPath);
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classPath)) {
            if (is != null) {
                Log.debugf("Found class %s in classloader", className);
                return is.readAllBytes();
            }
        } catch (IOException e) {
            ioErrors.add(String.format("Classloader read error for %s: %s", className, e.getMessage()));
            Log.debugf(e, "IO error loading class %s from classloader", className);
        }

        // Build detailed error message for debugging
        // Issue #18 Fix: Use stream().limit() instead of subList() to avoid holding
        // a view reference to the parent list, which could cause memory issues
        String locationsSummary = searchedLocations.size() > 5
                ? searchedLocations.stream().limit(5).toList() + "... (" + searchedLocations.size() + " total)"
                : searchedLocations.toString();
        String errorsSummary = ioErrors.isEmpty() ? "" : ". IO errors: " + ioErrors;

        String message = String.format(
                "Could not find bytecode for class %s. Searched locations: %s%s",
                className, locationsSummary, errorsSummary);

        Log.warnf(message);
        throw AnalysisException.bytecodeNotFound(className);
    }
}
