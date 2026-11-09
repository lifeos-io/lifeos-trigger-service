package com.lifeos.trigger.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.lifeos.base.id.UuidV7;
import com.lifeos.trigger.strategyevents.consumer.StrategyEventConsumer;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base of the service's integration tests: the whole application over MockMvc, against real PostgreSQL and
 * Kafka. Requests are authenticated with {@link #as(UUID)} (a JWT whose {@code sub} is the user ID).
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        // The tests run due jobs themselves, at the clock's time; the background pass stays out of the way.
        "lifeos.trigger.runner.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbcClient;

    @Autowired
    protected JsonMapper jsonMapper;

    protected static RequestPostProcessor as(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    private StrategyEventConsumer strategyEventConsumer;

    /** Hands a Strategy event to Trigger's consumer, as Kafka would; the payload is checked against its schema. */
    protected void strategyEvent(UUID userId, String eventType, JsonNode payload) {
        EventSchemas.assertValidStrategyPayload(eventType, 1, payload);
        ObjectNode envelope = jsonMapper.createObjectNode()
                .put("eventId", UuidV7.next().toString())
                .put("eventType", eventType)
                .put("eventVersion", 1)
                .put("occurredAt", clock.instant().toString())
                .put("userId", userId.toString());
        envelope.set("payload", payload);
        strategyEventConsumer.on(new ConsumerRecord<>("lifeos.strategy.events.v1", 0, 0L, userId.toString(),
                jsonMapper.writeValueAsString(envelope)));
    }

    /** Payloads of the user's Trigger events of one type in the outbox, oldest first. */
    protected List<JsonNode> outboxPayloads(UUID userId, String eventType) {
        return jdbcClient.sql("""
                        SELECT payload::text FROM outbox_event
                        WHERE user_id = :userId AND event_type = :eventType ORDER BY seq
                        """)
                .param("userId", userId)
                .param("eventType", eventType)
                .query(String.class)
                .list()
                .stream()
                .map(jsonMapper::readTree)
                .toList();
    }
}
