package com.footballbot;

import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Avvio Football Bot...");

        MyConfiguration config = MyConfiguration.getInstance();
        DatabaseManager dbManager = DatabaseManager.getInstance();

        try {
            String botToken = config.getProperty("BOT_TOKEN");

            if (botToken == null || botToken.isEmpty()) {
                System.err.println("ERRORE: BOT_TOKEN non configurato nel file config.properties");
                System.exit(-1);
            }

            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, new FootballBot());

            System.out.println("✅ Football Bot avviato con successo!");
            System.out.println("Il bot è ora online e pronto a ricevere messaggi.");
            System.out.println("Premi CTRL+C per terminare.");

            //Pulizia cache scaduta all'avvio
            dbManager.clearExpiredCache();

            //Aggiungi shutdown hook per chiudere correttamente il database
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nChiusura bot in corso...");
                dbManager.close();
                System.out.println("Bot terminato correttamente.");
            }));

        } catch (TelegramApiException e) {
            System.err.println("❌ Errore durante l'avvio del bot: " + e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }
}