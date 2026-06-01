package io.sagaweaw.spring.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.core.StepStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SagaJacksonModule {

    @Bean
    public Module sagaStatusModule() {
        SimpleModule module = new SimpleModule("sagaweaw-status");

        module.addSerializer(SagaStatus.class, new StdSerializer<>(SagaStatus.class) {
            @Override
            public void serialize(SagaStatus v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.persistenceName());
            }
        });
        module.addDeserializer(SagaStatus.class, new StdDeserializer<>(SagaStatus.class) {
            @Override
            public SagaStatus deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                return SagaStatus.fromPersistenceName(p.getText());
            }
        });

        module.addSerializer(StepStatus.class, new StdSerializer<>(StepStatus.class) {
            @Override
            public void serialize(StepStatus v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.persistenceName());
            }
        });
        module.addDeserializer(StepStatus.class, new StdDeserializer<>(StepStatus.class) {
            @Override
            public StepStatus deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                return StepStatus.fromPersistenceName(p.getText());
            }
        });

        return module;
    }
}
