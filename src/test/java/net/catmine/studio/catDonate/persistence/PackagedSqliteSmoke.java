package net.catmine.studio.catDonate.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackagedSqliteSmoke {
    private static final String SQLITE_CLASS = "org/sqlite/JDBC.class";
    private static final String RELOCATED_FOLIA_SCHEDULER =
        "net/catmine/studio/catdonate/libs/catengine/scheduler/FoliaCatScheduler.class";
    private PackagedSqliteSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected the shadow JAR path and SQLite coordinate");
        }
        verifyArtifact(Path.of(args[0]), args[1]);
        verifyNativeDriver();
    }

    private static void verifyArtifact(Path artifact, String sqliteCoordinate) throws Exception {
        try (ZipFile jar = new ZipFile(artifact.toFile())) {
            if (jar.getEntry(SQLITE_CLASS) != null) {
                throw new IllegalStateException("sqlite-jdbc must not be bundled in the fat JAR");
            }
            if (jar.getEntry(RELOCATED_FOLIA_SCHEDULER) == null) {
                throw new IllegalStateException("CatEngine FoliaCatScheduler is missing from the fat JAR");
            }
            ZipEntry pluginYaml = jar.getEntry("plugin.yml");
            if (pluginYaml == null) {
                throw new IllegalStateException("plugin.yml is missing from the fat JAR");
            }
            String yaml = new String(jar.getInputStream(pluginYaml).readAllBytes(), StandardCharsets.UTF_8);
            if (!yaml.contains(sqliteCoordinate)) {
                throw new IllegalStateException("plugin.yml does not declare " + sqliteCoordinate);
            }
        }
    }

    private static void verifyNativeDriver() throws Exception {
        Path database = Files.createTempFile("catdonate-external-sqlite-", ".db");
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE smoke_test (id INTEGER PRIMARY KEY)");
                statement.execute("INSERT INTO smoke_test(id) VALUES (1)");
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM smoke_test")) {
                    if (!rows.next() || rows.getInt(1) != 1) {
                        throw new IllegalStateException("External SQLite smoke query returned an unexpected result");
                    }
                }
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
