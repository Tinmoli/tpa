package tpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteStorage {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private SqliteStorage() {}

    public static void initialize(Path db) throws Exception {
        Files.createDirectories(db.getParent());
        try (Connection c = connect(db); Statement s = c.createStatement()) {
            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tpa_storage ("
                    + "id INTEGER PRIMARY KEY CHECK (id=1), "
                    + "version INTEGER NOT NULL, payload TEXT NOT NULL)");
        }
    }

    private static Connection connect(Path db) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:sqlite:" + db.toAbsolutePath());
    }

    public static StorageManager.StorageClass load(Path db) throws Exception {
        initialize(db);
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payload FROM tpa_storage WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new StorageManager.StorageClass();
            }
            StorageManager.StorageClass value = GSON.fromJson(
                    rs.getString(1), StorageManager.StorageClass.class);
            return value == null
                    ? new StorageManager.StorageClass() : value;
        }
    }

    public static void save(
            Path db, StorageManager.StorageClass storage) throws Exception {
        initialize(db);
        String payload = GSON.toJson(storage);
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tpa_storage(id,version,payload) "
                     + "VALUES(1,?,?) ON CONFLICT(id) DO UPDATE SET "
                     + "version=excluded.version,payload=excluded.payload")) {
            ps.setInt(1, storage.getVersion());
            ps.setString(2, payload);
            ps.executeUpdate();
        }
    }

    /**
     * Imports legacy JSON through a temporary database. The live database is
     * replaced only after the temporary database can be read back. An existing
     * database is copied to a timestamped backup before replacement.
     */
    public static StorageManager.StorageClass importJsonAtomically(
            Path json, Path db) throws Exception {
        StorageManager.StorageClass imported =
                StorageManager.loadJsonFile(json);
        imported.normalize();

        Files.createDirectories(db.getParent());
        Path temporary = db.resolveSibling(
                db.getFileName() + ".importing");
        deleteSqliteFiles(temporary);

        try {
            save(temporary, imported);

            StorageManager.StorageClass verified = load(temporary);
            verified.normalize();
            save(temporary, verified);

            if (Files.exists(db)) {
                Path backup = db.resolveSibling(
                        db.getFileName() + ".backup-"
                                + System.currentTimeMillis());
                Files.copy(db, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Constants.LOGGER.info(
                        "Existing SQLite storage backed up to {}.", backup);
            }

            try {
                Files.move(temporary, db,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, db,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return verified;
        } finally {
            deleteSqliteFiles(temporary);
        }
    }

    private static void deleteSqliteFiles(Path db) throws Exception {
        Files.deleteIfExists(db);
        Files.deleteIfExists(Path.of(db + "-wal"));
        Files.deleteIfExists(Path.of(db + "-shm"));
        Files.deleteIfExists(Path.of(db + "-journal"));
    }
}
