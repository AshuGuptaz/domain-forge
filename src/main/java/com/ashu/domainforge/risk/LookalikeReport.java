package com.ashu.domainforge.risk;

import java.util.List;

public record LookalikeReport(
    String domain, double risk, String level, boolean official, List<Match> matches, List<String> flags) {

  public record Match(String brand, String reason, double score, double vectorSimilarity) {}

  public static LookalikeReport none(String domain, boolean official, List<String> flags) {
    return new LookalikeReport(domain, 0.0, official ? "OFFICIAL" : "NONE", official, List.of(), flags);
  }
}
