package tpa.storage;

import com.google.gson.Gson;

import tpa.Constants;

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
import java.util.UUID;

/** Relational storage. Runtime edits commit before their in-memory counterparts. */
public final class SqliteStorage {
    private static final Gson GSON = new Gson();
    private static final int SCHEMA_VERSION = 2;

    private SqliteStorage() {}

    private static Connection connect(Path databasePath) throws SQLException {
        Connection connection =
                DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    public static synchronized void initialize(Path databasePath) throws Exception {
        Files.createDirectories(databasePath.toAbsolutePath().getParent());
        try (Connection connection = connect(databasePath)) {
            int version = schemaVersion(connection);
            if (version > SCHEMA_VERSION)
                throw new SQLException("Unsupported newer TPA database schema: " + version);
            if (version == SCHEMA_VERSION) return;
            StorageData legacy = new StorageData();
            boolean hasLegacy;
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet =
                            statement.executeQuery(
                                    "SELECT 1 FROM sqlite_master WHERE type='table' AND"
                                            + " name='tpa_storage'")) {
                hasLegacy = resultSet.next();
            }
            if (hasLegacy) {
                // Consistent backup includes committed WAL data; made before any schema edit.
                backup(connection, databasePath, ".pre-v2-");
                try (Statement statement = connection.createStatement();
                        ResultSet resultSet =
                                statement.executeQuery(
                                        "SELECT version,payload FROM tpa_storage WHERE id=1")) {
                    if (resultSet.next()) {
                        if (resultSet.getInt(1) > 1)
                            throw new SQLException("Unsupported legacy data version");
                        legacy = GSON.fromJson(resultSet.getString(2), StorageData.class);
                        if (legacy == null || legacy.getVersion() > 1) {
                            throw new SQLException(
                                    "Invalid or newer legacy storage; original database was"
                                            + " preserved");
                        }
                    }
                }
            }
            validateMigration(legacy);
            legacy.normalize();
            final StorageData data = legacy;
            transaction(
                    connection,
                    transactionConnection -> {
                        try (Statement statement = transactionConnection.createStatement()) {
                            statement.executeUpdate(
                                    "CREATE TABLE players (uuid TEXT PRIMARY KEY, default_home TEXT"
                                            + " NOT NULL, position INTEGER NOT NULL)");
                            statement.executeUpdate(
                                    "CREATE TABLE homes (owner TEXT NOT NULL REFERENCES"
                                        + " players(uuid) ON DELETE CASCADE, name TEXT NOT NULL,"
                                        + " world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT"
                                        + " NULL, z INTEGER NOT NULL, icon TEXT NOT NULL, position"
                                        + " INTEGER NOT NULL, PRIMARY KEY(owner,name))");
                            statement.executeUpdate(
                                    "CREATE TABLE warps (name TEXT PRIMARY KEY, world TEXT NOT"
                                        + " NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER"
                                        + " NOT NULL, icon TEXT NOT NULL, position INTEGER NOT"
                                        + " NULL)");
                            statement.executeUpdate(
                                    "CREATE INDEX players_position ON players(position)");
                            statement.executeUpdate(
                                    "CREATE INDEX homes_position ON homes(position)");
                            statement.executeUpdate(
                                    "CREATE INDEX warps_position ON warps(position)");
                        }
                        insertAll(transactionConnection, data);
                        verify(data, read(transactionConnection));
                        try (Statement statement = transactionConnection.createStatement()) {
                            if (hasLegacy) statement.executeUpdate("DROP TABLE tpa_storage");
                            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
                        }
                    });
        }
    }

    private static Path backup(Connection connection, Path databasePath, String suffix)
            throws Exception {
        Path backup =
                databasePath
                        .toAbsolutePath()
                        .resolveSibling(
                                databasePath.getFileName()
                                        + suffix
                                        + System.currentTimeMillis()
                                        + "-"
                                        + UUID.randomUUID()
                                        + ".bak");
        try (Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
        }
        Constants.LOGGER.info("TPA database backed up to {}", backup);
        return backup;
    }

    public static StorageData load(Path databasePath) throws Exception {
        initialize(databasePath);
        try (Connection connection = connect(databasePath)) {
            connection.setAutoCommit(false);
            StorageData result = read(connection);
            connection.commit();
            return result;
        }
    }

