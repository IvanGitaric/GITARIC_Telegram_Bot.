package com.footballbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Client per interagire con l'API di football-data.org
 * Gestisce il recupero di classifiche, partite e classifica marcatori
 */
public class FootballAPI {
    private static final String API_BASE_URL = "https://api.football-data.org/v4";
    private final String apiKey;
    private final HttpClient httpClient;
    private final DatabaseManager databaseManager;

    public FootballAPI() {
        this.apiKey = MyConfiguration.getInstance().getProperty("FOOTBALL_API_KEY");
        this.httpClient = HttpClient.newHttpClient();
        this.databaseManager = DatabaseManager.getInstance();

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("⚠️  ATTENZIONE: FOOTBALL_API_KEY non configurata!");
        }
    }

    /**
     * Recupera la classifica di un campionato
     * @param leagueCode Codice del campionato (es: SA, PL, PD)
     * @return Stringa formattata con la classifica
     */
    public String getStandings(String leagueCode) {
        String cacheKey = "standings_" + leagueCode;

        // Controlla se c'è una cache valida
        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/competitions/" + leagueCode + "/standings";
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray standings = json.getAsJsonArray("standings");

            if (standings == null || standings.isEmpty()) {
                return "❌ Classifica non disponibile per questo campionato.";
            }

            JsonObject standing = standings.get(0).getAsJsonObject();
            JsonArray table = standing.getAsJsonArray("table");

            StringBuilder result = new StringBuilder();
            result.append("📊 CLASSIFICA\n");
            result.append(getLeagueNameFromJson(json)).append("\n\n");

            for (int i = 0; i < Math.min(table.size(), 20); i++) {
                JsonObject team = table.get(i).getAsJsonObject();
                int position = team.get("position").getAsInt();
                String teamName = team.getAsJsonObject("team").get("name").getAsString();
                int points = team.get("points").getAsInt();
                int played = team.get("playedGames").getAsInt();
                int won = team.get("won").getAsInt();
                int draw = team.get("draw").getAsInt();
                int lost = team.get("lost").getAsInt();
                int goalsFor = team.get("goalsFor").getAsInt();
                int goalsAgainst = team.get("goalsAgainst").getAsInt();
                int goalDifference = team.get("goalDifference").getAsInt();

                String positionEmoji = getPositionEmoji(position);

                result.append(String.format("%s %d. %s\n", positionEmoji, position, teamName));
                result.append(String.format("   Pt: %d | G: %d | V:%d P:%d S:%d | GF:%d GS:%d Diff:%+d\n\n",
                        points, played, won, draw, lost, goalsFor, goalsAgainst, goalDifference));
            }

            String finalResult = result.toString();
            // Salva in cache per 30 minuti
            databaseManager.saveCache(cacheKey, finalResult, leagueCode, 30);
            System.out.println("💾 Classifica salvata in cache");

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero della classifica: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recupero della classifica. Verifica che il campionato sia attivo.";
        }
    }

    /**
     * Recupera le prossime partite di un campionato
     * @param leagueCode Codice del campionato
     * @return Stringa formattata con le partite
     */
    public String getMatches(String leagueCode) {
        String cacheKey = "matches_" + leagueCode;

        // Controlla cache (10 minuti per le partite)
        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/competitions/" + leagueCode + "/matches?status=SCHEDULED";
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray matches = json.getAsJsonArray("matches");

            if (matches == null || matches.isEmpty()) {
                return "❌ Nessuna partita in programma al momento.";
            }

            StringBuilder result = new StringBuilder();
            result.append("⚽ PROSSIME PARTITE\n");
            result.append(getLeagueNameFromCompetition(json)).append("\n\n");

            DateTimeFormatter inputFormatter = DateTimeFormatter.ISO_DATE_TIME;
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            for (int i = 0; i < Math.min(matches.size(), 10); i++) {
                JsonObject match = matches.get(i).getAsJsonObject();

                String homeTeam = match.getAsJsonObject("homeTeam").get("name").getAsString();
                String awayTeam = match.getAsJsonObject("awayTeam").get("name").getAsString();
                String utcDate = match.get("utcDate").getAsString();

                String matchday = "";
                if (!match.get("matchday").isJsonNull()) {
                    matchday = "Giornata " + match.get("matchday").getAsInt() + " - ";
                }

                LocalDateTime dateTime = LocalDateTime.parse(utcDate, inputFormatter);
                String formattedDate = dateTime.format(outputFormatter);

                result.append(matchday).append(formattedDate).append("\n");
                result.append(homeTeam).append(" vs ").append(awayTeam).append("\n\n");
            }

            String finalResult = result.toString();
            // Cache per 10 minuti
            databaseManager.saveCache(cacheKey, finalResult, leagueCode, 10);
            System.out.println("💾 Partite salvate in cache");

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero delle partite: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recupero delle partite.";
        }
    }

    /**
     * Recupera la classifica marcatori di un campionato
     * @param leagueCode Codice del campionato
     * @return Stringa formattata con i top scorer
     */
    public String getTopScorers(String leagueCode) {
        String cacheKey = "topscorers_" + leagueCode;

        // Controlla cache (60 minuti per i marcatori)
        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/competitions/" + leagueCode + "/scorers?limit=15";
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray scorers = json.getAsJsonArray("scorers");

            if (scorers == null || scorers.isEmpty()) {
                return "❌ Classifica marcatori non disponibile per questo campionato.";
            }

            StringBuilder result = new StringBuilder();
            result.append("🥇 CLASSIFICA MARCATORI\n");
            result.append(getLeagueNameFromCompetition(json)).append("\n\n");

            for (int i = 0; i < Math.min(scorers.size(), 15); i++) {
                JsonObject scorer = scorers.get(i).getAsJsonObject();
                JsonObject player = scorer.getAsJsonObject("player");
                JsonObject team = scorer.getAsJsonObject("team");

                String playerName = player.get("name").getAsString();
                String teamName = team.get("name").getAsString();
                int goals = scorer.get("goals").getAsInt();

                Integer assists = null;
                Integer penalties = null;

                if (scorer.has("assists") && !scorer.get("assists").isJsonNull()) {
                    assists = scorer.get("assists").getAsInt();
                }
                if (scorer.has("penalties") && !scorer.get("penalties").isJsonNull()) {
                    penalties = scorer.get("penalties").getAsInt();
                }

                String medal = "";
                if (i == 0) medal = "🥇";
                else if (i == 1) medal = "🥈";
                else if (i == 2) medal = "🥉";
                else medal = (i + 1) + ".";

                result.append(String.format("%s %s (%s)\n", medal, playerName, teamName));
                result.append(String.format("   ⚽ Gol: %d", goals));

                if (assists != null) {
                    result.append(String.format(" | 🅰️ Assist: %d", assists));
                }
                if (penalties != null && penalties > 0) {
                    result.append(String.format(" | 🎯 Rigori: %d", penalties));
                }

                result.append("\n\n");
            }

            String finalResult = result.toString();
            // Cache per 60 minuti
            databaseManager.saveCache(cacheKey, finalResult, leagueCode, 60);
            System.out.println("💾 Marcatori salvati in cache");

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero della classifica marcatori: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recupero della classifica marcatori.";
        }
    }

    /**
     * Effettua una chiamata HTTP all'API
     */
    private String makeApiRequest(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("X-Auth-Token", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("API request failed with status: " + response.statusCode());
        }

        return response.body();
    }

    private String getLeagueNameFromJson(JsonObject json) {
        if (json.has("competition")) {
            JsonObject competition = json.getAsJsonObject("competition");
            return competition.get("name").getAsString();
        }
        return "";
    }

    private String getLeagueNameFromCompetition(JsonObject json) {
        if (json.has("competition")) {
            JsonObject competition = json.getAsJsonObject("competition");
            return competition.get("name").getAsString();
        }
        return "";
    }

    /**
     * Recupera le informazioni dettagliate di una squadra con la rosa
     * @param teamId ID della squadra
     * @return Stringa formattata con le informazioni della squadra
     */
    public String getTeamInfo(int teamId) {
        String cacheKey = "team_" + teamId;

        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/teams/" + teamId;
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            String teamName = json.get("name").getAsString();
            String shortName = json.get("shortName").getAsString();
            String founded = json.has("founded") && !json.get("founded").isJsonNull()
                    ? json.get("founded").getAsString() : "N/D";
            String venue = json.has("venue") && !json.get("venue").isJsonNull()
                    ? json.get("venue").getAsString() : "N/D";
            String clubColors = json.has("clubColors") && !json.get("clubColors").isJsonNull()
                    ? json.get("clubColors").getAsString() : "N/D";

            StringBuilder result = new StringBuilder();
            result.append("🏟️ INFORMAZIONI SQUADRA\n\n");
            result.append("📌 Nome: ").append(teamName).append("\n");
            result.append("🔤 Nome breve: ").append(shortName).append("\n");
            result.append("📅 Fondata: ").append(founded).append("\n");
            result.append("🏟️ Stadio: ").append(venue).append("\n");
            result.append("🎨 Colori: ").append(clubColors).append("\n\n");

            // Rosa giocatori
            if (json.has("squad") && !json.get("squad").isJsonNull()) {
                JsonArray squad = json.getAsJsonArray("squad");
                result.append("👥 ROSA (").append(squad.size()).append(" giocatori)\n\n");

                // Raggruppa per ruolo
                int goalkeepers = 0, defenders = 0, midfielders = 0, attackers = 0;

                for (int i = 0; i < squad.size(); i++) {
                    JsonObject player = squad.get(i).getAsJsonObject();
                    String position = player.has("position") && !player.get("position").isJsonNull()
                            ? player.get("position").getAsString() : "N/D";

                    if (position.contains("Goalkeeper")) goalkeepers++;
                    else if (position.contains("Defence")) defenders++;
                    else if (position.contains("Midfield")) midfielders++;
                    else if (position.contains("Offence")) attackers++;
                }

                result.append("🧤 Portieri: ").append(goalkeepers).append("\n");
                result.append("🛡️ Difensori: ").append(defenders).append("\n");
                result.append("⚙️ Centrocampisti: ").append(midfielders).append("\n");
                result.append("⚡ Attaccanti: ").append(attackers).append("\n");
            }

            String finalResult = result.toString();
            databaseManager.saveCache(cacheKey, finalResult, null, 120);
            System.out.println("💾 Info squadra salvate in cache");

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero info squadra: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recupero delle informazioni della squadra.";
        }
    }

    /**
     * Cerca un giocatore per nome
     * @param playerName Nome del giocatore da cercare
     * @return Stringa formattata con i risultati della ricerca
     */
    public String searchPlayer(String playerName) {
        try {
            String endpoint = API_BASE_URL + "/persons?name=" + playerName.replace(" ", "%20");
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray persons = json.getAsJsonArray("persons");

            if (persons == null || persons.isEmpty()) {
                return "❌ Nessun giocatore trovato con il nome: " + playerName;
            }

            StringBuilder result = new StringBuilder();
            result.append("🔍 RISULTATI RICERCA: ").append(playerName).append("\n\n");

            for (int i = 0; i < Math.min(persons.size(), 10); i++) {
                JsonObject person = persons.get(i).getAsJsonObject();

                String name = person.get("name").getAsString();
                String dateOfBirth = person.has("dateOfBirth") && !person.get("dateOfBirth").isJsonNull()
                        ? person.get("dateOfBirth").getAsString() : "N/D";
                String nationality = person.has("nationality") && !person.get("nationality").isJsonNull()
                        ? person.get("nationality").getAsString() : "N/D";
                String position = person.has("position") && !person.get("position").isJsonNull()
                        ? person.get("position").getAsString() : "N/D";

                result.append((i + 1)).append(". ").append(name).append("\n");
                result.append("   📅 Nato: ").append(dateOfBirth).append("\n");
                result.append("   🌍 Nazionalità: ").append(nationality).append("\n");
                result.append("   ⚽ Ruolo: ").append(position).append("\n\n");
            }

            return result.toString();

        } catch (Exception e) {
            System.err.println("❌ Errore nella ricerca giocatore: " + e.getMessage());
            return "❌ Errore nella ricerca del giocatore.";
        }
    }

    /**
     * Recupera le partite di oggi da tutti i campionati
     * @return Stringa formattata con le partite di oggi
     */
    public String getTodayMatches() {
        String cacheKey = "matches_today";

        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/matches";
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray matches = json.getAsJsonArray("matches");

            if (matches == null || matches.isEmpty()) {
                return "❌ Nessuna partita in programma oggi.";
            }

            StringBuilder result = new StringBuilder();
            result.append("📅 PARTITE DI OGGI\n\n");

            DateTimeFormatter inputFormatter = DateTimeFormatter.ISO_DATE_TIME;
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("HH:mm");

            for (int i = 0; i < Math.min(matches.size(), 20); i++) {
                JsonObject match = matches.get(i).getAsJsonObject();

                String competition = match.getAsJsonObject("competition").get("name").getAsString();
                String homeTeam = match.getAsJsonObject("homeTeam").get("shortName").getAsString();
                String awayTeam = match.getAsJsonObject("awayTeam").get("shortName").getAsString();
                String utcDate = match.get("utcDate").getAsString();
                String status = match.get("status").getAsString();

                LocalDateTime dateTime = LocalDateTime.parse(utcDate, inputFormatter);
                String formattedTime = dateTime.format(outputFormatter);

                String statusEmoji = switch (status) {
                    case "SCHEDULED", "TIMED" -> "⏰";
                    case "IN_PLAY" -> "🔴";
                    case "PAUSED" -> "⏸️";
                    case "FINISHED" -> "✅";
                    default -> "⚪";
                };

                result.append(statusEmoji).append(" ").append(formattedTime).append(" | ");
                result.append(competition).append("\n");
                result.append("   ").append(homeTeam).append(" vs ").append(awayTeam);

                // Aggiungi il risultato se la partita è finita o in corso
                if (status.equals("FINISHED") || status.equals("IN_PLAY") || status.equals("PAUSED")) {
                    JsonObject score = match.getAsJsonObject("score");
                    if (score.has("fullTime") && !score.get("fullTime").isJsonNull()) {
                        JsonObject fullTime = score.getAsJsonObject("fullTime");
                        int home = fullTime.get("home").isJsonNull() ? 0 : fullTime.get("home").getAsInt();
                        int away = fullTime.get("away").isJsonNull() ? 0 : fullTime.get("away").getAsInt();
                        result.append(" (").append(home).append("-").append(away).append(")");
                    }
                }

                result.append("\n\n");
            }

            String finalResult = result.toString();
            databaseManager.saveCache(cacheKey, finalResult, null, 5); // Cache 5 minuti
            System.out.println("💾 Partite di oggi salvate in cache");

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero partite di oggi: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recupero delle partite di oggi.";
        }
    }

    /**
     * Recupera lo scontro diretto tra due squadre
     * @param team1Id ID prima squadra
     * @param team2Id ID seconda squadra
     * @return Stringa formattata con lo storico degli scontri
     */
    public String getHeadToHead(int team1Id, int team2Id) {
        String cacheKey = "h2h_" + team1Id + "_" + team2Id;

        String cachedData = databaseManager.getCache(cacheKey);
        if (cachedData != null) {
            System.out.println("📦 Dati caricati dalla cache: " + cacheKey);
            return cachedData;
        }

        try {
            String endpoint = API_BASE_URL + "/teams/" + team1Id + "/matches";
            String response = makeApiRequest(endpoint);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray allMatches = json.getAsJsonArray("matches");

            StringBuilder result = new StringBuilder();
            result.append("⚔️ SCONTRI DIRETTI\n\n");

            int team1Wins = 0, team2Wins = 0, draws = 0;
            int matchesFound = 0;

            for (int i = 0; i < allMatches.size() && matchesFound < 10; i++) {
                JsonObject match = allMatches.get(i).getAsJsonObject();

                int homeId = match.getAsJsonObject("homeTeam").get("id").getAsInt();
                int awayId = match.getAsJsonObject("awayTeam").get("id").getAsInt();

                // Verifica se la partita coinvolge entrambe le squadre
                if ((homeId == team1Id && awayId == team2Id) || (homeId == team2Id && awayId == team1Id)) {
                    String status = match.get("status").getAsString();

                    if (status.equals("FINISHED")) {
                        matchesFound++;

                        String homeTeam = match.getAsJsonObject("homeTeam").get("shortName").getAsString();
                        String awayTeam = match.getAsJsonObject("awayTeam").get("shortName").getAsString();

                        JsonObject score = match.getAsJsonObject("score");
                        JsonObject fullTime = score.getAsJsonObject("fullTime");
                        int homeGoals = fullTime.get("home").getAsInt();
                        int awayGoals = fullTime.get("away").getAsInt();

                        result.append(homeTeam).append(" ").append(homeGoals).append("-");
                        result.append(awayGoals).append(" ").append(awayTeam).append("\n");

                        // Conta vittorie
                        if (homeGoals > awayGoals) {
                            if (homeId == team1Id) team1Wins++; else team2Wins++;
                        } else if (awayGoals > homeGoals) {
                            if (awayId == team1Id) team1Wins++; else team2Wins++;
                        } else {
                            draws++;
                        }
                    }
                }
            }

            if (matchesFound == 0) {
                return "❌ Nessuno scontro diretto trovato tra queste squadre.";
            }

            result.append("\n📊 BILANCIO:\n");
            result.append("Vittorie squadra 1: ").append(team1Wins).append("\n");
            result.append("Pareggi: ").append(draws).append("\n");
            result.append("Vittorie squadra 2: ").append(team2Wins).append("\n");

            String finalResult = result.toString();
            databaseManager.saveCache(cacheKey, finalResult, null, 1440); // Cache 24 ore

            return finalResult;

        } catch (Exception e) {
            System.err.println("❌ Errore nel recupero H2H: " + e.getMessage());
            return "❌ Errore nel recupero dello storico scontri diretti.";
        }
    }

    /**
     * Restituisce un emoji in base alla posizione in classifica
     */
    private String getPositionEmoji(int position) {
        if (position == 1) return "🥇";
        if (position == 2) return "🥈";
        if (position == 3) return "🥉";
        if (position <= 4) return "🟢"; // Champions League
        if (position <= 6) return "🔵"; // Europa League
        if (position >= 18) return "🔴"; // Retrocessione
        return "⚪";
    }
}