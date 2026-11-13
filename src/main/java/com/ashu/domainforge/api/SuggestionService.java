package com.ashu.domainforge.api;

import com.ashu.domainforge.api.Dtos.SuggestRequest;
import com.ashu.domainforge.api.Dtos.SuggestResponse;
import com.ashu.domainforge.api.Dtos.SuggestStats;
import com.ashu.domainforge.api.Dtos.Suggestion;
import com.ashu.domainforge.avail.BulkAvailabilityService;
import com.ashu.domainforge.avail.Status;
import com.ashu.domainforge.embed.NgramEmbedder;
import com.ashu.domainforge.gen.AiAdvisor;
import com.ashu.domainforge.gen.Lexicon;
import com.ashu.domainforge.gen.LlmNameGenerator;
import com.ashu.domainforge.gen.RuleBasedGenerator;
import com.ashu.domainforge.index.SemanticSearch;
import com.ashu.domainforge.index.VexIndex;
import com.ashu.domainforge.rank.NameScorer;
import com.ashu.domainforge.risk.LookalikeDetector;
import com.ashu.domainforge.risk.LookalikeReport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Pipeline: keywords -> candidates (rules + LLM) -> score + lookalike risk -> concurrent
 * availability -> diversity filter -> optional LLM rationale.
 */
