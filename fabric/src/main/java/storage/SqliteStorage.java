package tpa;

import com.google.gson.Gson;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

/** Relational storage. Runtime edits commit before their in-memory counterparts. */
public final class SqliteStorage {
    private static final Gson GSON = new Gson();
    private static final int SCHEMA_VERSION = 2;
    private SqliteStorage() {}

    private static Connection connect(Path db) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        return c;
    }

    private static int schemaVersion(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA user_version")) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public static synchronized void initialize(Path db) throws Exception {
        Files.createDirectories(db.toAbsolutePath().getParent());
        try (Connection c = connect(db)) {
            int version = schemaVersion(c);
            if (version > SCHEMA_VERSION) throw new SQLException("Unsupported newer TPA database schema: " + version);
            if (version == SCHEMA_VERSION) return;
            StorageManager.StorageClass legacy = new StorageManager.StorageClass();
            boolean hasLegacy;
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='tpa_storage'")) {
                hasLegacy = r.next();
            }
            if (hasLegacy) {
                // Consistent backup includes committed WAL data; made before any schema edit.
                backup(c, db, ".pre-v2-");
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                        "SELECT version,payload FROM tpa_storage WHERE id=1")) {
                    if (r.next()) {
                        if (r.getInt(1) > 1) throw new SQLException("Unsupported legacy data version");
                        legacy = GSON.fromJson(r.getString(2), StorageManager.StorageClass.class);
                        if (legacy == null || legacy.getVersion() > 1) {
                            throw new SQLException("Invalid or newer legacy storage; original database was preserved");
                        }
                    }
                }
            }
            validateMigration(legacy);
            legacy.normalize();
            final StorageManager.StorageClass data = legacy;
            transaction(c, connection -> {
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("CREATE TABLE players (uuid TEXT PRIMARY KEY, default_home TEXT NOT NULL, position INTEGER NOT NULL)");
                    s.executeUpdate("CREATE TABLE homes (owner TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE, name TEXT NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, icon TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(owner,name))");
                    s.executeUpdate("CREATE TABLE warps (name TEXT PRIMARY KEY, world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, icon TEXT NOT NULL, position INTEGER NOT NULL)");
                    s.executeUpdate("CREATE INDEX players_position ON players(position)");
                    s.executeUpdate("CREATE INDEX homes_position ON homes(position)");
                    s.executeUpdate("CREATE INDEX warps_position ON warps(position)");
                }
                insertAll(connection, data);
                verify(data, read(connection));
                try (Statement s = connection.createStatement()) {
                    if (hasLegacy) s.executeUpdate("DROP TABLE tpa_storage");
                    s.execute("PRAGMA user_version=" + SCHEMA_VERSION);
                }
            });
        }
    }

    private static Path backup(Connection c, Path db, String suffix) throws Exception {
        Path backup = db.toAbsolutePath().resolveSibling(db.getFileName() + suffix + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".bak");
        try (Statement s = c.createStatement()) {
            s.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
        }
        Constants.LOGGER.info("TPA database backed up to {}", backup);
        return backup;
    }

    public static StorageManager.StorageClass load(Path db) throws Exception {
        initialize(db);
        try (Connection c = connect(db)) {
            c.setAutoCommit(false);
            StorageManager.StorageClass result = read(c);
            c.commit();
            return result;
        }
    }

    private static StorageManager.StorageClass read(Connection c) throws Exception {
        StorageManager.StorageClass data = new StorageManager.StorageClass();
        java.util.Map<String, Player> players = new java.util.HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM players ORDER BY position")) {
            while (r.next()) {
                Player player = Player.loaded(r.getString("uuid"), r.getString("default_home"));
                data.addLoadedPlayer(player);
                players.put(player.getUUID(), player);
            }
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM homes ORDER BY owner,position")) {
            while (r.next()) {
                Player player = players.get(r.getString("owner"));
                if (player == null) throw new SQLException("Home references a missing player");
                player.addLoadedHome(readLocation(r));
            }
        }
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM warps ORDER BY position")) {
            while (r.next()) data.addLoadedWarp(readLocation(r));
        }
        return data;
    }

    private static NamedLocation readLocation(ResultSet r) throws SQLException {
        return NamedLocation.loaded(r.getString("name"), r.getInt("x"), r.getInt("y"), r.getInt("z"), r.getString("world"), r.getString("icon"));
    }

    private static void verify(StorageManager.StorageClass expected, StorageManager.StorageClass actual) throws SQLException {
        if (!GSON.toJsonTree(expected).equals(GSON.toJsonTree(actual))) {
            throw new SQLException("TPA storage verification failed; migration/import was cancelled");
        }
    }

    private static void validateMigration(StorageManager.StorageClass data) throws SQLException {
        // Do not silently discard malformed entries while upgrading a user's database.
        for (NamedLocation warp : data.getWarps()) {
            if (warp == null || !warp.isStructurallyValid()) throw new SQLException("Invalid legacy warp; original data was preserved");
        }
        for (Player player : data.getPlayers()) {
            if (player == null || player.getUUID() == null || player.getUUID().isBlank())
                throw new SQLException("Invalid legacy player; original data was preserved");
            for (NamedLocation home : player.getHomes()) {
                if (home == null || !home.isStructurallyValid()) throw new SQLException("Invalid legacy home; original data was preserved");
            }
            String defaultHome = player.getDefaultHome();
            if (defaultHome != null && !defaultHome.isEmpty() && player.getHome(defaultHome).isEmpty())
                throw new SQLException("Legacy default home does not exist; original data was preserved");
        }
    }

    // Full replacement is reserved for explicit import/cleanup, never normal player edits.
    public static void save(Path db, StorageManager.StorageClass storage) throws Exception {
        initialize(db);
        try (Connection c = connect(db)) {
            transaction(c, connection -> {
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("DELETE FROM homes");
                    s.executeUpdate("DELETE FROM players");
                    s.executeUpdate("DELETE FROM warps");
                }
                insertAll(connection, storage);
                verify(storage, read(connection));
            });
        }
    }

    private static void insertAll(Connection c, StorageManager.StorageClass storage) throws SQLException {
        int p = 0;
        for (Player player : storage.getPlayers()) {
            execute(c, "INSERT INTO players VALUES(?,?,?)", player.getUUID(), player.getDefaultHome(), p++);
            int h = 0;
            for (NamedLocation home : player.getHomes()) insertLocation(c, player.getUUID(), home, h++);
        }
        int w = 0;
        for (NamedLocation warp : storage.getWarps()) insertLocation(c, null, warp, w++);
    }

    private static void insertLocation(Connection c, String owner, NamedLocation location, int position) throws SQLException {
        if (owner == null) {
            execute(c, "INSERT INTO warps VALUES(?,?,?,?,?,?,?)", location.getName(), location.getWorldString(), location.getX(), location.getY(), location.getZ(), location.getIcon(), position);
        } else {
            execute(c, "INSERT INTO homes VALUES(?,?,?,?,?,?,?,?)", owner, location.getName(), location.getWorldString(), location.getX(), location.getY(), location.getZ(), location.getIcon(), position);
        }
    }

    public static void addLocation(Path db, Player owner, NamedLocation location) throws Exception {
        edit(db, c -> {
            if (owner != null) execute(c, "INSERT INTO players(uuid,default_home,position) SELECT ?,?,COALESCE(MAX(position),-1)+1 FROM players WHERE true ON CONFLICT(uuid) DO NOTHING", owner.getUUID(), owner.getDefaultHome());
            String table = owner == null ? "warps" : "homes";
            int position;
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COALESCE(MAX(position),-1)+1 FROM " + table)) {
                r.next(); position = r.getInt(1);
            }
            insertLocation(c, owner == null ? null : owner.getUUID(), location, position);
        });
    }

    public static void deleteLocation(Path db, String owner, String name) throws Exception {
        edit(db, c -> {
            if (owner == null) requireOne(execute(c, "DELETE FROM warps WHERE name=?", name));
            else {
                requireOne(execute(c, "DELETE FROM homes WHERE owner=? AND name=?", owner, name));
                execute(c, "UPDATE players SET default_home='' WHERE uuid=? AND default_home=?", owner, name);
            }
        });
    }

    public static void renameLocation(Path db, String owner, String oldName, String newName) throws Exception {
        edit(db, c -> {
            if (owner == null) requireOne(execute(c, "UPDATE warps SET name=? WHERE name=?", newName, oldName));
            else {
                requireOne(execute(c, "UPDATE homes SET name=? WHERE owner=? AND name=?", newName, owner, oldName));
                execute(c, "UPDATE players SET default_home=? WHERE uuid=? AND default_home=?", newName, owner, oldName);
            }
        });
    }

    public static void setIcon(Path db, String owner, String name, String icon) throws Exception {
        edit(db, c -> requireOne(owner == null
                ? execute(c, "UPDATE warps SET icon=? WHERE name=?", icon, name)
                : execute(c, "UPDATE homes SET icon=? WHERE owner=? AND name=?", icon, owner, name)));
    }

    public static void setDefaultHome(Path db, String owner, String name) throws Exception {
        edit(db, c -> requireOne(execute(c, "UPDATE players SET default_home=? WHERE uuid=?", name, owner)));
    }

    private static void requireOne(int rows) throws SQLException {
        if (rows != 1) throw new SQLException("The location/player no longer exists; reopen the menu");
    }

    private static int execute(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            return s.executeUpdate();
        }
    }

    @FunctionalInterface private interface Change { void run(Connection c) throws Exception; }
    private static void transaction(Connection c, Change change) throws Exception {
        c.setAutoCommit(false);
        try { change.run(c); c.commit(); }
        catch (Exception e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(true); }
    }
    private static void edit(Path db, Change change) throws Exception {
        try (Connection c = connect(db)) { transaction(c, change); }
    }

    public static StorageManager.StorageClass importJsonAtomically(Path json, Path db) throws Exception {
        StorageManager.StorageClass imported = StorageManager.loadJsonFile(json);
        validateMigration(imported);
        imported.normalize();
        Files.createDirectories(db.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(db.toAbsolutePath().getParent(), "tpa-import-", ".db");
        try {
            save(temporary, imported);
            StorageManager.StorageClass verified = load(temporary);
            verify(imported, verified);
            if (Files.exists(db)) {
                try (Connection c = connect(db)) { backup(c, db, ".backup-"); }
                // Apply transactionally; no rename/WAL sidecar race with the live database.
                save(db, verified);
            } else {
                // A failed first import must not leave an empty live database that suppresses retry.
                try { Files.move(temporary, db, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, db); }
            }
            return verified;
        } finally { Files.deleteIfExists(temporary); }
    }
}
