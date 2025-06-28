package com.example.google.google_hackathon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    // @Bean
    // public Jackson2ObjectMapperBuilder jacksonBuilder() {
    // Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    // builder.modules(new JavaTimeModule());
    // builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // return builder;
    // }
    /**
     * Jackson ObjectMapperのカスタマイズを行う設定クラス。
     * Java 8のDate/Time API (java.time.*) をJSONに適切にマッピングできるようにします。
     */

    @Bean
    @Primary // 同じ型のBeanが複数存在する場合に、このBeanを優先的に使用するようSpringに指示
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Java 8 Date & Time APIモジュールを登録
        objectMapper.registerModule(new JavaTimeModule());

        // 必要に応じて、追加のJackson設定をここで行うことができます。
        // 例: 日付をタイムスタンプではなくISO 8601形式の文字列として出力
        // objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        // false);

        return objectMapper;
    }
}
