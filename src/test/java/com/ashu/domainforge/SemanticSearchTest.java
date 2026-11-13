package com.ashu.domainforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ashu.domainforge.index.SemanticSearch;
import com.ashu.domainforge.index.VexIndex;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the REAL neural model (MiniLM via ONNX Runtime) end to end through Vex. Skipped automatically
 * if the model could not be downloaded (e.g. offline CI).
 */
@SpringBootTest(properties = "domainforge.availability.mode=mock")
class SemanticSearchTest {

  @Autowired SemanticSearch semantic;

  @BeforeAll
  static void noop() {}

  private void requireModel() {
    semantic.load(); // idempotent: waits for / performs init
    assumeTrue(semantic.ready(), "MiniLM model unavailable, skipping");
  }

  @Test
  void coffeeShopRetrievesCoffeeVocabularyByMeaning() {
    requireModel();
    List<String> words = semantic.relatedWords("a specialty coffee shop", 12).stream().map(VexIndex.Neighbor::label).toList();
    assertThat(words).containsAnyOf("brew", "roast", "bean", "espresso", "cafe", "mocha", "latte");
  }

  @Test
  void unrelatedDomainsGetDifferentVocabulary() {
    requireModel();
    List<String> fitness = semantic.relatedWords("personal trainer and gym", 12).stream().map(VexIndex.Neighbor::label).toList();
    assertThat(fitness).containsAnyOf("fit", "flex", "lift", "strong", "sprint", "stride", "vital");
    assertThat(fitness).doesNotContain("espresso", "latte");
  }

  @Test
  void meaningSimilarityBeatsSpellingSimilarity() {
    requireModel();
    // "puppy" and "dog" share no letters in order; "puppet" is spelled almost the same as "puppy".
    assertThat(semantic.similarity("puppy", "dog")).isGreaterThan(semantic.similarity("puppy", "puppet"));
  }
}
