package com.simulator.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.simulator.model.GraphQLSpec;
import com.simulator.model.RequestMapping;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

public class JpaConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Converter(autoApply = true)
    public static class RequestSpecConverter implements AttributeConverter<RequestMapping.RequestSpec, String> {
        @Override
        public String convertToDatabaseColumn(RequestMapping.RequestSpec attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize RequestSpec", e); }
        }
        @Override
        public RequestMapping.RequestSpec convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try { return MAPPER.readValue(dbData, RequestMapping.RequestSpec.class); } catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize RequestSpec", e); }
        }
    }

    @Converter(autoApply = true)
    public static class ResponseSpecConverter implements AttributeConverter<RequestMapping.ResponseSpec, String> {
        @Override
        public String convertToDatabaseColumn(RequestMapping.ResponseSpec attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize ResponseSpec", e); }
        }
        @Override
        public RequestMapping.ResponseSpec convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try { return MAPPER.readValue(dbData, RequestMapping.ResponseSpec.class); } catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize ResponseSpec", e); }
        }
    }

    @Converter(autoApply = true)
    public static class DelaySpecConverter implements AttributeConverter<RequestMapping.DelaySpec, String> {
        @Override
        public String convertToDatabaseColumn(RequestMapping.DelaySpec attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize DelaySpec", e); }
        }
        @Override
        public RequestMapping.DelaySpec convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try { return MAPPER.readValue(dbData, RequestMapping.DelaySpec.class); } catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize DelaySpec", e); }
        }
    }

    @Converter(autoApply = true)
    public static class GraphQLSpecConverter implements AttributeConverter<GraphQLSpec, String> {
        @Override
        public String convertToDatabaseColumn(GraphQLSpec attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize GraphQLSpec", e); }
        }
        @Override
        public GraphQLSpec convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try { return MAPPER.readValue(dbData, GraphQLSpec.class); } catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize GraphQLSpec", e); }
        }
    }

    @Converter(autoApply = false)
    public static class StringListConverter implements AttributeConverter<List<String>, String> {
        private static final TypeReference<List<String>> TYPE_REF = new TypeReference<>() {};
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            if (attribute == null) return null;
            try { return MAPPER.writeValueAsString(attribute); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize List<String>", e); }
        }
        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null) return null;
            try { return MAPPER.readValue(dbData, TYPE_REF); } catch (Exception e) { throw new IllegalArgumentException("Cannot deserialize List<String>", e); }
        }
    }
}
