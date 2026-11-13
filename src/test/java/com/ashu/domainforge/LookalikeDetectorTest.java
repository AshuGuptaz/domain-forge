package com.ashu.domainforge;

import static org.assertj.core.api.Assertions.assertThat;

import com.ashu.domainforge.risk.LookalikeDetector;
import com.ashu.domainforge.risk.LookalikeReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"domainforge.availability.mode=mock", "domainforge.embedding.mode=off"})
class LookalikeDetectorTest {

  @Autowired LookalikeDetector detector;

  @Test
  void officialDomainIsNotFlagged() {
    LookalikeReport r = detector.analyze("paypal.com");
    assertThat(r.official()).isTrue();
    assertThat(r.level()).isEqualTo("OFFICIAL");
  }

  @Test
  void digitSubstitutionIsHighRisk() {
    LookalikeReport r = detector.analyze("paypa1.com");
    assertThat(r.level()).isEqualTo("HIGH");
    assertThat(r.matches().get(0).brand()).isEqualTo("paypal.com");
  }

  @Test
  void rnForMIsHighRisk() {
    assertThat(detector.analyze("rnicrosoft.com").level()).isEqualTo("HIGH");
  }

  @Test
  void cyrillicHomoglyphIsHighRisk() {
    // first letter is Cyrillic 'а' (U+0430)
    LookalikeReport r = detector.analyze("аpple.com");
    assertThat(r.level()).isEqualTo("HIGH");
    assertThat(r.flags()).anyMatch(f -> f.startsWith("mixed scripts"));
  }

  @Test
  void brandPlusPhishingKeywordIsHighRisk() {
    LookalikeReport r = detector.analyze("paypal-secure-login.com");
    assertThat(r.level()).isEqualTo("HIGH");
    assertThat(r.matches().get(0).reason()).contains("phishing keyword");
  }

  @Test
  void oneCharacterTypoIsHighRisk() {
    assertThat(detector.analyze("gogle.com").level()).isEqualTo("HIGH");
    assertThat(detector.analyze("netflx.com").level()).isEqualTo("HIGH");
  }

  @Test
  void transpositionIsCaught() {
    assertThat(detector.analyze("amazno.com").level()).isEqualTo("HIGH");
  }

  @Test
  void brandOnOtherTldIsMedium() {
    assertThat(detector.analyze("spotify.xyz").level()).isEqualTo("MEDIUM");
  }

  @Test
  void unrelatedNamesAreClean() {
    assertThat(detector.analyze("brewly.com").level()).isEqualTo("NONE");
    assertThat(detector.analyze("sproutlabs.io").level()).isEqualTo("NONE");
    assertThat(detector.analyze("lucknowroast.in").level()).isEqualTo("NONE");
  }

  @Test
  void punycodeIsDecodedAndFlagged() {
    // xn--pple-43d.com is "аpple.com" with a Cyrillic а
    LookalikeReport r = detector.analyze("xn--pple-43d.com");
    assertThat(r.flags()).anyMatch(f -> f.startsWith("punycode"));
    assertThat(r.level()).isEqualTo("HIGH");
  }

  @Test
  void urlsAreAccepted() {
    assertThat(detector.analyze("https://www.paypa1.com/login").level()).isEqualTo("HIGH");
  }
}
