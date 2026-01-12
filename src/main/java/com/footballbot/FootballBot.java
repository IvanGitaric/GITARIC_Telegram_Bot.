package com.footballbot;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bot Telegram per consultare dati calcistici
 * Gestisce comandi, callback e interazioni con gli utenti
 */
public class FootballBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final FootballAPI footballAPI;
    private final DatabaseManager databaseManager;
    private final Map<Long, String> userSelectedLeague = new HashMap<>();

    public FootballBot() {
        this.telegramClient = new OkHttpTelegramClient(MyConfiguration.getInstance().getProperty("BOT_TOKEN"));
        this.footballAPI = new FootballAPI();
        this.databaseManager = DatabaseManager.getInstance();
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Long userId = update.getMessage().getFrom().getId();
                String username = update.getMessage().getFrom().getUserName();
                String firstName = update.getMessage().getFrom().getFirstName();
                String lastName = update.getMessage().getFrom().getLastName();

                // Registra l'utente nel database
                databaseManager.registerUser(userId, username, firstName, lastName);
                databaseManager.updateUserActivity(userId);

                handleMessage(update);
            } else if (update.hasCallbackQuery()) {
                Long userId = update.getCallbackQuery().getFrom().getId();
                databaseManager.updateUserActivity(userId);

                handleCallbackQuery(update);
            }
        } catch (Exception e) {
            System.err.println("Errore durante l'elaborazione dell'update: " + e.getMessage());
            e.printStackTrace();

            String chatId = "";
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId().toString();
            } else if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId().toString();
            }

            if (!chatId.isEmpty()) {
                sendMessage(chatId, "❌ Si è verificato un errore. Riprova più tardi.");
            }
        }
    }

    /**
     * Gestisce i messaggi di testo ricevuti
     */
    private void handleMessage(Update update) {
        String messageText = update.getMessage().getText();
        String chatId = update.getMessage().getChatId().toString();
        Long userId = update.getMessage().getFrom().getId();

        System.out.println("📩 Comando ricevuto: " + messageText + " da user " + userId);

        if (messageText.startsWith("/start")) {
            sendWelcomeMessage(chatId);
        } else if (messageText.startsWith("/help")) {
            sendHelpMessage(chatId);
        } else if (messageText.startsWith("/campionati")) {
            sendLeagueSelection(chatId);
        } else if (messageText.startsWith("/preferiti")) {
            sendUserPreferences(chatId, userId);
        } else if (messageText.startsWith("/stats")) {
            sendUserStats(chatId, userId);
        } else if (messageText.startsWith("/oggi")) {
            sendTodayMatches(chatId, userId);
        } else if (messageText.startsWith("/cerca ")) {
            String playerName = messageText.substring(7).trim();
            if (!playerName.isEmpty()) {
                sendPlayerSearch(chatId, userId, playerName);
            } else {
                sendMessage(chatId, "❌ Usa: /cerca Nome Cognome");
            }
        } else if (messageText.startsWith("/squadre")) {
            sendTeamsMenu(chatId);
        } else {
            sendMessage(chatId, "❌ Comando non riconosciuto. Usa /help per vedere i comandi disponibili.");
        }
    }

    /**
     * Gestisce le callback query dai bottoni inline
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
        Long userId = update.getCallbackQuery().getFrom().getId();

        System.out.println("🔘 Callback ricevuta: " + callbackData + " da user " + userId);

        if (callbackData.startsWith("league_")) {
            String leagueCode = callbackData.replace("league_", "");
            userSelectedLeague.put(userId, leagueCode);
            databaseManager.setPreferredLeague(userId, leagueCode);
            sendLeagueOptions(chatId, leagueCode);
        } else if (callbackData.startsWith("standings_")) {
            String leagueCode = callbackData.replace("standings_", "");
            databaseManager.logQuery(userId, "STANDINGS", leagueCode);
            sendStandings(chatId, leagueCode);
        } else if (callbackData.startsWith("matches_")) {
            String leagueCode = callbackData.replace("matches_", "");
            databaseManager.logQuery(userId, "MATCHES", leagueCode);
            sendMatches(chatId, leagueCode);
        } else if (callbackData.startsWith("topscorers_")) {
            String leagueCode = callbackData.replace("topscorers_", "");
            databaseManager.logQuery(userId, "TOPSCORERS", leagueCode);
            sendTopScorers(chatId, leagueCode);
        } else if (callbackData.startsWith("team_")) {
            int teamId = Integer.parseInt(callbackData.replace("team_", ""));
            databaseManager.logQuery(userId, "TEAM_INFO", String.valueOf(teamId));
            sendTeamInfo(chatId, teamId);
        } else if (callbackData.equals("back_leagues")) {
            sendLeagueSelection(chatId);
        } else if (callbackData.equals("back_teams")) {
            sendTeamsMenu(chatId);
        }
    }

    private void sendWelcomeMessage(String chatId) {
        String welcomeText = """
            ⚽ Benvenuto al Football Bot! ⚽
            
            Qui puoi consultare informazioni aggiornate su:
            • 📊 Classifiche dei principali campionati
            • ⚽ Partite in programma
            • 🥇 Classifica marcatori
            
            Usa /campionati per iniziare o /help per maggiori informazioni.
            """;
        sendMessage(chatId, welcomeText);
    }

    private void sendHelpMessage(String chatId) {
        String helpText = """
            📋 COMANDI DISPONIBILI:
            
            /start - Messaggio di benvenuto
            /help - Mostra questo messaggio
            /campionati - Seleziona un campionato
            /squadre - Info dettagliate sulle squadre
            /oggi - Partite di oggi
            /cerca [nome] - Cerca un giocatore
            /preferiti - Mostra le tue preferenze
            /stats - Visualizza le tue statistiche
            
            📌 ESEMPI:
            /cerca Cristiano Ronaldo
            /cerca Lautaro Martinez
            
            💡 I dati vengono salvati in cache per velocizzare le risposte!
            """;
        sendMessage(chatId, helpText);
    }

    private void sendLeagueSelection(String chatId) {
        SendMessage message = new SendMessage(chatId, "🏆 Seleziona un campionato:");

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        keyboard.add(createKeyboardRow("🇮🇹 Serie A", "league_SA"));
        keyboard.add(createKeyboardRow("🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League", "league_PL"));
        keyboard.add(createKeyboardRow("🇪🇸 La Liga", "league_PD"));
        keyboard.add(createKeyboardRow("🇩🇪 Bundesliga", "league_BL1"));
        keyboard.add(createKeyboardRow("🇫🇷 Ligue 1", "league_FL1"));
        keyboard.add(createKeyboardRow("🏆 Champions League", "league_CL"));

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup(keyboard);
        message.setReplyMarkup(markupInline);

        executeMessage(message);
    }

    private void sendLeagueOptions(String chatId, String leagueCode) {
        String leagueName = getLeagueName(leagueCode);
        SendMessage message = new SendMessage(chatId, "✅ Hai selezionato: " + leagueName + "\n\n📋 Cosa vuoi vedere?");

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        keyboard.add(createKeyboardRow("📊 Classifica", "standings_" + leagueCode));
        keyboard.add(createKeyboardRow("⚽ Partite", "matches_" + leagueCode));
        keyboard.add(createKeyboardRow("🥇 Capocannonieri", "topscorers_" + leagueCode));
        keyboard.add(createKeyboardRow("⬅️ Torna ai campionati", "back_leagues"));

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup(keyboard);
        message.setReplyMarkup(markupInline);

        executeMessage(message);
    }

    private void sendStandings(String chatId, String leagueCode) {
        sendMessage(chatId, "⏳ Caricamento classifica in corso...");
        String standings = footballAPI.getStandings(leagueCode);
        sendMessage(chatId, standings);
    }

    private void sendMatches(String chatId, String leagueCode) {
        sendMessage(chatId, "⏳ Caricamento partite in corso...");
        String matches = footballAPI.getMatches(leagueCode);
        sendMessage(chatId, matches);
    }

    private void sendTopScorers(String chatId, String leagueCode) {
        sendMessage(chatId, "⏳ Caricamento classifica marcatori...");
        String topScorers = footballAPI.getTopScorers(leagueCode);
        sendMessage(chatId, topScorers);
    }

    private void sendUserPreferences(String chatId, Long userId) {
        StringBuilder prefs = new StringBuilder();
        prefs.append("⚙️ LE TUE PREFERENZE\n\n");

        String preferredLeague = databaseManager.getPreferredLeague(userId);
        if (preferredLeague != null) {
            prefs.append("🏆 Campionato preferito: ").append(getLeagueName(preferredLeague)).append("\n");
        } else {
            prefs.append("🏆 Campionato preferito: Non impostato\n");
        }

        String favoriteTeam = databaseManager.getFavoriteTeam(userId);
        if (favoriteTeam != null) {
            prefs.append("⭐ Squadra preferita: ").append(favoriteTeam).append("\n");
        } else {
            prefs.append("⭐ Squadra preferita: Non impostata\n");
        }

        String mostQueried = databaseManager.getMostQueriedLeague(userId);
        if (mostQueried != null) {
            prefs.append("📊 Campionato più consultato: ").append(getLeagueName(mostQueried)).append("\n");
        }

        prefs.append("\n💡 Usa /campionati per consultare altri dati!");

        sendMessage(chatId, prefs.toString());
    }

    private void sendUserStats(String chatId, Long userId) {
        StringBuilder stats = new StringBuilder();
        stats.append("📈 LE TUE STATISTICHE\n\n");

        List<String> history = databaseManager.getUserQueryHistory(userId, 5);
        if (!history.isEmpty()) {
            stats.append("📜 Ultime 5 ricerche:\n");
            for (String entry : history) {
                stats.append("• ").append(entry).append("\n");
            }
        } else {
            stats.append("📭 Nessuna ricerca effettuata ancora.\n");
        }

        stats.append("\n💡 Usa /campionati per iniziare!");

        sendMessage(chatId, stats.toString());
    }

    private void sendTodayMatches(String chatId, Long userId) {
        sendMessage(chatId, "⏳ Caricamento partite di oggi...");
        databaseManager.logQuery(userId, "TODAY_MATCHES", null);
        String matches = footballAPI.getTodayMatches();
        sendMessage(chatId, matches);
    }

    private void sendPlayerSearch(String chatId, Long userId, String playerName) {
        sendMessage(chatId, "🔍 Ricerca in corso: " + playerName + "...");
        databaseManager.logQuery(userId, "PLAYER_SEARCH", playerName);
        String results = footballAPI.searchPlayer(playerName);
        sendMessage(chatId, results);
    }

    private void sendTeamsMenu(String chatId) {
        SendMessage message = new SendMessage(chatId, "🏟️ Seleziona una squadra per vedere info dettagliate:");

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        // Serie A
        keyboard.add(createKeyboardRow("⚫🔵 Inter", "team_108"));
        keyboard.add(createKeyboardRow("🔴⚫ Milan", "team_98"));
        keyboard.add(createKeyboardRow("⚪⚫ Juventus", "team_109"));
        keyboard.add(createKeyboardRow("🟡🔴 Roma", "team_100"));
        keyboard.add(createKeyboardRow("🔵⚪ Napoli", "team_113"));

        // Premier League
        keyboard.add(createKeyboardRow("🔴 Manchester United", "team_66"));
        keyboard.add(createKeyboardRow("🔵 Manchester City", "team_65"));
        keyboard.add(createKeyboardRow("🔴 Liverpool", "team_64"));
        keyboard.add(createKeyboardRow("🔵 Chelsea", "team_61"));
        keyboard.add(createKeyboardRow("⚪🔴 Arsenal", "team_57"));

        // La Liga
        keyboard.add(createKeyboardRow("⚪ Real Madrid", "team_86"));
        keyboard.add(createKeyboardRow("🔴🔵 Barcelona", "team_81"));
        keyboard.add(createKeyboardRow("🔴⚪ Atletico Madrid", "team_78"));

        // Bundesliga
        keyboard.add(createKeyboardRow("🔴 Bayern Munich", "team_5"));
        keyboard.add(createKeyboardRow("🟡⚫ Borussia Dortmund", "team_4"));

        keyboard.add(createKeyboardRow("⬅️ Menu principale", "back_leagues"));

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup(keyboard);
        message.setReplyMarkup(markupInline);

        executeMessage(message);
    }

    private void sendTeamInfo(String chatId, int teamId) {
        sendMessage(chatId, "⏳ Caricamento informazioni squadra...");
        String teamInfo = footballAPI.getTeamInfo(teamId);
        sendMessage(chatId, teamInfo);
    }

    private InlineKeyboardRow createKeyboardRow(String text, String callbackData) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
        return new InlineKeyboardRow(button);
    }

    private String getLeagueName(String leagueCode) {
        return switch (leagueCode) {
            case "SA" -> "🇮🇹 Serie A";
            case "PL" -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League";
            case "PD" -> "🇪🇸 La Liga";
            case "BL1" -> "🇩🇪 Bundesliga";
            case "FL1" -> "🇫🇷 Ligue 1";
            case "CL" -> "🏆 Champions League";
            default -> "Campionato";
        };
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.err.println("❌ Errore nell'invio del messaggio: " + e.getMessage());
            e.printStackTrace();
        }
    }
}