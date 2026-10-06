package com.example.projectbuild.exceptionhandler.servlet;

import com.example.projectbuild.exceptionhandler.servlet.autoconfigure.FeignExceptionHandlerAutoConfiguration;
import com.example.projectbuild.exceptionhandler.servlet.autoconfigure.MongoExceptionHandlerAutoConfiguration;
import com.example.projectbuild.exceptionhandler.servlet.autoconfigure.ServletExceptionHandlerAutoConfiguration;
import com.example.projectbuild.exceptionhandler.servlet.feign.FeignClientExceptionHandler;
import com.example.projectbuild.exceptionhandler.servlet.mongo.MongoExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHandlerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ServletExceptionHandlerAutoConfiguration.class,
                    FeignExceptionHandlerAutoConfiguration.class,
                    MongoExceptionHandlerAutoConfiguration.class
            ));

    @Test
    void configuresCommonAndOptionalHandlersWhenDependenciesArePresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(FeignClientExceptionHandler.class);
            assertThat(context).hasSingleBean(MongoExceptionHandler.class);
        });
    }

    @Test
    void backsOffFeignHandlerWhenFeignIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("feign"))
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(FeignClientExceptionHandler.class);
                });
    }

    @Test
    void backsOffMongoHandlerWhenSpringDataMongoIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.mongodb"))
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(MongoExceptionHandler.class);
                });
    }
}
