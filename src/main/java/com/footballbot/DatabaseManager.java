package com.footballbot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce tutte le operazioni sul database SQLite del bot.
 * Implementa il pattern Singleton per garantire una sola connessione.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:footballbot.db";

    private DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            createTables();
            System.out.println("✅ Database connesso con successo (footballbot.db)");
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("❌ Errore nella connessione al database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Crea tutte le tabelle necessarie se non esistono
     */
    private void createTables() {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY,
                username TEXT,
                first_name TEXT,
                last_name TEXT,
                preferred_league TEXT,
                favorite_team TEXT,
                notifications_enabled INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                last_activity TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String createQueriesTable = """
            CREATE TABLE IF NOT EXISTS query_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                query_type TEXT,
                league_code TEXT,
                timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )
        """;

        String createCacheTable = """
            CREATE TABLE IF NOT EXISTS api_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cache_key TEXT UNIQUE,
                cache_data TEXT,
                league_code TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                expires_at TEXT
            )
        """;

        String createFavoriteTeamsTable = """
            CREATE TABLE IF NOT EXISTS favorite_teams (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                team_name TEXT,
                team_id INTEGER,
                added_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(user_id),
                UNIQUE(user_id, team_id)
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createQueriesTable);
            stmt.execute(createCacheTable);
            stmt.execute(createFavoriteTeamsTable);
            System.out.println("Tabelle database create/verificate");
        } catch (SQLException e) {
            System.err.println("Errore nella creazione delle tabelle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // GESTIONE UTENTI

    public void registerUser(Long userId, String username, String firstName, String lastName) {
        String sql = """
            INSERT OR IGNORE INTO users (user_id, username, first_name, last_name)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, username);
            pstmt.setString(3, firstName);
            pstmt.setString(4, lastName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateUserActivity(Long userId) {
        String sql = "UPDATE users SET last_activity = CURRENT_TIMESTAMP WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setPreferredLeague(Long userId, String leagueCode) {
        String sql = "UPDATE users SET preferred_league = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, leagueCode);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPreferredLeague(Long userId) {
        String sql = "SELECT preferred_league FROM users WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("preferred_league");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setFavoriteTeam(Long userId, String teamName) {
        String sql = "UPDATE users SET favorite_team = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, teamName);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getFavoriteTeam(Long userId) {
        String sql = "SELECT favorite_team FROM users WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("favorite_team");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setNotifications(Long userId, boolean enabled) {
        String sql = "UPDATE users SET notifications_enabled = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean getNotificationsEnabled(Long userId) {
        String sql = "SELECT notifications_enabled FROM users WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("notifications_enabled") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // STORICO QUERY

    public void logQuery(Long userId, String queryType, String leagueCode) {
        String sql = """
            INSERT INTO query_history (user_id, query_type, league_code)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, queryType);
            pstmt.setString(3, leagueCode);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getUserQueryHistory(Long userId, int limit) {
        String sql = """
            SELECT query_type, league_code, timestamp 
            FROM query_history 
            WHERE user_id = ? 
            ORDER BY timestamp DESC 
            LIMIT ?
        """;

        List<String> history = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String entry = String.format("%s - %s (%s)",
                        rs.getString("query_type"),
                        rs.getString("league_code"),
                        rs.getString("timestamp"));
                history.add(entry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return history;
    }

    public String getMostQueriedLeague(Long userId) {
        String sql = """
            SELECT league_code, COUNT(*) as count 
            FROM query_history 
            WHERE user_id = ? AND league_code IS NOT NULL
            GROUP BY league_code 
            ORDER BY count DESC 
            LIMIT 1
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("league_code");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // CACHE API

    public void saveCache(String cacheKey, String data, String leagueCode, int expirationMinutes) {
        String sql = """
            INSERT OR REPLACE INTO api_cache (cache_key, cache_data, league_code, created_at, expires_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, datetime('now', '+' || ? || ' minutes'))
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, cacheKey);
            pstmt.setString(2, data);
            pstmt.setString(3, leagueCode);
            pstmt.setInt(4, expirationMinutes);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getCache(String cacheKey) {
        String sql = """
            SELECT cache_data 
            FROM api_cache 
            WHERE cache_key = ? AND expires_at > CURRENT_TIMESTAMP
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, cacheKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("cache_data");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void clearExpiredCache() {
        String sql = "DELETE FROM api_cache WHERE expires_at < CURRENT_TIMESTAMP";

        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                System.out.println("🗑️  Cache scaduta eliminata: " + deleted + " record");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearAllCache() {
        String sql = "DELETE FROM api_cache";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("🗑️  Tutta la cache è stata eliminata");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // STATISTICHE

    public int getTotalUsers() {
        String sql = "SELECT COUNT(*) as count FROM users";

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getTotalQueries() {
        String sql = "SELECT COUNT(*) as count FROM query_history";

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // GESTIONE DELLE SQUADRE PREFERITE

    public void addFavoriteTeam(Long userId, String teamName, int teamId) {
        String sql = """
            INSERT OR IGNORE INTO favorite_teams (user_id, team_name, team_id)
            VALUES (?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, teamName);
            pstmt.setInt(3, teamId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getFavoriteTeams(Long userId) {
        String sql = "SELECT team_name FROM favorite_teams WHERE user_id = ? ORDER BY added_at DESC";
        List<String> teams = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                teams.add(rs.getString("team_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return teams;
    }

    public void removeFavoriteTeam(Long userId, int teamId) {
        String sql = "DELETE FROM favorite_teams WHERE user_id = ? AND team_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, teamId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ========== CHIUSURA ==========

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ Connessione al database chiusa");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}