package com.karmayogi.form.config;

import com.karmayogi.form.utils.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author anil
 */
@Configuration
public class CORSConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedMethods(Constants.GET, Constants.POST,
                                Constants.PUT, Constants.DELETE, Constants.OPTIONS)
                        .allowedOrigins("*").allowedHeaders("*");
            }
        };
    }

}
