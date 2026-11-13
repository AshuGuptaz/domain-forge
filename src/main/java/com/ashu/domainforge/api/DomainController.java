package com.ashu.domainforge.api;

import com.ashu.domainforge.api.Dtos.*;
import com.ashu.domainforge.avail.BulkAvailabilityService;
import com.ashu.domainforge.gen.AiAdvisor;
import com.ashu.domainforge.index.SemanticSearch;
import com.ashu.domainforge.risk.BrandCatalog;
import com.ashu.domainforge.risk.LookalikeDetector;
import com.ashu.domainforge.risk.LookalikeReport;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DomainController {
  private final SuggestionService suggestions;
  private final LookalikeDetector detector;
  private final BulkAvailabilityService availability;
  private final AiAdvisor advisor;
  private final BrandCatalog catalog;
  private final LatencyRecorder latency;
  private final SemanticSearch semantic;

  public DomainController(SuggestionService suggestions, LookalikeDetector detector, BulkAvailabilityService availability,
      AiAdvisor advisor, BrandCatalog catalog, LatencyRecorder latency, SemanticSearch semantic) {
    this.suggestions = suggestions;
    this.detector = detector;
    this.availability = availability;
    this.advisor = advisor;
    this.catalog = catalog;
    this.latency = latency;
    this.semantic = semantic;
  }

  @PostMapping("/suggest")
  public SuggestResponse suggest(@Valid @RequestBody SuggestRequest req) {
    long t = System.nanoTime();
    SuggestResponse r = suggestions.suggest(req);
    latency.recordSuggest((System.nanoTime() - t) / 1000);
    return r;
  }

  @GetMapping("/check")
  public ResponseEntity<?> check(@RequestParam String domain) {
    String d = domain.trim().toLowerCase();
    if (!d.matches("^[a-z0-9-]{1,63}(\\.[a-z0-9-]{1,63})+$")) {
      return ResponseEntity.badRequest().body(Map.of("error", "invalid domain: " + domain));
    }
    return ResponseEntity.ok(new CheckResponse(d, availability.checkOne(d), detector.analyze(d)));
  }

  @PostMapping("/lookalike")
  public LookalikeResponse lookalike(@Valid @RequestBody LookalikeRequest req) {
    long t = System.nanoTime();
    LookalikeReport report = detector.analyze(req.domain());
    String ai = report.risk() >= 0.5 ? advisor.explainRisk(report).orElse(null) : null;
    latency.recordLookalike((System.nanoTime() - t) / 1000);
    return new LookalikeResponse(report, ai);
  }

  /** Meaning-based word search over the Vex index (shows what the neural embeddings retrieve). */
  @GetMapping("/semantic")
  public ResponseEntity<?> semantic(@RequestParam String q, @RequestParam(defaultValue = "10") int k) {
    if (!semantic.ready()) return ResponseEntity.status(503).body(Map.of("error", "semantic index not ready"));
    return ResponseEntity.ok(semantic.relatedWords(q, Math.max(1, Math.min(k, 30))));
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("brandsIndexedInVex", catalog.size());
    m.put("llmEnabled", advisor.enabled());
    m.put("semanticSearchReady", semantic.ready());
    m.put("wordsIndexedInVex", semantic.size());
    m.put("cache", Map.of("entries", availability.cache().size(), "hits", availability.cache().hits(), "misses", availability.cache().misses()));
    m.put("latency", List.of(latency.summary("suggest", latency.suggestSeries()), latency.summary("lookalike", latency.lookalikeSeries())));
    return m;
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }
}
