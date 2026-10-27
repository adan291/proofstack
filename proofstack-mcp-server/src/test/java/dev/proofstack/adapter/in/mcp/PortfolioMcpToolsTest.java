package dev.proofstack.adapter.in.mcp;

import dev.proofstack.adapter.in.yaml.YamlProfileSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioMcpToolsTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", ".."));
    private final PortfolioMcpTools tools = new PortfolioMcpTools(
            new YamlProfileSource().load(REPO.resolve("profiles/example/profile.yaml")));

    @Test
    void summaryHasAreasAndNoEmail() {
        var summary = tools.getProfileSummary();

        assertThat(summary.name()).isEqualTo("Jane Doe");
        assertThat(summary.areas()).extracting(PortfolioMcpTools.AreaSummary::area).contains("Backend", "Messaging");
        assertThat(summary.toString()).doesNotContain("jane.doe@example.com");
    }

    @Test
    void listAndGetProject() {
        assertThat(tools.listProjects()).extracting(PortfolioMcpTools.ProjectItem::id).containsExactly("billing-events");

        var detail = tools.getProject("billing-events");
        assertThat(detail.problem()).contains("monolith");
        assertThat(detail.techStack()).contains("Kafka");
    }

    @Test
    void unknownIdsListTheAvailableOnes() {
        assertThatThrownBy(() -> tools.getProject("nope")).hasMessageContaining("billing-events");
        assertThatThrownBy(() -> tools.getContentFacts("nope")).hasMessageContaining("onboarding-guide");
    }

    @Test
    void searchFindsByKeywordAndReturnsNothingForUnrelatedTopics() {
        var hits = tools.searchPortfolio("kafka outbox", null);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().id()).isEqualTo("project:billing-events");
        assertThat(tools.searchPortfolio("paella", 3)).isEmpty();
        assertThat(tools.searchPortfolio("java", 50)).hasSizeLessThanOrEqualTo(10);
        assertThatThrownBy(() -> tools.searchPortfolio(" ", 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentFactsIncludeHonestyRules() {
        var facts = tools.getContentFacts("billing-events");

        assertThat(facts.subjectType()).isEqualTo("project");
        assertThat(facts.rules()).anyMatch(r -> r.contains("never invent"));

        assertThat(tools.getContentFacts("onboarding-guide").subjectType()).isEqualTo("evidence");
    }
}
