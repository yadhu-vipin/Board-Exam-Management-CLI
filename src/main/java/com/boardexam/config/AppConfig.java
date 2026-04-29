package com.boardexam.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Centralised configuration loader.
 * Reads from .env file — credentials NEVER hard-coded.
 * Singleton pattern ensures one config load per JVM run.
 */
public final class AppConfig {

    private static AppConfig instance;
    private final Dotenv dotenv;

    private AppConfig() {
        this.dotenv = Dotenv.configure().ignoreIfMissing().load();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    public String get(String key) {
        String val = dotenv.get(key);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: " + key +
                "\nPlease set it in your .env file or OS environment.");
        }
        return val;
    }

    public String getOrDefault(String key, String defaultValue) {
        try { return get(key); } catch (IllegalStateException e) { return defaultValue; }
    }

    public String dbUrl()      { return get("DB_URL"); }
    public String dbUser()     { return get("DB_USER"); }
    public String dbPassword() { return get("DB_PASSWORD"); }
    public int    poolSize()   { return Integer.parseInt(getOrDefault("DB_POOL_SIZE", "10")); }
    public String appEnv()     { return getOrDefault("APP_ENV", "production"); }
    public boolean isDev()     { return "development".equalsIgnoreCase(appEnv()); }
}
