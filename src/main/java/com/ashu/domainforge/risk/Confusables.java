package com.ashu.domainforge.risk;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Homoglyph / look-alike normalisation ("skeleton") used to defeat visual obfuscation. */
public final class Confusables {

  private Confusables() {}

  private static final Map<Character, String> MAP = Map.ofEntries(
      // Cyrillic
      Map.entry('а', "a"), Map.entry('е', "e"), Map.entry('о', "o"), Map.entry('р', "p"),
      Map.entry('с', "c"), Map.entry('х', "x"), Map.entry('у', "y"), Map.entry('і', "i"),
      Map.entry('ј', "j"), Map.entry('ѕ', "s"), Map.entry('ԁ', "d"), Map.entry('ӏ', "l"),
      Map.entry('һ', "h"), Map.entry('ԛ', "q"),
      // Greek
      Map.entry('ο', "o"), Map.entry('α', "a"), Map.entry('ν', "v"), Map.entry('ρ', "p"),
      Map.entry('ι', "i"), Map.entry('κ', "k"), Map.entry('τ', "t"),
      // digits that pass for letters
      Map.entry('0', "o"), Map.entry('3', "e"), Map.entry('5', "s"), Map.entry('4', "a"),
      Map.entry('$', "s"));

  /** True if the string mixes Latin letters with letters from another script. */
  public static boolean mixedScript(String s) {
    boolean latin = false;
    boolean other = false;
    for (char c : s.toCharArray()) {
      if (!Character.isLetter(c)) continue;
      if (Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) latin = true;
      else other = true;
    }
    return latin && other;
  }

  public static boolean hasNonAscii(String s) {
    for (char c : s.toCharArray()) if (c > 127) return true;
    return false;
  }

  /**
   * Returns the distinct skeleton variants of a label. The digit '1' is ambiguous (l or i), so two
   * variants are produced. Hyphens are removed and multi-glyph tricks (rn→m, vv→w) are folded.
   */
  public static Set<String> skeletons(String label) {
    String base = Normalizer.normalize(label.toLowerCase(), Normalizer.Form.NFKC);
    StringBuilder sb = new StringBuilder();
    for (char c : base.toCharArray()) {
      if (c == '-' || c == '_' || c == '.') continue;
      String m = MAP.get(c);
      sb.append(m != null ? m : String.valueOf(c));
    }
    String s = sb.toString();
    Set<String> out = new LinkedHashSet<>();
    for (String one : new String[] {"l", "i"}) {
      String v = s.replace("1", one);
      out.add(v);
      out.add(v.replace("rn", "m").replace("vv", "w").replace("cl", "d"));
    }
    return out;
  }

  /** Optimal string alignment distance: insert, delete, substitute and adjacent transposition. */
  public static int distance(String a, String b) {
    int[][] d = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) d[i][0] = i;
    for (int j = 0; j <= b.length(); j++) d[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length()][b.length()];
  }
}
