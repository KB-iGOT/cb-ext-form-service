package com.karmayogi.form.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author anil
 */
@Configuration
@ConditionalOnMissingBean(RestHighLevelClient.class)
public class ESConfig {

    @Value("${form.elasticsearch.host}")
    private String host;

    @Value("${form.elasticsearch.port}")
    private int port;

    @Value("${form.elasticsearch.scheme}")
    private String scheme;

    @Value("${form.elasticsearch.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${form.elasticsearch.socket-timeout-ms}")
    private int socketTimeoutMs;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, scheme))
                        .setRequestConfigCallback(builder -> builder
                                .setConnectTimeout(connectTimeoutMs)
                                .setSocketTimeout(socketTimeoutMs)));
    }


}
