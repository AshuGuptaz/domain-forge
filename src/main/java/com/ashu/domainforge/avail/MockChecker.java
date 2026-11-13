package com.ashu.domainforge.avail;

import java.util.Set;

/** Deterministic offline checker: ~55% of names look taken, short dictionary words always taken. */
public class MockChecker implements AvailabilityChecker {
  private static final Set<String> ALWAYS_TAKEN = Set.of("coffee", "tea", "shop", "store", "brew", "hub", "bean", "cafe");

  @Override
  public Status check(String domain) {
    String label = domain.substring(0, domain.indexOf('.'));
    if (ALWAYS_TAKEN.contains(label)) return Status.TAKEN;
    int h = Math.abs(domain.hashCode() % 100);
    return h < 55 ? Status.TAKEN : Status.AVAILABLE;
  }
}
