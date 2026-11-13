package com.ashu.domainforge.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** dnstwist-style synthetic attack domains, labelled by attack category. */
public final class TyposquatGenerator {

  public record Sample(String domain, String category, String brandDomain) {}

  private static final Map<Character, String> KEYS = Map.ofEntries(
      Map.entry('q', "wa"), Map.entry('w', "qes"), Map.entry('e', "wrd"), Map.entry('r', "etf"),
      Map.entry('t', "ryg"), Map.entry('y', "tuh"), Map.entry('u', "yij"), Map.entry('i', "uok"),
      Map.entry('o', "ipl"), Map.entry('p', "ol"), Map.entry('a', "qsz"), Map.entry('s', "awdx"),
      Map.entry('d', "serf"), Map.entry('f', "drtg"), Map.entry('g', "ftyh"), Map.entry('h', "gyuj"),
      Map.entry('j', "huik"), Map.entry('k', "jiol"), Map.entry('l', "kop"), Map.entry('z', "asx"),
      Map.entry('x', "zsdc"), Map.entry('c', "xdfv"), Map.entry('v', "cfgb"), Map.entry('b', "vghn"),
      Map.entry('n', "bhjm"), Map.entry('m', "njk"));

  private static final Map<Character, Character> HOMOGLYPH = Map.of(
      'a', 'а', 'e', 'е', 'o', 'о', 'p', 'р', 'c', 'с', 'x', 'х', 'y', 'у', 'i', 'і');

  // keywords the detector KNOWS about (its PHISH_WORDS) vs ones it has never seen
  static final List<String> KNOWN_KW = List.of("login", "secure", "verify", "support", "account", "billing", "update");
  static final List<String> UNKNOWN_KW = List.of("rewards", "giveaway", "helpdesk", "signon", "mobileapp", "offers", "claim");
  static final List<String> TLDS = List.of("net", "co", "biz", "info", "xyz", "online");

  private TyposquatGenerator() {}

  public static List<Sample> generate(List<String> brandDomains, long seed) {
    Random rnd = new Random(seed);
    List<Sample> out = new ArrayList<>();
    for (String bd : brandDomains) {
      int dot = bd.indexOf('.');
      String label = bd.substring(0, dot);
      String tld = bd.substring(dot + 1);
      if (label.length() < 5 || label.contains("-")) continue;
      int n = label.length();

      int i = 1 + rnd.nextInt(n - 1);
      out.add(new Sample(label.substring(0, i) + label.substring(i + 1) + "." + tld, "omission", bd));

      i = rnd.nextInt(n);
      out.add(new Sample(label.substring(0, i + 1) + label.charAt(i) + label.substring(i + 1) + "." + tld, "repetition", bd));

      for (int t = 0; t < 8; t++) {
        i = rnd.nextInt(n - 1);
        if (label.charAt(i) != label.charAt(i + 1)) {
          char[] c = label.toCharArray();
          char tmp = c[i]; c[i] = c[i + 1]; c[i + 1] = tmp;
          out.add(new Sample(new String(c) + "." + tld, "transposition", bd));
          break;
        }
      }

      i = rnd.nextInt(n);
      String near = KEYS.get(label.charAt(i));
      if (near != null) {
        char r = near.charAt(rnd.nextInt(near.length()));
        out.add(new Sample(label.substring(0, i) + r + label.substring(i + 1) + "." + tld, "key-substitution", bd));
      }

      i = rnd.nextInt(n);
      near = KEYS.get(label.charAt(i));
      if (near != null) {
        char r = near.charAt(rnd.nextInt(near.length()));
        out.add(new Sample(label.substring(0, i) + r + label.substring(i) + "." + tld, "insertion", bd));
      }

      for (int t = 0; t < n; t++) {
        i = rnd.nextInt(n);
        Character g = HOMOGLYPH.get(label.charAt(i));
        if (g != null) {
          out.add(new Sample(label.substring(0, i) + g + label.substring(i + 1) + "." + tld, "unicode-homoglyph", bd));
          break;
        }
      }

      String digit = label.replace('o', '0');
      if (!digit.equals(label)) out.add(new Sample(digit + "." + tld, "digit-swap", bd));
      else if (label.contains("l")) out.add(new Sample(label.replace('l', '1') + "." + tld, "digit-swap", bd));

      if (label.contains("m")) out.add(new Sample(label.replaceFirst("m", "rn") + "." + tld, "rn-for-m", bd));

      i = 2 + rnd.nextInt(Math.max(1, n - 3));
      out.add(new Sample(label.substring(0, i) + "-" + label.substring(i) + "." + tld, "hyphen-split", bd));

      String k = KNOWN_KW.get(rnd.nextInt(KNOWN_KW.size()));
      out.add(new Sample(label + "-" + k + "." + tld, "brand+known-keyword", bd));
      out.add(new Sample(k + label + "." + tld, "brand+known-keyword", bd));

      k = UNKNOWN_KW.get(rnd.nextInt(UNKNOWN_KW.size()));
      out.add(new Sample(label + "-" + k + "." + tld, "brand+unseen-keyword", bd));

      String other = TLDS.get(rnd.nextInt(TLDS.size()));
      if (!other.equals(tld)) out.add(new Sample(label + "." + other, "tld-swap", bd));
    }
    return out;
  }
}
