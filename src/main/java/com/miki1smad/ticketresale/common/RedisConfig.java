package com.miki1smad.ticketresale.common;

import java.util.HashMap;
import java.util.Map;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setCodec(new SerializationCodec());
        String address = "redis://" + redisHost + ":" + redisPort;
        var singleServerConfig = config.useSingleServer().setAddress(address);
        if (redisPassword != null && !redisPassword.isBlank()) {
            singleServerConfig.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<>();
        // "matches" TTL: 10 mins, maxIdle: 5 mins
        config.put("matches", new CacheConfig(10 * 60 * 1000, 5 * 60 * 1000));
        // "stadiums" TTL: 30 mins, maxIdle: 15 mins
        config.put("stadiums", new CacheConfig(30 * 60 * 1000, 15 * 60 * 1000));
        // "clubs" TTL: 30 mins, maxIdle: 15 mins
        config.put("clubs", new CacheConfig(30 * 60 * 1000, 15 * 60 * 1000));
        // "listings" TTL: 2 mins, maxIdle: 1 min
        config.put("listings", new CacheConfig(2 * 60 * 1000, 1 * 60 * 1000));
        return new RedissonSpringCacheManager(redissonClient, config);
    }

    @Bean
    public org.springframework.transaction.support.TransactionTemplate transactionTemplate(
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }
}
