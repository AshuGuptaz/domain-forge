package com.ashu.domainforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ashu.domainforge.config.DomainForgeProperties;
import com.ashu.domainforge.gen.AiAdvisor;
import com.ashu.domainforge.gen.LlmClient;
import com.ashu.domainforge.gen.LlmNameGenerator;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"domainforge.availability.mode=mock", "domainforge.embedding.mode=off"})
@AutoConfigureMockMvc
class ApiAndAiTest {

  @Autowired MockMvc mvc;

  @Test
  void suggestReturnsRankedAvailableNames() throws Exception {
    mvc.perform(post("/api/v1/suggest").contentType(MediaType.APPLICATION_JSON)
            .content("{\"description\":\"I'm opening a coffee shop in Lucknow\",\"count\":8,\"ai\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.suggestions.length()").value(8))
        .andExpect(jsonPath("$.suggestions[0].status").value("AVAILABLE"))
        .andExpect(jsonPath("$.suggestions[0].score").isNumber())
        .andExpect(jsonPath("$.keywords[0]").value("coffee"));
  }

  @Test
  void suggestionsAreSortedByScoreAndLabelsUnique() throws Exception {
    String body = mvc.perform(post("/api/v1/suggest").contentType(MediaType.APPLICATION_JSON)
            .content("{\"description\":\"organic tea garden\",\"count\":12,\"ai\":false}"))
        .andReturn().getResponse().getContentAsString();
    var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
    double prev = 101;
    java.util.Set<String> labels = new java.util.HashSet<>();
    for (var s : root.get("suggestions")) {
      assertThat(s.get("score").asDouble()).isLessThanOrEqualTo(prev);
      prev = s.get("score").asDouble();
      assertThat(labels.add(s.get("label").asText())).isTrue();
      assertThat(s.get("risk").asText()).isNotEqualTo("HIGH");
    }
  }

  @Test
  void blankDescriptionIsRejected() throws Exception {
    mvc.perform(post("/api/v1/suggest").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void lookalikeEndpointFlagsTyposquat() throws Exception {
    mvc.perform(post("/api/v1/lookalike").contentType(MediaType.APPLICATION_JSON).content("{\"domain\":\"paypa1.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.report.level").value("HIGH"));
  }

  @Test
  void checkRejectsGarbage() throws Exception {
    mvc.perform(get("/api/v1/check").param("domain", "not a domain")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/check").param("domain", "freshbrew.com")).andExpect(status().isOk());
  }

  @Test
  void statsExposeVexBrandCount() throws Exception {
    mvc.perform(get("/api/v1/stats")).andExpect(status().isOk())
        .andExpect(jsonPath("$.brandsIndexedInVex").value(org.hamcrest.Matchers.greaterThan(100)));
  }

  /** Exercises the real HTTP code path of the AI features against a local stub of an OpenAI-compatible chat endpoint. */
  @Test
  void aiFeaturesParseModelOutputFromStubServer() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", ex -> {
      String req = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String text = req.contains("JSON array")
          ? "Sure! [\"Brewvana\", \"bean-bliss\", \"CupCraft\"]"
          : "{\"brewly.com\": \"Short, warm and instantly says coffee.\"}";
      String json = new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", text)))));
      byte[] out = json.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("content-type", "application/json");
      ex.sendResponseHeaders(200, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
    try {
      DomainForgeProperties props = new DomainForgeProperties();
      props.getLlm().setModel("test-model");
      props.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
      LlmClient client = new LlmClient(props);

      List<String> names = new LlmNameGenerator(client).generate("coffee shop", List.of("coffee"), 10);
      assertThat(names).containsExactly("brewvana", "beanbliss", "cupcraft");

      Map<String, String> why = new AiAdvisor(client).explainNames("coffee shop", List.of("brewly.com"));
      assertThat(why).containsEntry("brewly.com", "Short, warm and instantly says coffee.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void aiDegradesGracefullyWithoutModelOrServer() {
    DomainForgeProperties props = new DomainForgeProperties();
    assertThat(new LlmNameGenerator(new LlmClient(props)).generate("x", List.of(), 5)).isEmpty();
    props.getLlm().setModel("test-model");
    props.getLlm().setBaseUrl("http://127.0.0.1:1");
    assertThat(new AiAdvisor(new LlmClient(props)).explainNames("x", List.of("a.com"))).isEmpty();
  }
}
