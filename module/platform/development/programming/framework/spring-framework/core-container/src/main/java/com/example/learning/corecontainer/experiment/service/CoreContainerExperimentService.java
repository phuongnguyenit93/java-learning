package com.example.learning.corecontainer.experiment.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.example.learning.corecontainer.experiment.model.DependencyFailureScenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoreContainerExperimentService {

    private static final String ISOLATED_EXPERIMENT_PROFILE =
            "core-container-isolated-experiment";

    public Map<String, Object> observeConfigurationIdentity() {
        Map<String, Object> result = new LinkedHashMap<>();

        try (AnnotationConfigApplicationContext fullContext =
                     createContext(FullConfiguration.class)) {
            SampleRepository managedRepository = fullContext.getBean(SampleRepository.class);
            SampleService service = fullContext.getBean(SampleService.class);
            result.put("fullConfiguration", Map.of(
                    "managedRepositoryIdentityHashCode", identityHashCode(managedRepository),
                    "serviceRepositoryIdentityHashCode", identityHashCode(service.repository()),
                    "sameManagedInstance", managedRepository == service.repository()
            ));
        }

        try (AnnotationConfigApplicationContext liteContext =
                     createContext(LiteConfiguration.class)) {
            SampleRepository managedRepository = liteContext.getBean(SampleRepository.class);
            SampleService service = liteContext.getBean(SampleService.class);
            result.put("liteConfiguration", Map.of(
                    "managedRepositoryIdentityHashCode", identityHashCode(managedRepository),
                    "serviceRepositoryIdentityHashCode", identityHashCode(service.repository()),
                    "sameManagedInstance", managedRepository == service.repository()
            ));
        }

        return result;
    }

    public Map<String, Object> observeScopeIdentity() {
        try (AnnotationConfigApplicationContext context =
                     createContext(ScopeExperimentConfig.class)) {

            SingletonProbe singletonFirst = context.getBean(SingletonProbe.class);
            SingletonProbe singletonSecond = context.getBean(SingletonProbe.class);
            PrototypeProbe prototypeFirst = context.getBean(PrototypeProbe.class);
            PrototypeProbe prototypeSecond = context.getBean(PrototypeProbe.class);
            PrototypeConsumer consumer = context.getBean(PrototypeConsumer.class);

            PrototypeProbe injectedFirst = consumer.injectedPrototype();
            PrototypeProbe injectedSecond = consumer.injectedPrototype();
            PrototypeProbe providerFirst = consumer.newPrototype();
            PrototypeProbe providerSecond = consumer.newPrototype();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("singleton", Map.of(
                    "firstIdentityHashCode", identityHashCode(singletonFirst),
                    "secondIdentityHashCode", identityHashCode(singletonSecond),
                    "sameInstance", singletonFirst == singletonSecond
            ));
            result.put("prototype", Map.of(
                    "firstIdentityHashCode", identityHashCode(prototypeFirst),
                    "secondIdentityHashCode", identityHashCode(prototypeSecond),
                    "sameInstance", prototypeFirst == prototypeSecond
            ));
            result.put("singletonWithPrototypeDependency", Map.of(
                    "injectedFirstIdentityHashCode", identityHashCode(injectedFirst),
                    "injectedSecondIdentityHashCode", identityHashCode(injectedSecond),
                    "directInjectionReusesSamePrototype", injectedFirst == injectedSecond,
                    "providerFirstIdentityHashCode", identityHashCode(providerFirst),
                    "providerSecondIdentityHashCode", identityHashCode(providerSecond),
                    "providerReturnsFreshPrototype", providerFirst != providerSecond
            ));
            return result;
        }
    }

    public Map<String, Object> observeLifecycleCallbacks() {
        LifecycleTrace trace;
        List<String> initializationTrace;
        try (AnnotationConfigApplicationContext context =
                     createContext(LifecycleExperimentConfig.class)) {
            trace = context.getBean(LifecycleTrace.class);
            initializationTrace = trace.snapshot();
        }
        List<String> fullTrace = trace.snapshot();

        List<String> expectedInitialization = List.of(
                "constructor",
                "aware:setBeanName=lifecycleProbe",
                "init:@PostConstruct",
                "init:InitializingBean.afterPropertiesSet",
                "init:customInit"
        );
        List<String> expectedDestruction = List.of(
                "destroy:@PreDestroy",
                "destroy:DisposableBean.destroy",
                "destroy:customDestroy"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("initializationTrace", initializationTrace);
        result.put("destructionTrace",
                fullTrace.subList(initializationTrace.size(), fullTrace.size()));
        result.put("fullTrace", fullTrace);
        result.put("initializationOrderMatches", initializationTrace.equals(expectedInitialization));
        result.put("destructionOrderMatches",
                fullTrace.subList(initializationTrace.size(), fullTrace.size())
                        .equals(expectedDestruction));
        return result;
    }

    public Map<String, Object> observeDependencyFailure(DependencyFailureScenario scenario) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(ISOLATED_EXPERIMENT_PROFILE);
        Class<?> configClass = switch (scenario) {
            case MISSING -> MissingDependencyConfig.class;
            case AMBIGUOUS -> AmbiguousDependencyConfig.class;
            case CONSTRUCTOR_CYCLE -> ConstructorCycleConfig.class;
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario.name());
        result.put("phase", "ApplicationContext.refresh");

        try {
            context.register(configClass);
            context.refresh();
            result.put("refreshFailed", false);
            result.put("exceptionChain", List.of());
            result.put("rootCauseType", "");
            result.put("rootCauseMessage", "");
            return result;
        } catch (RuntimeException failure) {
            List<String> exceptionChain = new ArrayList<>();
            Throwable current = failure;
            Throwable root = failure;
            while (current != null && exceptionChain.size() < 10) {
                exceptionChain.add(current.getClass().getSimpleName());
                root = current;
                current = current.getCause();
            }

            result.put("refreshFailed", true);
            result.put("exceptionChain", exceptionChain);
            result.put("rootCauseType", root.getClass().getSimpleName());
            result.put("rootCauseMessage",
                    root.getMessage() == null ? "" : root.getMessage());
            return result;
        } finally {
            context.close();
        }
    }

    private static int identityHashCode(Object value) {
        return System.identityHashCode(value);
    }

    private static AnnotationConfigApplicationContext createContext(
            Class<?> configurationClass
    ) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().setActiveProfiles(ISOLATED_EXPERIMENT_PROFILE);
            context.register(configurationClass);
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    @Configuration
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class FullConfiguration {

        @Bean
        SampleRepository sampleRepository() {
            return new SampleRepository();
        }

        @Bean
        SampleService sampleService() {
            return new SampleService(sampleRepository());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class LiteConfiguration {

        @Bean
        SampleRepository sampleRepository() {
            return new SampleRepository();
        }

        @Bean
        SampleService sampleService() {
            return new SampleService(sampleRepository());
        }
    }

    static final class SampleRepository {
    }

    record SampleService(SampleRepository repository) {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class ScopeExperimentConfig {

        @Bean
        SingletonProbe singletonProbe() {
            return new SingletonProbe();
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        PrototypeProbe prototypeProbe() {
            return new PrototypeProbe();
        }

        @Bean
        PrototypeConsumer prototypeConsumer(
                PrototypeProbe prototypeProbe,
                ObjectProvider<PrototypeProbe> prototypeProvider
        ) {
            return new PrototypeConsumer(prototypeProbe, prototypeProvider);
        }
    }

    static final class SingletonProbe {
    }

    static final class PrototypeProbe {
    }

    static final class PrototypeConsumer {
        private final PrototypeProbe injectedPrototype;
        private final ObjectProvider<PrototypeProbe> prototypeProvider;

        PrototypeConsumer(
                PrototypeProbe injectedPrototype,
                ObjectProvider<PrototypeProbe> prototypeProvider
        ) {
            this.injectedPrototype = injectedPrototype;
            this.prototypeProvider = prototypeProvider;
        }

        PrototypeProbe injectedPrototype() {
            return injectedPrototype;
        }

        PrototypeProbe newPrototype() {
            return prototypeProvider.getObject();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class LifecycleExperimentConfig {

        @Bean
        LifecycleTrace lifecycleTrace() {
            return new LifecycleTrace();
        }

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        LifecycleProbe lifecycleProbe(LifecycleTrace trace) {
            return new LifecycleProbe(trace);
        }
    }

    static final class LifecycleTrace {
        private final List<String> events = new ArrayList<>();

        void add(String event) {
            events.add(event);
        }

        List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    static final class LifecycleProbe
            implements BeanNameAware, InitializingBean, DisposableBean {

        private final LifecycleTrace trace;

        LifecycleProbe(LifecycleTrace trace) {
            this.trace = trace;
            trace.add("constructor");
        }

        @Override
        public void setBeanName(String name) {
            trace.add("aware:setBeanName=" + name);
        }

        @PostConstruct
        void postConstruct() {
            trace.add("init:@PostConstruct");
        }

        @Override
        public void afterPropertiesSet() {
            trace.add("init:InitializingBean.afterPropertiesSet");
        }

        void customInit() {
            trace.add("init:customInit");
        }

        @PreDestroy
        void preDestroy() {
            trace.add("destroy:@PreDestroy");
        }

        @Override
        public void destroy() {
            trace.add("destroy:DisposableBean.destroy");
        }

        void customDestroy() {
            trace.add("destroy:customDestroy");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class MissingDependencyConfig {

        @Bean
        MissingConsumer missingConsumer(MissingPort missingPort) {
            return new MissingConsumer(missingPort);
        }
    }

    interface MissingPort {
    }

    record MissingConsumer(MissingPort missingPort) {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class AmbiguousDependencyConfig {

        @Bean
        CandidatePort candidateA() {
            return new CandidatePort("A");
        }

        @Bean
        CandidatePort candidateB() {
            return new CandidatePort("B");
        }

        @Bean
        CandidateConsumer candidateConsumer(CandidatePort candidatePort) {
            return new CandidateConsumer(candidatePort);
        }
    }

    record CandidatePort(String id) {
    }

    record CandidateConsumer(CandidatePort candidatePort) {
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(ISOLATED_EXPERIMENT_PROFILE)
    static class ConstructorCycleConfig {

        @Bean
        CycleA cycleA(CycleB cycleB) {
            return new CycleA(cycleB);
        }

        @Bean
        CycleB cycleB(CycleA cycleA) {
            return new CycleB(cycleA);
        }
    }

    record CycleA(CycleB cycleB) {
    }

    record CycleB(CycleA cycleA) {
    }
}
