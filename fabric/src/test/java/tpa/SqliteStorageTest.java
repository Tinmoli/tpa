package tpa;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class SqliteStorageTest {
    @TempDir Path directory;
    private static final String LEGACY = """
        {"version":1,"Players":[
          {"UUID":"player-一","DefaultHome":"家","Homes":[
            {"name":"家","x":-123,"y":91,"z":400,"world":"minecraft:overworld","icon":"minecraft:diamond"},
            {"name":"矿洞","x":1,"y":-50,"z":3,"world":"custom:world"}]},
          {"UUID":"player-two","DefaultHome":"","Homes":[]}],
          "Warps":[{"name":"公共点","x":5,"y":67,"z":-9,"world":"minecraft:the_nether","icon":"minecraft:emerald"}]}
        """;

    private Connection connection(Path db) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }
    private Path legacy(String json) throws Exception {
        Path db = directory.resolve("storage.db");
        try (Connection c = connection(db); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE tpa_storage(id INTEGER PRIMARY KEY,version INTEGER,payload TEXT)");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO tpa_storage VALUES(1,1,?)")) {
                p.setString(1, json); p.executeUpdate();
            }
        }
        return db;
    }
    private long scalar(Path db, String sql) throws Exception {
        try (Connection c = connection(db); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next()); return r.getLong(1);
        }
    }
    private void use(Path db) throws Exception {
        StorageManager.SQLITE_FILE = db;
        StorageManager.STORAGE = SqliteStorage.load(db);
    }

    @Test void migratesAllFieldsAndOrderingAndDoesNotRepeat() throws Exception {
        Path db = legacy(LEGACY);
        var expected = new Gson().fromJson(LEGACY, StorageManager.StorageClass.class);
        expected.normalize();
        var actual = SqliteStorage.load(db);
        assertEquals(new Gson().toJsonTree(expected), new Gson().toJsonTree(actual));
        assertEquals(2, scalar(db, "PRAGMA user_version"));
        Path backup;
        try (var paths = Files.list(directory)) {
            var backups = paths.filter(p -> p.toString().endsWith(".bak")).toList();
            assertEquals(1, backups.size()); backup = backups.getFirst();
        }
        assertEquals(1, scalar(backup, "SELECT COUNT(*) FROM tpa_storage"));
        SqliteStorage.load(db);
        try (var paths = Files.list(directory)) { assertEquals(1, paths.filter(p -> p.toString().endsWith(".bak")).count()); }
    }

    @Test void editsAreIncrementalAndDefaultRenameDeleteAreAtomic() throws Exception {
        Path db = legacy(LEGACY); use(db);
        // Any accidental whole-database rewrite must fail this test.
        try (Connection c = connection(db); Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER protect_warps BEFORE DELETE ON warps BEGIN SELECT RAISE(ABORT,'unrelated warp rewrite'); END");
            s.execute("CREATE TRIGGER protect_other_home BEFORE UPDATE ON homes WHEN OLD.name='矿洞' BEGIN SELECT RAISE(ABORT,'unrelated home update'); END");
        }
        Player player = StorageManager.STORAGE.getPlayer("player-一").orElseThrow();
        NamedLocation home = player.getHome("家").orElseThrow();
        home.setIcon("minecraft:apple");
        home.setName("新家");
        assertEquals("新家", player.getDefaultHome());
        var reloaded = SqliteStorage.load(db).getPlayer(player.getUUID()).orElseThrow();
        assertEquals("新家", reloaded.getDefaultHome());
        assertEquals("minecraft:apple", reloaded.getHome("新家").orElseThrow().getIcon());
        player.deleteHome(home);
        assertEquals("", player.getDefaultHome());
        assertEquals("", SqliteStorage.load(db).getPlayer(player.getUUID()).orElseThrow().getDefaultHome());
    }

    @Test void duplicateRenameRollsBackDatabaseAndMemory() throws Exception {
        Path db = legacy(LEGACY); use(db);
        Player player = StorageManager.STORAGE.getPlayer("player-一").orElseThrow();
        NamedLocation home = player.getHome("家").orElseThrow();
        assertThrows(SQLException.class, () -> home.setName("矿洞"));
        assertEquals("家", home.getName());
        assertEquals("家", player.getDefaultHome());
        assertEquals("家", SqliteStorage.load(db).getPlayer(player.getUUID()).orElseThrow().getDefaultHome());
    }

    @Test void failedMigrationKeepsLegacyTableAndPayload() throws Exception {
        Path db = legacy(LEGACY.replace("\"name\":\"矿洞\"", "\"name\":\"家\""));
        assertThrows(SQLException.class, () -> SqliteStorage.load(db));
        assertEquals(0, scalar(db, "PRAGMA user_version"));
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM tpa_storage"));
        assertEquals(0, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='homes'"));
    }

    @Test void malformedLegacyIsNeverReplacedWithEmptyStorage() throws Exception {
        Path db = legacy("{broken");
        assertThrows(Exception.class, () -> SqliteStorage.load(db));
        assertEquals(1, scalar(db, "SELECT COUNT(*) FROM tpa_storage"));
        assertEquals(0, scalar(db, "PRAGMA user_version"));
    }

    @Test void importsUtf8VersionZeroJsonAndKeepsSourceAndBackup() throws Exception {
        Path db = legacy(LEGACY); use(db);
        Path json = directory.resolve("storage.json");
        String oldJson = LEGACY.replace("\"version\":1", "\"version\":0").replace("\"UUID\"", "\"Player_UUID\"");
        Files.writeString(json, oldJson);
        var imported = SqliteStorage.importJsonAtomically(json, db);
        assertEquals("家", imported.getPlayer("player-一").orElseThrow().getDefaultHome());
        assertEquals(oldJson, Files.readString(json));
        assertEquals(2, scalar(db, "SELECT COUNT(*) FROM homes"));
        try (var paths = Files.list(directory)) { assertTrue(paths.anyMatch(p -> p.getFileName().toString().contains(".backup-"))); }
    }

    @Test void newPlayersAndWarpsPersistAndStaleMenusCannotWrite() throws Exception {
        Path db = directory.resolve("new.db"); use(db);
        Player player = StorageManager.STORAGE.addPlayer("new-player");
        NamedLocation home = NamedLocation.loaded("home", 1, 80, 2, "minecraft:overworld", "");
        assertFalse(player.addHome(home));
        player.setDefaultHome("home");
        assertFalse(StorageManager.STORAGE.addWarp(NamedLocation.loaded("spawn", 0, 70, 0, "minecraft:overworld", "")));
        use(db);
        assertEquals("home", StorageManager.STORAGE.getPlayer("new-player").orElseThrow().getDefaultHome());
        assertThrows(IllegalStateException.class, () -> home.setIcon("minecraft:stone"));
        assertThrows(IllegalStateException.class, () -> player.setDefaultHome("home"));
    }

    @Test void refusesNewerSchemaWithoutChangingIt() throws Exception {
        Path db = directory.resolve("future.db");
        try (Connection c = connection(db); Statement s = c.createStatement()) { s.execute("PRAGMA user_version=99"); }
        assertThrows(SQLException.class, () -> SqliteStorage.load(db));
        assertEquals(99, scalar(db, "PRAGMA user_version"));
    }

    @Test void failureBetweenHomeAndDefaultUpdateRollsBackBoth() throws Exception {
        Path db = legacy(LEGACY); use(db);
        try (Connection c = connection(db); Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER fail_default BEFORE UPDATE ON players BEGIN SELECT RAISE(ABORT,'simulated disk write failure'); END");
        }
        Player player = StorageManager.STORAGE.getPlayer("player-一").orElseThrow();
        NamedLocation home = player.getHome("家").orElseThrow();
        assertThrows(SQLException.class, () -> home.setName("renamed"));
        assertEquals("家", home.getName());
        assertTrue(SqliteStorage.load(db).getPlayer(player.getUUID()).orElseThrow().getHome("家").isPresent());
    }

    @Test void failedJsonImportDoesNotOverwriteExistingData() throws Exception {
        Path db = legacy(LEGACY); use(db);
        var before = new Gson().toJsonTree(SqliteStorage.load(db));
        Path json = directory.resolve("storage.json");
        Files.writeString(json, LEGACY.replace("\"name\":\"矿洞\"", "\"name\":\"家\""));
        assertThrows(SQLException.class, () -> SqliteStorage.importJsonAtomically(json, db));
        assertEquals(before, new Gson().toJsonTree(SqliteStorage.load(db)));
    }

    @Test void nullOptionalCollectionsAndIconsMigrateWithoutLoss() throws Exception {
        Path db = legacy("""
            {"Players":[{"UUID":"old","Homes":null,"DefaultHome":null}],"Warps":null}
            """);
        var data = SqliteStorage.load(db);
        assertEquals(1, data.getPlayers().size());
        assertEquals("", data.getPlayers().getFirst().getDefaultHome());
        assertTrue(data.getPlayers().getFirst().getHomes().isEmpty());
    }

    @Test void migrationBackupIncludesCommittedWalRows() throws Exception {
        Path db = legacy(LEGACY);
        try (Connection live = connection(db); Statement s = live.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.executeUpdate("UPDATE tpa_storage SET payload=replace(payload,'公共点','WAL点')");
            assertTrue(SqliteStorage.load(db).getWarp("WAL点").isPresent());
            Path backup;
            try (var paths = Files.list(directory)) { backup = paths.filter(p -> p.toString().endsWith(".bak")).findFirst().orElseThrow(); }
            try (Connection c = connection(backup); Statement query = c.createStatement(); ResultSet r = query.executeQuery("SELECT payload FROM tpa_storage")) {
                assertTrue(r.next()); assertTrue(r.getString(1).contains("WAL点"));
            }
        }
    }
}
