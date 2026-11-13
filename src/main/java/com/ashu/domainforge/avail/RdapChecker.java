package com.ashu.domainforge.avail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live availability via RDAP (404 = unregistered, 200 = registered).
 *
 * <p>The public {@code rdap.org} redirector rate-limits after ~16 quick requests, so this checker
 * resolves each TLD's own registry server from the IANA RDAP bootstrap file and queries it
 * directly, with a small per-host concurrency cap and a single Retry-After-aware retry on HTTP 429.
 * TLDs with no RDAP service return UNKNOWN (never a guess).
 */
public class RdapChecker implements AvailabilityChecker {
  private static final Logger log = LoggerFactory.getLogger(RdapChecker.class);
  private static final String BOOTSTRAP = "https://data.iana.org/rdap/dns.json";

  private final HttpClient http;
  private final String fallbackBase;
  private final Duration timeout;
  private final ObjectMapper mapper = new ObjectMapper();
  private volatile Map<String, String> tldToBase;
  private final ConcurrentHashMap<String, Semaphore> hostPermits = new ConcurrentHashMap<>();

  public RdapChecker(String fallbackBase, int timeoutMs) {
    this.fallbackBase = fallbackBase;
    this.timeout = Duration.ofMillis(timeoutMs);
    this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  /** Visible for tests. */
  public RdapChecker withBootstrap(Map<String, String> map) {
    this.tldToBase = map;
    return this;
  }

  private Map<String, String> bootstrap() {
    Map<String, String> m = tldToBase;
    if (m != null) return m;
    synchronized (this) {
      if (tldToBase != null) return tldToBase;
      Map<String, String> loaded = new ConcurrentHashMap<>();
      try {
        HttpResponse<String> r = http.send(
            HttpRequest.newBuilder(URI.create(BOOTSTRAP)).timeout(timeout).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 200) {
          for (JsonNode svc : mapper.readTree(r.body()).path("services")) {
            String base = svc.path(1).path(0).asText("");
            if (base.isEmpty()) continue;
            if (!base.endsWith("/")) base += "/";
            for (JsonNode tld : svc.path(0)) loaded.put(tld.asText().toLowerCase(), base);
          }
          log.info("Loaded RDAP bootstrap for {} TLDs", loaded.size());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        log.warn("RDAP bootstrap fetch failed: {}", e.toString());
      }
      if (!loaded.isEmpty()) tldToBase = loaded; // otherwise retry on next call
      return loaded;
    }
  }

  @Override
  public Status check(String domain) {
    String tld = domain.substring(domain.lastIndexOf('.') + 1).toLowerCase();
    String base = bootstrap().get(tld);
    if (base == null) {
      if (tldToBase != null) return Status.UNKNOWN; // bootstrap loaded; this TLD has no RDAP
      base = fallbackBase; // bootstrap unreachable -> degrade to the redirector
    }
    Semaphore permit = hostPermits.computeIfAbsent(URI.create(base).getHost(), h -> new Semaphore(4));
    try {
      permit.acquire();
      try {
        for (int attempt = 0; attempt < 2; attempt++) {
          HttpResponse<Void> r = http.send(
              HttpRequest.newBuilder(URI.create(base + "domain/" + domain.replaceAll("^.*://", "")))
                  .timeout(timeout).header("accept", "application/rdap+json").GET().build(),
              HttpResponse.BodyHandlers.discarding());
          int code = r.statusCode();
          if (code == 404) return Status.AVAILABLE;
          if (code == 200) return Status.TAKEN;
          if (code == 429 && attempt == 0) {
            long wait = r.headers().firstValue("retry-after").map(s -> {
              try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 1L; }
            }).orElse(1L);
            Thread.sleep(Math.min(wait, 2) * 1000);
            continue;
          }
          return Status.UNKNOWN;
        }
        return Status.UNKNOWN;
      } finally {
        permit.release();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Status.UNKNOWN;
    } catch (Exception e) {
      return Status.UNKNOWN;
    }
  }
}
