package com.ashu.domainforge;

import static org.assertj.core.api.Assertions.assertThat;

import com.ashu.domainforge.avail.CachingChecker;
import com.ashu.domainforge.avail.Status;
import com.ashu.domainforge.embed.NgramEmbedder;
import com.ashu.domainforge.rank.NameScorer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScoringAndEmbeddingTest {

  private final NameScorer scorer = new NameScorer();
  private final NgramEmbedder embedder = new NgramEmbedder();

  @Test
  void embeddingsAreUnitLengthAndDeterministic() {
    float[] a = embedder.embed("paypal");
    float[] b = embedder.embed("paypal");
    assertThat(NgramEmbedder.cosine(a, a)).isBetween(0.999, 1.001);
    assertThat(a).containsExactly(b);
  }

  @Test
  void typoIsCloserThanUnrelatedWord() {
    float[] brand = embedder.embed("paypal");
    assertThat(NgramEmbedder.cosine(brand, embedder.embed("paypa1")))
        .isGreaterThan(NgramEmbedder.cosine(brand, embedder.embed("sunflower")));
  }

  @Test
  void goodNameBeatsAwkwardName() {
    double good = scorer.score("brewly", "com", 1.0, 0).total();
    double bad = scorer.score("xqzt-brw99", "xyz", 0.1, 0).total();
    assertThat(good).isGreaterThan(bad + 30);
  }

  @Test
  void riskPenalisesScore() {
    double clean = scorer.score("sproutly", "com", 1.0, 0).total();
    double risky = scorer.score("sproutly", "com", 1.0, 0.9).total();
    assertThat(risky).isLessThan(clean - 30);
  }

  @Test
  void consonantClustersHurtPronounceability() {
    assertThat(NameScorer.pronounceability("strengths")).isLessThan(NameScorer.pronounceability("banora"));
  }

  @Test
  void cacheAvoidsRepeatLookupsAndSkipsUnknown() {
    AtomicInteger calls = new AtomicInteger();
    CachingChecker c = new CachingChecker(d -> {
      calls.incrementAndGet();
      return d.startsWith("flaky") ? Status.UNKNOWN : Status.AVAILABLE;
    }, 60_000, 60_000);
    c.check("a.com");
    c.check("a.com");
    assertThat(calls.get()).isEqualTo(1);
    c.check("flaky.com");
    c.check("flaky.com");
    assertThat(calls.get()).isEqualTo(3);
    assertThat(c.hits()).isEqualTo(1);
  }
}
