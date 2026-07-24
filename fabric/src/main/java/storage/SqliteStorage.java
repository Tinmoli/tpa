package tpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class SqliteStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private SqliteStorage() {}

    public static void initialize(Path db) throws Exception {
        Files.createDirectories(db.getParent());
        try (Connection c = connect(db); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS tpa_storage (id INTEGER PRIMARY KEY CHECK (id=1), version INTEGER NOT NULL, payload TEXT NOT NULL)");
        }
    }

    private static Connection connect(Path db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    public static StorageManager.StorageClass load(Path db) throws Exception {
        initialize(db);
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement("SELECT payload FROM tpa_storage WHERE id=1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return new StorageManager.StorageClass();
            StorageManager.StorageClass value = GSON.fromJson(rs.getString(1), StorageManager.StorageClass.class);
            return value == null ? new StorageManager.StorageClass() : value;
        }
    }

    public static void save(Path db, StorageManager.StorageClass storage) throws Exception {
        initialize(db);
        String payload = GSON.toJson(storage);
        try (Connection c = connect(db);
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tpa_storage(id,version,payload) VALUES(1,?,?) " +
                     "ON CONFLICT(id) DO UPDATE SET version=excluded.version,payload=excluded.payload")) {
            ps.setInt(1, storage.getVersion());
            ps.setString(2, payload);
            ps.executeUpdate();
        }
    }

    public static void convertJsonToSqlite(Path json, Path db) throws Exception {
        StorageManager.StorageClass storage = StorageManager.loadJsonFile(json);
        save(db, storage);
    }

    public static void convertSqliteToJson(Path db, Path json) throws Exception {
        StorageManager.StorageClass storage = load(db);
        StorageManager.writeJsonFile(json, storage);
    }
}
