package com.ashu.domainforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All tunables, bound from {@code domainforge.*} in application.yml / env vars. */
@ConfigurationProperties(prefix = "domainforge")
public class DomainForgeProperties {

  private final Availability availability = new Availability();
  private final Llm llm = new Llm();
  private final Embedding embedding = new Embedding();

  public Embedding getEmbedding() {
    return embedding;
  }

  public Availability getAvailability() {
    return availability;
  }

  public Llm getLlm() {
    return llm;
  }

  public static class Availability {
    /** "rdap" (live lookups) or "mock" (deterministic, offline). */
    private String mode = "rdap";
    private String rdapBaseUrl = "https://rdap.org/domain/";
    private int timeoutMs = 4000;
    private int maxConcurrent = 8;
    private int deadlineMs = 8000;
    private int availableTtlSeconds = 300;
    private int takenTtlSeconds = 3600;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getRdapBaseUrl() { return rdapBaseUrl; }
    public void setRdapBaseUrl(String v) { this.rdapBaseUrl = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int v) { this.maxConcurrent = v; }
    public int getDeadlineMs() { return deadlineMs; }
    public void setDeadlineMs(int v) { this.deadlineMs = v; }
    public int getAvailableTtlSeconds() { return availableTtlSeconds; }
    public void setAvailableTtlSeconds(int v) { this.availableTtlSeconds = v; }
    public int getTakenTtlSeconds() { return takenTtlSeconds; }
    public void setTakenTtlSeconds(int v) { this.takenTtlSeconds = v; }
  }

  public static class Embedding {
    /** "auto" = download/load the MiniLM model; "off" = lexical features only. */
    private String mode = "auto";
    private String dir = "~/.cache/domain-forge/minilm";

    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public String getDir() { return dir; }
    public void setDir(String v) { this.dir = v; }
  }

  public static class Llm {
    /** Optional. Local servers such as Ollama need none; hosted ones (e.g. Groq) do. */
    private String apiKey = "";
    private String model = "";
    private int timeoutMs = 8000;
    /** Any OpenAI-compatible endpoint. Default: a local Ollama server. */
    private String baseUrl = "http://localhost:11434/v1";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
  }
}
