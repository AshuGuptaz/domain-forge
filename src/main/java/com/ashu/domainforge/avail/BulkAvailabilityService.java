package com.ashu.domainforge.avail;

import com.ashu.domainforge.config.DomainForgeProperties;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * Checks many domains concurrently with a concurrency cap (to be polite to RDAP servers) and a
 * hard overall deadline; anything unfinished by the deadline is reported as UNKNOWN.
 */
@Service
public class BulkAvailabilityService {
  private final CachingChecker checker;
  private final ExecutorService pool = Executors.newFixedThreadPool(32);
  private final Semaphore permits;
  private final int deadlineMs;

  public BulkAvailabilityService(DomainForgeProperties props) {
    DomainForgeProperties.Availability a = props.getAvailability();
    AvailabilityChecker base = "mock".equalsIgnoreCase(a.getMode())
        ? new MockChecker()
        : new RdapChecker(a.getRdapBaseUrl(), a.getTimeoutMs());
    this.checker = new CachingChecker(base, a.getAvailableTtlSeconds() * 1000L, a.getTakenTtlSeconds() * 1000L);
    this.permits = new Semaphore(a.getMaxConcurrent());
    this.deadlineMs = a.getDeadlineMs();
    if (!"mock".equalsIgnoreCase(a.getMode())) {
      pool.submit(() -> checker.check("warmup-domainforge.com")); // loads the RDAP bootstrap off the request path
    }
  }

  public Status checkOne(String domain) {
    return checker.check(domain);
  }

  public Map<String, Status> checkAll(List<String> domains) {
    Map<String, CompletableFuture<Status>> futures = new LinkedHashMap<>();
    for (String d : domains) {
      futures.put(d, CompletableFuture.supplyAsync(() -> {
        try {
          permits.acquire();
          try {
            return checker.check(d);
          } finally {
            permits.release();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Status.UNKNOWN;
        }
      }, pool));
    }
    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMs);
    Map<String, Status> out = new LinkedHashMap<>();
    for (var e : futures.entrySet()) {
      long left = Math.max(1, end - System.nanoTime());
      try {
        out.put(e.getKey(), e.getValue().get(left, TimeUnit.NANOSECONDS));
      } catch (Exception ex) {
        e.getValue().cancel(true);
        out.put(e.getKey(), Status.UNKNOWN);
      }
    }
    return out;
  }

  public CachingChecker cache() {
    return checker;
  }

  @PreDestroy
  void shutdown() {
    pool.shutdownNow();
  }
}