@Service
public class SuggestionService {
  private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,18}[a-z0-9])?$");
  private static final List<String> DEFAULT_TLDS = List.of("com", "ai", "in", "net", "org", "app");
  private static final double DIVERSITY_COSINE = 0.88;

  private final Lexicon lexicon;
  private final RuleBasedGenerator rules;
  private final LlmNameGenerator llm;
  private final NameScorer scorer;
  private final LookalikeDetector detector;
  private final BulkAvailabilityService availability;
  private final NgramEmbedder embedder;
  private final AiAdvisor advisor;
  private final SemanticSearch semantic;

  public SuggestionService(Lexicon lexicon, RuleBasedGenerator rules, LlmNameGenerator llm, NameScorer scorer,
      LookalikeDetector detector, BulkAvailabilityService availability, NgramEmbedder embedder, AiAdvisor advisor, SemanticSearch semantic) {
    this.lexicon = lexicon;
    this.rules = rules;
    this.llm = llm;
    this.scorer = scorer;
    this.detector = detector;
    this.availability = availability;
    this.embedder = embedder;
    this.advisor = advisor;
    this.semantic = semantic;
  }

  private record Cand(String label, String tld, double rel, NameScorer.Breakdown b, LookalikeReport risk) {
    String domain() { return label + "." + tld; }
  }

  public SuggestResponse suggest(SuggestRequest req) {
    long t0 = System.nanoTime();
    int count = req.count() == null ? 10 : req.count();
    List<String> tlds = req.tlds() == null || req.tlds().isEmpty() ? DEFAULT_TLDS
        : req.tlds().stream().map(t -> t.toLowerCase().replace(".", "")).distinct().toList();
    boolean includeTaken = Boolean.TRUE.equals(req.includeTaken());
    boolean useAi = req.ai() == null || req.ai();

    List<String> keywords = lexicon.keywords(req.description());
    List<String> generators = new ArrayList<>(List.of(rules.name()));
    Set<String> labels = new LinkedHashSet<>();

    // Neural step: pick the keywords that carry the meaning, then ask Vex for their nearest words.
    List<String> core = keywords;
    List<String> semanticWords = new ArrayList<>();
    if (semantic.ready() && !keywords.isEmpty()) {
      Map<String, Double> salience = new LinkedHashMap<>();
      for (String k : keywords) salience.put(k, semantic.similarity(k, req.description()));
      core = keywords.stream()
          .sorted(Comparator.comparingDouble((String k) -> -salience.get(k)))
          .limit(3).toList();
      Set<String> seen = new LinkedHashSet<>();
      for (String k : core) {
        for (VexIndex.Neighbor n : semantic.relatedWords(k, 8)) {
          if (n.similarity() >= 0.45 && !keywords.contains(n.label())) seen.add(n.label());
        }
      }
      for (VexIndex.Neighbor n : semantic.relatedWords(req.description(), 6)) {
        if (n.similarity() >= 0.40 && !keywords.contains(n.label())) seen.add(n.label());
      }
      semanticWords.addAll(seen.stream().limit(14).toList());
    }
    labels.addAll(rules.generate(req.description(), core, semanticWords, 400));
    if (useAi && llm.enabled()) {
      List<String> fromLlm = llm.generate(req.description(), core, 40);
      if (!fromLlm.isEmpty()) {
        generators.add(llm.name());
        labels.addAll(fromLlm);
      }
    }
    labels.removeIf(l -> l == null || !LABEL.matcher(l).matches());
    int generated = labels.size();

    Set<String> related = new LinkedHashSet<>(core);
    related.addAll(semanticWords);
    for (String k : core) related.addAll(lexicon.relatedTo(k));
    Set<String> coreSet = new LinkedHashSet<>(core);
    Map<String, float[]> relatedVecs = new LinkedHashMap<>();
    for (String r : related) relatedVecs.put(r, embedder.embed(r));

    List<Cand> pool = new ArrayList<>();
    for (String label : labels) {
      double rel = relevance(label, coreSet, related, relatedVecs);
      for (String tld : tlds) {
        LookalikeReport risk = detector.analyze(label + "." + tld);
        pool.add(new Cand(label, tld, rel, scorer.score(label, tld, rel, risk.risk()), risk));
      }
    }
    // 1. Rank labels (best score across the requested TLDs), then drop near-duplicate labels BEFORE
    //    spending any network lookups on them.
    Map<String, List<Cand>> byLabel = new LinkedHashMap<>();
    for (Cand c : pool) byLabel.computeIfAbsent(c.label(), k -> new ArrayList<>()).add(c);
    for (List<Cand> l : byLabel.values()) l.sort(Comparator.comparingDouble((Cand c) -> c.b().total()).reversed());
    List<List<Cand>> ranked = new ArrayList<>(byLabel.values());
    ranked.sort(Comparator.comparingDouble((List<Cand> l) -> l.get(0).b().total()).reversed());

    // Neural re-rank: blend meaning-similarity (MiniLM cosine to the description) into relevance
    // for the top labels, then re-sort. Skipped gracefully if the model is not loaded.
    if (semantic.ready()) {
      List<List<Cand>> top = new ArrayList<>(ranked.subList(0, Math.min(ranked.size(), Math.max(60, count * 8))));
      for (int i = 0; i < top.size(); i++) {
        List<Cand> l = top.get(i);
        double sim = Math.max(0, semantic.similarity(l.get(0).label(), req.description()));
        List<Cand> rescored = new ArrayList<>();
        for (Cand c : l) {
          double rel = Math.min(1.0, 0.6 * c.rel() + 0.4 * Math.min(1.0, sim * 2.2));
          rescored.add(new Cand(c.label(), c.tld(), rel, scorer.score(c.label(), c.tld(), rel, c.risk().risk()), c.risk()));
        }
        rescored.sort(Comparator.comparingDouble((Cand c) -> c.b().total()).reversed());
        top.set(i, rescored);
      }
      top.sort(Comparator.comparingDouble((List<Cand> l) -> l.get(0).b().total()).reversed());
      ranked = top;
    }

    int wantLabels = Math.min(ranked.size(), count * 3);
    List<List<Cand>> diverse = new ArrayList<>();
    List<float[]> diverseVecs = new ArrayList<>();
    for (List<Cand> l : ranked) {
      float[] v = embedder.embed(l.get(0).label());
      boolean tooSimilar = false;
      for (float[] pv : diverseVecs) {
        if (NgramEmbedder.cosine(v, pv) > DIVERSITY_COSINE) { tooSimilar = true; break; }
      }
      if (tooSimilar) continue;
      diverse.add(l);
      diverseVecs.add(v);
      if (diverse.size() >= wantLabels) break;
    }

    // 2. Check TLDs in rounds: each label's best-scoring TLD first; only labels whose domain was
    //    taken move on to their next TLD. Stops as soon as enough names are confirmed available.
    Map<String, Status> status = new LinkedHashMap<>();
    Map<String, Cand> chosen = new LinkedHashMap<>(); // label -> candidate shown
    int checked = 0;
    for (int round = 0; round < tlds.size(); round++) {
      List<Cand> batch = new ArrayList<>();
      for (List<Cand> l : diverse) {
        if (chosen.containsKey(l.get(0).label()) && status.get(chosen.get(l.get(0).label()).domain()) == Status.AVAILABLE) continue;
        if (round < l.size()) batch.add(l.get(round));
      }
      if (batch.isEmpty()) break;
      Map<String, Status> res = availability.checkAll(batch.stream().map(Cand::domain).toList());
      checked += batch.size();
      for (Cand c : batch) {
        Status st = res.getOrDefault(c.domain(), Status.UNKNOWN);
        status.put(c.domain(), st);
        Cand cur = chosen.get(c.label());
        boolean curAvail = cur != null && status.get(cur.domain()) == Status.AVAILABLE;
        if (!curAvail && (st == Status.AVAILABLE || cur == null || st == Status.UNKNOWN && status.get(cur.domain()) == Status.TAKEN)) {
          chosen.put(c.label(), c);
        }
      }
      long availableNow = chosen.values().stream().filter(c -> status.get(c.domain()) == Status.AVAILABLE).count();
      if (availableNow >= count) break;
    }

    List<Cand> picked = new ArrayList<>();
    for (List<Cand> l : diverse) {
      Cand c = chosen.get(l.get(0).label());
      if (c == null) continue;
      Status st = status.getOrDefault(c.domain(), Status.UNKNOWN);
      if (st == Status.TAKEN && !includeTaken) continue;
      picked.add(c);
    }
    picked.sort(Comparator
        .comparing((Cand c) -> status.get(c.domain()) == Status.AVAILABLE ? 0 : 1)
        .thenComparing(Comparator.comparingDouble((Cand c) -> c.b().total()).reversed()));
    if (picked.size() > count) picked = new ArrayList<>(picked.subList(0, count));

    Map<String, String> why = Map.of();
    if (useAi && advisor.enabled() && !picked.isEmpty()) {
      why = advisor.explainNames(req.description(), picked.stream().map(Cand::domain).toList());
    }
    List<Suggestion> out = new ArrayList<>();
    for (Cand c : picked) {
      out.add(new Suggestion(c.domain(), c.label(), c.tld(), status.getOrDefault(c.domain(), Status.UNKNOWN),
          c.b().total(), c.b(), c.risk().level(), why.get(c.domain())));
    }
    long tookMs = (System.nanoTime() - t0) / 1_000_000;
    return new SuggestResponse(req.description(), keywords, out,
        new SuggestStats(generated, pool.size(), checked, tookMs, generators, !why.isEmpty(), semantic.ready(), semanticWords));
  }

  private double relevance(String label, Set<String> core, Set<String> related, Map<String, float[]> vecs) {
    for (String r : core) if (r.length() >= 3 && label.contains(r)) return 0.9;
    for (String r : related) if (r.length() >= 3 && label.contains(r)) return 0.75;
    if (vecs.isEmpty()) return 0.3;
    float[] v = embedder.embed(label);
    double best = 0;
    for (float[] rv : vecs.values()) best = Math.max(best, NgramEmbedder.cosine(v, rv));
    return Math.min(0.8, best);
  }
}
