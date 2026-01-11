package com.basketballbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class BasketballAPI {
    private static final String BASE_URL = "https://v1.basketball.api-sports.io";
    private final String apiKey;
    private final OkHttpClient client;

    public BasketballAPI(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
    }

    private JsonObject makeRequest(String endpoint) {
        try {
            Request request = new Request.Builder()
                    .url(BASE_URL + endpoint)
                    .addHeader("x-rapidapi-key", apiKey)
                    .addHeader("x-rapidapi-host", "v1.basketball.api-sports.io")
                    .build();

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();
                return JsonParser.parseString(jsonResponse).getAsJsonObject();
            }
        } catch (IOException e) {
            System.err.println("Errore chiamata API: " + e.getMessage());
        }
        return null;
    }

    // Cerca giocatori per nome
    public List<Map<String, String>> searchPlayers(String name) {
        List<Map<String, String>> players = new ArrayList<>();
        JsonObject response = makeRequest("/players?search=" + name);

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject playerObj = element.getAsJsonObject();
                Map<String, String> player = new HashMap<>();

                player.put("id", getString(playerObj, "id"));
                player.put("name", getString(playerObj, "name"));
                player.put("firstname", getString(playerObj, "firstname"));
                player.put("lastname", getString(playerObj, "lastname"));

                if (playerObj.has("birth")) {
                    JsonObject birth = playerObj.getAsJsonObject("birth");
                    player.put("country", getString(birth, "country"));
                    player.put("date", getString(birth, "date"));
                }

                if (playerObj.has("height")) {
                    player.put("height", getString(playerObj, "height"));
                }

                if (playerObj.has("weight")) {
                    player.put("weight", getString(playerObj, "weight"));
                }

                // Team info se disponibile
                if (playerObj.has("team")) {
                    JsonObject team = playerObj.getAsJsonObject("team");
                    player.put("team_name", getString(team, "name"));
                    player.put("team_id", getString(team, "id"));
                }

                players.add(player);

                if (players.size() >= 5) break; // Limita a 5 risultati
            }
        }
        return players;
    }

    // Ottieni dettagli giocatore
    public Map<String, String> getPlayerDetails(int playerId) {
        JsonObject response = makeRequest("/players?id=" + playerId);

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            if (results.size() > 0) {
                JsonObject playerObj = results.get(0).getAsJsonObject();
                Map<String, String> player = new HashMap<>();

                player.put("id", getString(playerObj, "id"));
                player.put("name", getString(playerObj, "name"));
                player.put("firstname", getString(playerObj, "firstname"));
                player.put("lastname", getString(playerObj, "lastname"));

                if (playerObj.has("birth")) {
                    JsonObject birth = playerObj.getAsJsonObject("birth");
                    player.put("country", getString(birth, "country"));
                    player.put("date", getString(birth, "date"));
                }

                player.put("height", getString(playerObj, "height"));
                player.put("weight", getString(playerObj, "weight"));

                if (playerObj.has("team")) {
                    JsonObject team = playerObj.getAsJsonObject("team");
                    player.put("team_name", getString(team, "name"));
                    player.put("team_id", getString(team, "id"));
                }

                if (playerObj.has("leagues")) {
                    JsonArray leagues = playerObj.getAsJsonArray("leagues");
                    if (leagues.size() > 0) {
                        JsonObject league = leagues.get(0).getAsJsonObject();
                        player.put("league", getString(league, "name"));
                    }
                }

                return player;
            }
        }
        return null;
    }

    // Ottieni partite live
    public List<Map<String, String>> getLiveGames() {
        List<Map<String, String>> games = new ArrayList<>();
        JsonObject response = makeRequest("/games?live=all");

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject gameObj = element.getAsJsonObject();
                Map<String, String> game = parseGame(gameObj);
                games.add(game);

                if (games.size() >= 10) break;
            }
        }
        return games;
    }

    // Ottieni partite per data
    public List<Map<String, String>> getGamesByDate(String date) {
        List<Map<String, String>> games = new ArrayList<>();
        JsonObject response = makeRequest("/games?date=" + date + "&league=12"); // NBA

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject gameObj = element.getAsJsonObject();
                Map<String, String> game = parseGame(gameObj);
                games.add(game);
            }
        }
        return games;
    }

    // Ottieni partite di oggi
    public List<Map<String, String>> getTodayGames() {
        String today = LocalDate.now().toString();
        return getGamesByDate(today);
    }

    // Cerca squadre
    public List<Map<String, String>> searchTeams(String name) {
        List<Map<String, String>> teams = new ArrayList<>();
        JsonObject response = makeRequest("/teams?search=" + name);

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject teamObj = element.getAsJsonObject();
                Map<String, String> team = new HashMap<>();

                team.put("id", getString(teamObj, "id"));
                team.put("name", getString(teamObj, "name"));
                team.put("country", getString(teamObj, "country"));
                team.put("logo", getString(teamObj, "logo"));

                teams.add(team);

                if (teams.size() >= 5) break;
            }
        }
        return teams;
    }

    // Ottieni squadre NBA
    public List<Map<String, String>> getNBATeams() {
        List<Map<String, String>> teams = new ArrayList<>();
        JsonObject response = makeRequest("/teams?league=12&season=2024-2025"); // NBA

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject teamObj = element.getAsJsonObject();
                Map<String, String> team = new HashMap<>();

                team.put("id", getString(teamObj, "id"));
                team.put("name", getString(teamObj, "name"));
                team.put("country", getString(teamObj, "country"));
                team.put("logo", getString(teamObj, "logo"));

                teams.add(team);
            }
        }
        return teams;
    }

    // Ottieni classifica NBA
    public List<Map<String, String>> getNBAStandings() {
        List<Map<String, String>> standings = new ArrayList<>();
        JsonObject response = makeRequest("/standings?league=12&season=2024-2025");

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            for (JsonElement element : results) {
                JsonObject standingObj = element.getAsJsonObject();
                Map<String, String> standing = new HashMap<>();

                standing.put("position", getString(standingObj, "position"));

                if (standingObj.has("team")) {
                    JsonObject team = standingObj.getAsJsonObject("team");
                    standing.put("team_name", getString(team, "name"));
                    standing.put("team_id", getString(team, "id"));
                }

                if (standingObj.has("games")) {
                    JsonObject games = standingObj.getAsJsonObject("games");
                    standing.put("wins", getString(games.getAsJsonObject("win"), "total"));
                    standing.put("losses", getString(games.getAsJsonObject("lose"), "total"));
                }

                standings.add(standing);
            }
        }
        return standings;
    }

    // Statistiche giocatore per stagione
    public Map<String, String> getPlayerSeasonStats(int playerId, int teamId) {
        JsonObject response = makeRequest("/statistics/players?player=" + playerId +
                "&team=" + teamId + "&season=2024-2025");

        if (response != null && response.has("response")) {
            JsonArray results = response.getAsJsonArray("response");

            if (results.size() > 0) {
                JsonObject statsObj = results.get(0).getAsJsonObject();
                Map<String, String> stats = new HashMap<>();

                if (statsObj.has("statistics")) {
                    JsonArray statArray = statsObj.getAsJsonArray("statistics");
                    if (statArray.size() > 0) {
                        JsonObject stat = statArray.get(0).getAsJsonObject();

                        stats.put("games", getString(stat, "games"));
                        stats.put("points", getString(stat, "points"));
                        stats.put("assists", getString(stat, "assists"));
                        stats.put("rebounds", getString(stat, "rebounds"));
                        stats.put("steals", getString(stat, "steals"));
                        stats.put("blocks", getString(stat, "blocks"));
                    }
                }

                return stats;
            }
        }
        return null;
    }

    // Helper per parsing game
    private Map<String, String> parseGame(JsonObject gameObj) {
        Map<String, String> game = new HashMap<>();

        game.put("id", getString(gameObj, "id"));
        game.put("date", getString(gameObj, "date"));
        game.put("status", getString(gameObj.getAsJsonObject("status"), "long"));

        if (gameObj.has("league")) {
            JsonObject league = gameObj.getAsJsonObject("league");
            game.put("league", getString(league, "name"));
        }

        if (gameObj.has("teams")) {
            JsonObject teams = gameObj.getAsJsonObject("teams");

            JsonObject home = teams.getAsJsonObject("home");
            game.put("home_team", getString(home, "name"));
            game.put("home_id", getString(home, "id"));

            JsonObject away = teams.getAsJsonObject("away");
            game.put("away_team", getString(away, "name"));
            game.put("away_id", getString(away, "id"));
        }

        if (gameObj.has("scores")) {
            JsonObject scores = gameObj.getAsJsonObject("scores");

            JsonObject home = scores.getAsJsonObject("home");
            game.put("home_score", getString(home, "total"));

            JsonObject away = scores.getAsJsonObject("away");
            game.put("away_score", getString(away, "total"));
        }

        return game;
    }

    // Helper per getString sicuro
    private String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "N/A";
    }
}