package com.ashu.domainforge.avail;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** TTL cache decorator. UNKNOWN results are never cached so transient failures retry. */
public class CachingChecker implements AvailabilityChecker {
  private record Entry(Status status, long expiresAtMs) {}

  private final AvailabilityChecker delegate;
  private final long availableTtlMs;
  private final long takenTtlMs;
  private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();

  public CachingChecker(AvailabilityChecker delegate, long availableTtlMs, long takenTtlMs) {
    this.delegate = delegate;
    this.availableTtlMs = availableTtlMs;
    this.takenTtlMs = takenTtlMs;
  }

  @Override
  public Status check(String domain) {
    long now = System.currentTimeMillis();
    Entry e = cache.get(domain);
    if (e != null && e.expiresAtMs > now) {
      hits.incrementAndGet();
      return e.status;
    }
    misses.incrementAndGet();
    Status s = delegate.check(domain);
    if (s != Status.UNKNOWN) {
      cache.put(domain, new Entry(s, now + (s == Status.AVAILABLE ? availableTtlMs : takenTtlMs)));
    }
    return s;
  }

  public long hits() { return hits.get(); }
  public long misses() { return misses.get(); }
  public int size() { return cache.size(); }
}
