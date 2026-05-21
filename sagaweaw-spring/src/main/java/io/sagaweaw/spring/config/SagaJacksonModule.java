package io.sagaweaw.spring.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.core.StepStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SagaJacksonModule {

    @Bean
    public JacksonModule sagaStatusModule() {
        SimpleModule module = new SimpleModule("sagaweaw-status");

        module.addSerializer(SagaStatus.class, new StdSerializer<>(SagaStatus.class) {
            @Override
            public void serialize(SagaStatus v, JsonGenerator g, SerializationContext p) throws JacksonException {
                g.writeString(v.persistenceName());
            }
        });
        module.addDeserializer(SagaStatus.class, new StdDeserializer<>(SagaStatus.class) {
            @Override
            public SagaStatus deserialize(JsonParser p, DeserializationContext ctx) throws JacksonException {
                return SagaStatus.fromPersistenceName(p.getText());
            }
        });

        module.addSerializer(StepStatus.class, new StdSerializer<>(StepStatus.class) {
            @Override
            public void serialize(StepStatus v, JsonGenerator g, SerializationContext p) throws JacksonException {
                g.writeString(v.persistenceName());
            }
        });
        module.addDeserializer(StepStatus.class, new StdDeserializer<>(StepStatus.class) {
            @Override
            public StepStatus deserialize(JsonParser p, DeserializationContext ctx) throws JacksonException {
                return StepStatus.fromPersistenceName(p.getText());
            }
        });

        return module;
    }
}
