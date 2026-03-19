///usr/bin/env jbang "$0" "$@" ; exit $?

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

class UpdateReadme {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: jbang updateReadme.java <newVersion>");
            System.exit(1);
        }

        String newVersion = args[0];
        Path readmePath = Path.of("README.md");
        String content = Files.readString(readmePath, StandardCharsets.UTF_8);

        // Update Maven XML: <version>...</version> inside quarkus-qubit dependency blocks
        content = content.replaceAll(
                "(<artifactId>quarkus-qubit</artifactId>\\s*<version>)[^<]+(</version>)",
                "$1" + newVersion + "$2");

        // Update Gradle: 'io.quarkiverse.qubit:quarkus-qubit:...'
        content = content.replaceAll(
                "(io\\.quarkiverse\\.qubit:quarkus-qubit:)[^'\"\\s]+",
                "$1" + newVersion);

        Files.writeString(readmePath, content, StandardCharsets.UTF_8);
        System.out.println("README.md updated to version " + newVersion);
    }
}
