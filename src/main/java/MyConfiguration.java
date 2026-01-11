package com.basketballbot;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class MyConfiguration {
    private static MyConfiguration instance;
    private Configurations configurations = new Configurations();
    private Configuration config;

    private MyConfiguration() {
        try {
            config = configurations.properties("config.properties");
        } catch (ConfigurationException e) {
            System.err.println("❌ Errore: File config.properties non trovato!");
            System.err.println("Crea un file config.properties nella cartella resources con:");
            System.err.println("BOT_TOKEN=your_telegram_bot_token");
            System.err.println("API_KEY=your_api_basketball_key");
            System.exit(-1);
        }
    }

    public static MyConfiguration getInstance() {
        if (instance == null) {
            instance = new MyConfiguration();
        }
        return instance;
    }

    public String getProperty(String key) {
        return config.getString(key);
    }

    public String getProperty(String key, String defaultValue) {
        return config.getString(key, defaultValue);
    }
}