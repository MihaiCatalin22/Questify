package com.questify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@ConfigurationProperties(prefix = "coach")
public class CoachProperties {

    private String runtime = "ollama";
    private String model = "smollm2:1.7b";
    private long timeoutMs = 90000;
    private int maxOutputTokens = 220;
    private double temperature = 0.3d;
    private boolean retryEnabled = true;
    private int maxRetries = 1;
    private boolean debugLogging = false;
    private String schemaVersion = "v1";
    private String runtimeBaseUrl = "http://ollama:11434";

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getRuntimeBaseUrl() {
        return runtimeBaseUrl;
    }

    public void setRuntimeBaseUrl(String runtimeBaseUrl) {
        this.runtimeBaseUrl = runtimeBaseUrl;
    }

    public String normalizedRuntime() {
        return runtime == null ? "ollama" : runtime.trim().toLowerCase(Locale.ROOT);
    }
}
