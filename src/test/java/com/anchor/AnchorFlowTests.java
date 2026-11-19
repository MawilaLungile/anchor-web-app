package com.anchor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end: sign up, onboard with sample data, render every screen, run a focus session. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:anchor-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.thymeleaf.cache=true"
})
@AutoConfigureMockMvc
class AnchorFlowTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void publicPagesAreReachableAndAppIsProtected() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void fullJourney() throws Exception {
        MvcResult registered = mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Thandi Test")
                        .param("email", "thandi@example.com")
                        .param("password", "correct-horse-battery")
                        .param("timeZone", "Africa/Johannesburg"))
                .andExpect(redirectedUrl("/welcome"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        // Not onboarded yet: the app redirects to the welcome tour.
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/welcome"));
        mvc.perform(post("/welcome").session(session).with(csrf()).param("mode", "sample")).andExpect(redirectedUrl("/"));

        for (String page : List.of("/", "/focus", "/shield", "/shield/sites?list=FOCUS", "/shield/sites?list=ALWAYS",
                "/insights", "/insights?days=30", "/settings", "/energy", "/review", "/urge", "/tasks/new")) {
            mvc.perform(get(page).session(session)).andExpect(status().isOk());
        }

        mvc.perform(get("/").session(session)).andExpect(content().string(containsString("Finish the product strategy memo")));

        // Add a task, then start and end a focus commitment.
        mvc.perform(post("/tasks").session(session).with(csrf())
                        .param("title", "Write the integration tests").param("slot", "OTHER")
                        .param("deep", "true").param("estimateMinutes", "30").param("when", "today"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(post("/focus/start").session(session).with(csrf())
                        .param("intention", "Write the integration tests").param("minutes", "25").param("block", "true"))
                .andExpect(redirectedUrl("/focus"));
        mvc.perform(get("/focus").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Committed to")));
        mvc.perform(post("/focus/end").session(session).with(csrf()).param("reason", "Fire drill"))
                .andExpect(redirectedUrl("/focus"));

        // Energy, urge and blocked sites.
        mvc.perform(post("/energy").session(session).with(csrf()).param("level", "4")).andExpect(redirectedUrl("/"));
        mvc.perform(post("/urge").session(session).with(csrf()).param("trigger", "Bored").param("source", "manual").param("resisted", "true"))
                .andExpect(redirectedUrl("/urge/done"));
        mvc.perform(post("/shield/sites").session(session).with(csrf()).param("domain", "https://www.example.org/page").param("list", "ALWAYS"))
                .andExpect(redirectedUrl("/shield/sites?list=ALWAYS"));
        mvc.perform(get("/shield/sites?list=ALWAYS").session(session)).andExpect(content().string(containsString("example.org")));

        mvc.perform(get("/settings/export.json").session(session)).andExpect(status().isOk());
        mvc.perform(get("/shield/hosts.txt?adult=false").session(session)).andExpect(status().isOk());
    }
}
