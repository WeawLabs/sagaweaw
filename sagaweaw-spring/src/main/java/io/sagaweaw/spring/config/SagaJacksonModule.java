package io.sagaweaw.spring.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.sagaweaw.core.SagaMask;
import io.sagaweaw.core.SagaStatus;
import io.sagaweaw.core.StepStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

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

        // Replace serialization of @SagaMask fields with "[REDACTED]" so that
        // sensitive data (PII, tokens, credentials) never reaches context_json,
        // the observability API, or the dead-letter queue.
        module.setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription beanDesc,
                    List<BeanPropertyWriter> beanProperties) {
                for (int i = 0; i < beanProperties.size(); i++) {
                    if (beanProperties.get(i).getAnnotation(SagaMask.class) != null) {
                        beanProperties.set(i, new RedactedWriter(beanProperties.get(i)));
                    }
                }
                return beanProperties;
            }
        });

        return module;
    }

    /** Replaces the field value with "[REDACTED]" during JSON serialization. */
    static class RedactedWriter extends BeanPropertyWriter {
        RedactedWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov)
                throws Exception {
            gen.writeStringField(getName(), "[REDACTED]");
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov)
                throws Exception {
            gen.writeString("[REDACTED]");
        }
    }
}