    private static StorageData read(Connection connection) throws Exception {
        StorageData data = new StorageData();
        java.util.Map<String, PlayerData> players = new java.util.HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT * FROM players ORDER BY position")) {
            while (resultSet.next()) {
                PlayerData player =
                        PlayerData.loaded(
                                resultSet.getString("uuid"), resultSet.getString("default_home"));
                data.addLoadedPlayer(player);
                players.put(player.getUUID(), player);
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT * FROM homes ORDER BY owner,position")) {
            while (resultSet.next()) {
                PlayerData player = players.get(resultSet.getString("owner"));
                if (player == null) throw new SQLException("Home references a missing player");
                player.addLoadedHome(readLocation(resultSet));
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT * FROM warps ORDER BY position")) {
            while (resultSet.next()) data.addLoadedWarp(readLocation(resultSet));
        }
        return data;
    }

    private static NamedLocation readLocation(ResultSet resultSet) throws SQLException {
        return NamedLocation.loaded(
                resultSet.getString("name"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                resultSet.getString("world"),
                resultSet.getString("icon"));
    }

    private static void verify(StorageData expected, StorageData actual) throws SQLException {
        if (!GSON.toJsonTree(expected).equals(GSON.toJsonTree(actual))) {
            throw new SQLException(
                    "TPA storage verification failed; migration/import was cancelled");
        }
    }

    private static void validateMigration(StorageData data) throws SQLException {
        // Do not silently discard malformed entries while upgrading a user's database.
        for (NamedLocation warp : data.getWarps()) {
            if (warp == null || !warp.isStructurallyValid())
                throw new SQLException("Invalid legacy warp; original data was preserved");
        }
        for (PlayerData player : data.getPlayers()) {
            if (player == null || player.getUUID() == null || player.getUUID().isBlank())
                throw new SQLException("Invalid legacy player; original data was preserved");
            for (NamedLocation home : player.getHomes()) {
                if (home == null || !home.isStructurallyValid())
                    throw new SQLException("Invalid legacy home; original data was preserved");
            }
            String defaultHome = player.getDefaultHome();
            if (defaultHome != null
                    && !defaultHome.isEmpty()
                    && player.getHome(defaultHome).isEmpty())
                throw new SQLException(
                        "Legacy default home does not exist; original data was preserved");
        }
    }

    // Full replacement is reserved for explicit import/cleanup, never normal player edits.
    public static void save(Path databasePath, StorageData storage) throws Exception {
        initialize(databasePath);
        try (Connection connection = connect(databasePath)) {
            transaction(
                    connection,
                    transactionConnection -> {
                        try (Statement statement = transactionConnection.createStatement()) {
                            statement.executeUpdate("DELETE FROM homes");
                            statement.executeUpdate("DELETE FROM players");
                            statement.executeUpdate("DELETE FROM warps");
                        }
                        insertAll(transactionConnection, storage);
                        verify(storage, read(transactionConnection));
                    });
        }
    }

    private static void insertAll(Connection connection, StorageData storage) throws SQLException {
        int playerPosition = 0;
        for (PlayerData player : storage.getPlayers()) {
            execute(
                    connection,
                    "INSERT INTO players VALUES(?,?,?)",
                    player.getUUID(),
                    player.getDefaultHome(),
                    playerPosition++);
            int homePosition = 0;
            for (NamedLocation home : player.getHomes())
                insertLocation(connection, player.getUUID(), home, homePosition++);
        }
        int warpPosition = 0;
        for (NamedLocation warp : storage.getWarps())
            insertLocation(connection, null, warp, warpPosition++);
    }

    private static void insertLocation(
            Connection connection, String owner, NamedLocation location, int position)
            throws SQLException {
        if (owner == null) {
            execute(
                    connection,
                    "INSERT INTO warps VALUES(?,?,?,?,?,?,?)",
                    location.getName(),
                    location.getWorldString(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getIcon(),
                    position);
        } else {
            execute(
                    connection,
                    "INSERT INTO homes VALUES(?,?,?,?,?,?,?,?)",
                    owner,
                    location.getName(),
                    location.getWorldString(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getIcon(),
                    position);
        }
    }

    public static void addLocation(Path databasePath, PlayerData owner, NamedLocation location)
            throws Exception {
        edit(
                databasePath,
                transactionConnection -> {
                    if (owner != null)
                        execute(
                                transactionConnection,
                                "INSERT INTO players(uuid,default_home,position) SELECT"
                                    + " ?,?,COALESCE(MAX(position),-1)+1 FROM players WHERE true ON"
                                    + " CONFLICT(uuid) DO NOTHING",
                                owner.getUUID(),
                                owner.getDefaultHome());
                    String table = owner == null ? "warps" : "homes";
                    int position;
                    try (Statement statement = transactionConnection.createStatement();
                            ResultSet resultSet =
                                    statement.executeQuery(
                                            "SELECT COALESCE(MAX(position),-1)+1 FROM " + table)) {
                        resultSet.next();
                        position = resultSet.getInt(1);
                    }
                    insertLocation(
                            transactionConnection,
                            owner == null ? null : owner.getUUID(),
                            location,
                            position);
                });
    }

    public static void deleteLocation(Path databasePath, String owner, String name)
            throws Exception {
        edit(
                databasePath,
                transactionConnection -> {
                    if (owner == null)
                        requireOne(
                                execute(
                                        transactionConnection,
                                        "DELETE FROM warps WHERE name=?",
                                        name));
                    else {
                        requireOne(
                                execute(
                                        transactionConnection,
                                        "DELETE FROM homes WHERE owner=? AND name=?",
                                        owner,
                                        name));
                        execute(
                                transactionConnection,
                                "UPDATE players SET default_home='' WHERE uuid=? AND"
                                        + " default_home=?",
                                owner,
                                name);
                    }
                });
    }

    public static void renameLocation(
            Path databasePath, String owner, String oldName, String newName) throws Exception {
        edit(
                databasePath,
                transactionConnection -> {
                    if (owner == null)
                        requireOne(
                                execute(
                                        transactionConnection,
                                        "UPDATE warps SET name=? WHERE name=?",
                                        newName,
                                        oldName));
                    else {
                        requireOne(
                                execute(
                                        transactionConnection,
                                        "UPDATE homes SET name=? WHERE owner=? AND name=?",
                                        newName,
                                        owner,
                                        oldName));
                        execute(
                                transactionConnection,
                                "UPDATE players SET default_home=? WHERE uuid=? AND default_home=?",
                                newName,
                                owner,
                                oldName);
                    }
                });
    }

    public static void setIcon(Path databasePath, String owner, String name, String icon)
            throws Exception {
        edit(
                databasePath,
                connection ->
                        requireOne(
                                owner == null
                                        ? execute(
                                                connection,
                                                "UPDATE warps SET icon=? WHERE name=?",
                                                icon,
                                                name)
                                        : execute(
                                                connection,
                                                "UPDATE homes SET icon=? WHERE owner=? AND name=?",
                                                icon,
                                                owner,
                                                name)));
    }

    public static void setDefaultHome(Path databasePath, String owner, String name)
            throws Exception {
        edit(
                databasePath,
                connection ->
                        requireOne(
                                execute(
                                        connection,
                                        "UPDATE players SET default_home=? WHERE uuid=?",
                                        name,
                                        owner)));
    }

    private static void requireOne(int rows) throws SQLException {
        if (rows != 1)
            throw new SQLException("The location/player no longer exists; reopen the menu");
    }

    private static int execute(Connection connection, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Change {
        void run(Connection connection) throws Exception;
    }

    private static void transaction(Connection connection, Change change) throws Exception {
        connection.setAutoCommit(false);
        try {
            change.run(connection);
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void edit(Path databasePath, Change change) throws Exception {
        try (Connection connection = connect(databasePath)) {
            transaction(connection, change);
        }
    }

    public static StorageData importJsonAtomically(Path json, Path databasePath) throws Exception {
        StorageData imported = StorageManager.loadJsonFile(json);
        validateMigration(imported);
        imported.normalize();
        Files.createDirectories(databasePath.toAbsolutePath().getParent());
        Path temporary =
                Files.createTempFile(
                        databasePath.toAbsolutePath().getParent(), "tpa-import-", ".db");
        try {
            save(temporary, imported);
            StorageData verified = load(temporary);
            verify(imported, verified);
            if (Files.exists(databasePath)) {
                try (Connection connection = connect(databasePath)) {
                    backup(connection, databasePath, ".backup-");
                }
                // Apply transactionally; no rename/WAL sidecar race with the live database.
                save(databasePath, verified);
            } else {
                // A failed first import must not leave an empty live database that suppresses
                // retry.
                try {
                    Files.move(temporary, databasePath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, databasePath);
                }
            }
            return verified;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
