package com.basketballbot;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_PATH = "basketball_bot.db";

    private DatabaseManager() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        } catch (SQLException e) {
            System.err.println("Errore connessione database: " + e.getMessage());
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initDatabase() {
        try {
            Statement stmt = connection.createStatement();

            // Tabella utenti
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "user_id INTEGER PRIMARY KEY, " +
                            "username TEXT, " +
                            "first_name TEXT, " +
                            "registration_date TEXT, " +
                            "last_interaction TEXT, " +
                            "message_count INTEGER DEFAULT 0)"
            );

            // Tabella giocatori preferiti
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS favorite_players (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "player_id INTEGER, " +
                            "player_name TEXT, " +
                            "team TEXT, " +
                            "added_date TEXT, " +
                            "FOREIGN KEY (user_id) REFERENCES users(user_id))"
            );

            // Tabella squadre preferite
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS favorite_teams (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "team_id INTEGER, " +
                            "team_name TEXT, " +
                            "league TEXT, " +
                            "added_date TEXT, " +
                            "FOREIGN KEY (user_id) REFERENCES users(user_id))"
            );

            // Cache giocatori
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS players_cache (" +
                            "player_id INTEGER PRIMARY KEY, " +
                            "player_name TEXT, " +
                            "team TEXT, " +
                            "position TEXT, " +
                            "height TEXT, " +
                            "weight TEXT, " +
                            "country TEXT, " +
                            "data TEXT, " +
                            "last_update TEXT)"
            );

            // Cache squadre
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS teams_cache (" +
                            "team_id INTEGER PRIMARY KEY, " +
                            "team_name TEXT, " +
                            "league TEXT, " +
                            "country TEXT, " +
                            "logo TEXT, " +
                            "data TEXT, " +
                            "last_update TEXT)"
            );

            // Statistiche ricerche
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS search_stats (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "search_type TEXT, " +
                            "search_query TEXT, " +
                            "timestamp TEXT, " +
                            "FOREIGN KEY (user_id) REFERENCES users(user_id))"
            );

            // Notifiche partite
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS game_notifications (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "team_id INTEGER, " +
                            "team_name TEXT, " +
                            "enabled INTEGER DEFAULT 1, " +
                            "FOREIGN KEY (user_id) REFERENCES users(user_id))"
            );

            System.out.println("✅ Database inizializzato correttamente");

        } catch (SQLException e) {
            System.err.println("Errore inizializzazione database: " + e.getMessage());
        }
    }

    // Gestione utenti
    public void registerUser(long userId, String username, String firstName) {
        try {
            String sql = "INSERT OR IGNORE INTO users (user_id, username, first_name, registration_date, last_interaction, message_count) " +
                    "VALUES (?, ?, ?, ?, ?, 0)";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            pstmt.setString(2, username);
            pstmt.setString(3, firstName);
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.setString(5, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore registrazione utente: " + e.getMessage());
        }
    }

    public void updateUserInteraction(long userId) {
        try {
            String sql = "UPDATE users SET last_interaction = ?, message_count = message_count + 1 WHERE user_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, LocalDateTime.now().toString());
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore aggiornamento interazione: " + e.getMessage());
        }
    }

    // Giocatori preferiti
    public void addFavoritePlayer(long userId, int playerId, String playerName, String team) {
        try {
            String sql = "INSERT INTO favorite_players (user_id, player_id, player_name, team, added_date) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            pstmt.setInt(2, playerId);
            pstmt.setString(3, playerName);
            pstmt.setString(4, team);
            pstmt.setString(5, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore aggiunta giocatore preferito: " + e.getMessage());
        }
    }

    public void removeFavoritePlayer(long userId, int playerId) {
        try {
            String sql = "DELETE FROM favorite_players WHERE user_id = ? AND player_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            pstmt.setInt(2, playerId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore rimozione giocatore preferito: " + e.getMessage());
        }
    }

    public List<Map<String, String>> getFavoritePlayers(long userId) {
        List<Map<String, String>> players = new ArrayList<>();
        try {
            String sql = "SELECT * FROM favorite_players WHERE user_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, String> player = new HashMap<>();
                player.put("player_id", String.valueOf(rs.getInt("player_id")));
                player.put("player_name", rs.getString("player_name"));
                player.put("team", rs.getString("team"));
                players.add(player);
            }
        } catch (SQLException e) {
            System.err.println("Errore recupero giocatori preferiti: " + e.getMessage());
        }
        return players;
    }

    // Cache giocatori
    public void cachePlayer(int playerId, String playerName, String team, String position,
                            String height, String weight, String country, String data) {
        try {
            String sql = "INSERT OR REPLACE INTO players_cache (player_id, player_name, team, position, height, weight, country, data, last_update) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, playerId);
            pstmt.setString(2, playerName);
            pstmt.setString(3, team);
            pstmt.setString(4, position);
            pstmt.setString(5, height);
            pstmt.setString(6, weight);
            pstmt.setString(7, country);
            pstmt.setString(8, data);
            pstmt.setString(9, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore cache giocatore: " + e.getMessage());
        }
    }

    public Map<String, String> getCachedPlayer(int playerId) {
        try {
            String sql = "SELECT * FROM players_cache WHERE player_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Map<String, String> player = new HashMap<>();
                player.put("player_id", String.valueOf(rs.getInt("player_id")));
                player.put("player_name", rs.getString("player_name"));
                player.put("team", rs.getString("team"));
                player.put("position", rs.getString("position"));
                player.put("height", rs.getString("height"));
                player.put("weight", rs.getString("weight"));
                player.put("country", rs.getString("country"));
                player.put("data", rs.getString("data"));
                player.put("last_update", rs.getString("last_update"));
                return player;
            }
        } catch (SQLException e) {
            System.err.println("Errore recupero cache giocatore: " + e.getMessage());
        }
        return null;
    }

    // Statistiche ricerche
    public void logSearch(long userId, String searchType, String query) {
        try {
            String sql = "INSERT INTO search_stats (user_id, search_type, search_query, timestamp) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            pstmt.setString(2, searchType);
            pstmt.setString(3, query);
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Errore log ricerca: " + e.getMessage());
        }
    }

    public Map<String, Object> getUserStats(long userId) {
        Map<String, Object> stats = new HashMap<>();
        try {
            // Conteggio messaggi
            String sql = "SELECT message_count FROM users WHERE user_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.put("message_count", rs.getInt("message_count"));
            }

            // Ricerche totali
            sql = "SELECT COUNT(*) as count FROM search_stats WHERE user_id = ?";
            pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.put("search_count", rs.getInt("count"));
            }

            // Giocatori preferiti
            sql = "SELECT COUNT(*) as count FROM favorite_players WHERE user_id = ?";
            pstmt = connection.prepareStatement(sql);
            pstmt.setLong(1, userId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.put("favorite_players", rs.getInt("count"));
            }

        } catch (SQLException e) {
            System.err.println("Errore recupero statistiche utente: " + e.getMessage());
        }
        return stats;
    }

    public List<Map<String, Object>> getTopSearches(int limit) {
        List<Map<String, Object>> searches = new ArrayList<>();
        try {
            String sql = "SELECT search_query, COUNT(*) as count FROM search_stats " +
                    "GROUP BY search_query ORDER BY count DESC LIMIT ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> search = new HashMap<>();
                search.put("query", rs.getString("search_query"));
                search.put("count", rs.getInt("count"));
                searches.add(search);
            }
        } catch (SQLException e) {
            System.err.println("Errore recupero top ricerche: " + e.getMessage());
        }
        return searches;
    }

    public int getTotalUsers() {
        try {
            String sql = "SELECT COUNT(*) as count FROM users";
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Errore conteggio utenti: " + e.getMessage());
        }
        return 0;
    }
}