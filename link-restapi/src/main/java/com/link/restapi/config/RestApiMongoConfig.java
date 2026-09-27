package com.link.restapi.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;

@Configuration
public class RestApiMongoConfig {

    @Value("${spring.mongodb.uri}")
    private String mongoUri;

    /**
     * 默认 MongoClient（使用连接串的 readPreference=secondaryPreferred）
     */
    @Bean
    @Primary
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(settings);
    }

    /**
     * 默认 DatabaseFactory（基于默认 MongoClient）
     */
    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory(@Qualifier("mongoClient") MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, "im");
    }

    /**
     * 默认 MongoTemplate（优先从从库读，性能更好）
     *
     * 这个 Bean 会被自动注入到 link-base 中继承了 BasePlatFormMongoService 的服务类
     */
    @Bean
    @Primary
    public MongoTemplate mongoTemplate(
            @Qualifier("mongoDatabaseFactory") MongoDatabaseFactory factory,
            MongoConverter converter) {
        return new MongoTemplate(factory, converter);
    }

    /**
     * 专门给事务使用的 Primary MongoClient
     */
    @Bean("primaryMongoClient")
    public MongoClient primaryMongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .readPreference(ReadPreference.primary())
                .build();

        return MongoClients.create(settings);
    }

    /**
     * Primary DatabaseFactory
     */
    @Bean("primaryMongoDatabaseFactory")
    public MongoDatabaseFactory primaryMongoDatabaseFactory(
            @Qualifier("primaryMongoClient") MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, "im");
    }

    /**
     * Primary MongoTemplate（强制从主库读，用于事务）
     */
    @Bean("primaryMongoTemplate")
    public MongoTemplate primaryMongoTemplate(
            @Qualifier("primaryMongoDatabaseFactory") MongoDatabaseFactory factory,
            MongoConverter converter) {
        return new MongoTemplate(factory, converter);
    }

    /**
     * MongoDB 事务管理器
     */
    @Bean
    public MongoTransactionManager transactionManager(
            @Qualifier("mongoDatabaseFactory") MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
