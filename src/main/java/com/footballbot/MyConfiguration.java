package com.footballbot;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * Classe Singleton per la gestione della configurazione dell'applicazione.
 * Carica le proprietà dal file config.properties nella cartella resources.
 */
public class MyConfiguration {
    private static MyConfiguration instance;
    private Configuration config;

    private MyConfiguration() {
        try {
            Configurations configurations = new Configurations();
            config = configurations.properties("config.properties");
            System.out.println("File di configurazione caricato con successo");
        } catch (ConfigurationException e) {
            System.err.println("Errore: File config.properties non trovato nella cartella resources");
            System.err.println("Assicurati di aver creato il file e configurato BOT_TOKEN e FOOTBALL_API_KEY");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Restituisce l'istanza singleton di MyConfiguration
     */
    public static MyConfiguration getInstance() {
        if (instance == null) {
            instance = new MyConfiguration();
        }
        return instance;
    }

    /**
     * Recupera una proprietà dal file di configurazione
     * @param key La chiave della proprietà da recuperare
     * @return Il valore della proprietà o null se non trovata
     */
    public String getProperty(String key) {
        return config.getString(key);
    }

    /**
     * Recupera una proprietà con valore di default
     * @param key La chiave della proprietà
     * @param defaultValue Il valore di default se la chiave non esiste
     * @return Il valore della proprietà o il default
     */
    public String getProperty(String key, String defaultValue) {
        return config.getString(key, defaultValue);
    }
}