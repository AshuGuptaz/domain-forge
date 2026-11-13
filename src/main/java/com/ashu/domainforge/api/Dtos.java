package com.ashu.domainforge.api;

import com.ashu.domainforge.avail.Status;
import com.ashu.domainforge.rank.NameScorer;
import com.ashu.domainforge.risk.LookalikeReport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Dtos {
  private Dtos() {}

  public record SuggestRequest(
      @NotBlank @Size(max = 500) String description,
      List<@Size(min = 2, max = 10) String> tlds,
      @Min(1) @Max(30) Integer count,
      Boolean includeTaken,
      Boolean ai) {}

  public record Suggestion(
      String domain,
      String label,
      String tld,
      Status status,
      double score,
      NameScorer.Breakdown breakdown,
      String risk,
      String why) {}

  public record SuggestStats(int generated, int scored, int availabilityChecked, long tookMs, List<String> generators, boolean aiExplanations, boolean semanticSearch, List<String> semanticWords) {}

  public record SuggestResponse(String query, List<String> keywords, List<Suggestion> suggestions, SuggestStats stats) {}

  public record CheckResponse(String domain, Status status, LookalikeReport risk) {}

  public record LookalikeRequest(@NotBlank @Size(max = 253) String domain) {}

  public record LookalikeResponse(LookalikeReport report, String aiExplanation) {}
}
