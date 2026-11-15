package com.ordredesarcane.online.config;

import java.lang.reflect.Type;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class HibernateJsonConfig {

    @Bean
    public HibernatePropertiesCustomizer jackson3JsonFormatMapperCustomizer(JsonMapper jsonMapper) {
        return properties -> properties.put(
                MappingSettings.JSON_FORMAT_MAPPER,
                new AbstractJsonFormatMapper() {
                    @Override
                    protected <T> T fromString(CharSequence charSequence, Type javaType) {
                        return jsonMapper.readValue(
                                charSequence.toString(),
                                jsonMapper.constructType(javaType));
                    }

                    @Override
                    protected <T> String toString(T value, Type javaType) {
                        return jsonMapper.writeValueAsString(value);
                    }
                });
    }
}
