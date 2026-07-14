package ddraig.net.custommobs.data;

import ddraig.net.custommobs.CustomMobs;
import dev.architectury.platform.Platform;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    public static final Map<UUID, List<String>> bestiaryCache = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Connection connection;

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            File folder = Platform.getConfigFolder().resolve("CustomMobs").toFile();
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File dbFile = new File(folder, "custommobs_discoveries.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS bestiary_discoveries (" +
                        "player_uuid TEXT," +
                        "mob_id TEXT," +
                        "PRIMARY KEY (player_uuid, mob_id)" +
                        ")");
            }
            loadCache();
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to initialize bestiary database:", e);
        }
    }

    private static void loadCache() {
        bestiaryCache.clear();
        if (connection == null) return;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM bestiary_discoveries")) {
            while (rs.next()) {
                String uuidStr = rs.getString("player_uuid");
                String mobId = rs.getString("mob_id");
                try {
                    UUID playerUuid = UUID.fromString(uuidStr);
                    bestiaryCache.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>()).add(mobId);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            CustomMobs.LOGGER.error("Failed to load bestiary cache:", e);
        }
    }

    public static void discoverMob(UUID playerUuid, String mobId) {
        List<String> list = bestiaryCache.computeIfAbsent(playerUuid, k -> new CopyOnWriteArrayList<>());
        if (!list.contains(mobId)) {
            list.add(mobId);
            executor.submit(() -> {
                if (connection == null) return;
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR REPLACE INTO bestiary_discoveries (player_uuid, mob_id) VALUES (?, ?)")) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, mobId);
                    stmt.executeUpdate();
                } catch (Exception e) {
                    CustomMobs.LOGGER.error("Failed to save bestiary discovery asynchronously:", e);
                }
            });
        }
    }

    public static void removeDiscovery(UUID playerUuid, String mobId) {
        List<String> list = bestiaryCache.get(playerUuid);
        if (list != null) {
            list.remove(mobId);
        }
        executor.submit(() -> {
            if (connection == null) return;
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM bestiary_discoveries WHERE player_uuid = ? AND mob_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, mobId);
                stmt.executeUpdate();
            } catch (Exception e) {
                CustomMobs.LOGGER.error("Failed to remove bestiary discovery asynchronously:", e);
            }
        });
    }

    public static boolean isDiscovered(UUID playerUuid, String mobId) {
        List<String> list = bestiaryCache.get(playerUuid);
        return list != null && list.contains(mobId);
    }
}
