package com.ashu.domainforge.api;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Ring buffer of the last 1024 request latencies per operation; exposes p50/p99. */
@Component
public class LatencyRecorder {
  private static final int N = 1024;

  public static final class Series {
    private final long[] buf = new long[N];
    private final AtomicLong count = new AtomicLong();

    void add(long micros) {
      buf[(int) (count.getAndIncrement() % N)] = micros;
    }

    public synchronized long[] snapshot() {
      int n = (int) Math.min(count.get(), N);
      long[] copy = Arrays.copyOf(buf, n);
      Arrays.sort(copy);
      return copy;
    }

    public long count() {
      return count.get();
    }
  }

  private final Series suggest = new Series();
  private final Series lookalike = new Series();

  public void recordSuggest(long micros) { suggest.add(micros); }
  public void recordLookalike(long micros) { lookalike.add(micros); }

  public java.util.Map<String, Object> summary(String name, Series s) {
    long[] a = s.snapshot();
    var m = new java.util.LinkedHashMap<String, Object>();
    m.put("operation", name);
    m.put("requests", s.count());
    m.put("p50_us", a.length == 0 ? 0 : a[a.length / 2]);
    m.put("p99_us", a.length == 0 ? 0 : a[Math.min(a.length - 1, (int) (a.length * 0.99))]);
    return m;
  }

  public Series suggestSeries() { return suggest; }
  public Series lookalikeSeries() { return lookalike; }
}
