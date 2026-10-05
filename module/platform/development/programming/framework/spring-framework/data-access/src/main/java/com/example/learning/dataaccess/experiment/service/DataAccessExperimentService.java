package com.example.learning.dataaccess.experiment.service;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.R2dbcException;
import org.reactivestreams.Publisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.SingleConnectionFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DataAccessExperimentService {

    private static final Duration R2DBC_TIMEOUT = Duration.ofSeconds(5);

    public Map<String, Object> observeJdbcCardinality() {
        EmbeddedDatabase database = createJdbcDatabase();
        try {
            JdbcClient client = JdbcClient.create(database);
            client.sql("""
                    create table customer (
                        id integer primary key,
                        name varchar(100) not null,
                        status varchar(20) not null
                    )
                    """).update();
            client.sql("""
                    insert into customer(id, name, status)
                    values (1, 'Ada', 'ACTIVE'),
                           (2, 'Grace', 'ACTIVE'),
                           (3, 'Linus', 'INACTIVE')
                    """).update();

            List<String> activeNames = client.sql("""
                    select name
                    from customer
                    where status = :status
                    order by id
                    """)
                    .param("status", "ACTIVE")
                    .query(String.class)
                    .list();

            Optional<String> missing = client.sql(
                            "select name from customer where id = :id")
                    .param("id", 99)
                    .query(String.class)
                    .optional();

            String exactlyOne = client.sql(
                            "select name from customer where id = :id")
                    .param("id", 1)
                    .query(String.class)
                    .single();

            String duplicateSingleFailure;
            try {
                client.sql("""
                        select name
                        from customer
                        where status = :status
                        order by id
                        """)
                        .param("status", "ACTIVE")
                        .query(String.class)
                        .single();
                duplicateSingleFailure = "NONE";
            }
            catch (IncorrectResultSizeDataAccessException exception) {
                duplicateSingleFailure =
                        exception.getClass().getSimpleName();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("namedParameter", "ACTIVE");
            result.put("listResult", activeNames);
            result.put("listSize", activeNames.size());
            result.put("optionalMissingPresent", missing.isPresent());
            result.put("singleResult", exactlyOne);
            result.put("duplicateSingleFailure", duplicateSingleFailure);
            result.put(
                    "cardinalityContractObserved",
                    activeNames.size() == 2
                            && missing.isEmpty()
                            && "Ada".equals(exactlyOne)
                            && !"NONE".equals(duplicateSingleFailure)
            );
            return result;
        }
        finally {
            database.shutdown();
        }
    }

    public Map<String, Object> observeCrossStackExceptionTranslation() {
        Map<String, Object> jdbcEvidence = jdbcDuplicateKeyEvidence();
        Map<String, Object> r2dbcEvidence = r2dbcDuplicateKeyEvidence();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jdbc", jdbcEvidence);
        result.put("r2dbc", r2dbcEvidence);
        result.put(
                "bothUseSpringDataAccessHierarchy",
                Boolean.TRUE.equals(jdbcEvidence.get("dataAccessException"))
                        && Boolean.TRUE.equals(r2dbcEvidence.get("dataAccessException"))
        );
        result.put(
                "bothClassifyIntegrityFailure",
                Boolean.TRUE.equals(jdbcEvidence.get("dataIntegrityViolation"))
                        && Boolean.TRUE.equals(r2dbcEvidence.get("dataIntegrityViolation"))
        );
        return result;
    }

    public Map<String, Object> observeR2dbcDeferredExecution() {
        CountingConnectionFactory connectionFactory =
                new CountingConnectionFactory(
                        createDriverConnectionFactory()
                );
        DatabaseClient client =
                DatabaseClient.create(connectionFactory);

        Mono<Integer> query = client.sql("select 1")
                .map((row, metadata) ->
                        row.get(0, Integer.class))
                .one();

        int acquisitionsBeforeSubscription =
                connectionFactory.acquisitionCount();
        Integer resultValue = block(query);
        int acquisitionsAfterSubscription =
                connectionFactory.acquisitionCount();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publisherBuilt", true);
        result.put(
                "acquisitionsBeforeSubscription",
                acquisitionsBeforeSubscription
        );
        result.put(
                "acquisitionsAfterSubscription",
                acquisitionsAfterSubscription
        );
        result.put("queryResult", resultValue);
        result.put(
                "deferredExecutionObserved",
                acquisitionsBeforeSubscription == 0
                        && acquisitionsAfterSubscription > 0
                        && Integer.valueOf(1).equals(resultValue)
        );
        return result;
    }

    public Map<String, Object> observeResourceBinding() {
        EmbeddedDatabase jdbcDatabase = createJdbcDatabase();
        try {
            Map<String, Object> jdbcEvidence =
                    observeJdbcResourceBinding(jdbcDatabase);
            Map<String, Object> r2dbcEvidence =
                    observeR2dbcResourceBinding();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jdbc", jdbcEvidence);
            result.put("r2dbc", r2dbcEvidence);
            result.put(
                    "bothReuseTransactionAssociatedResource",
                    Boolean.TRUE.equals(
                            jdbcEvidence.get("sameConnection")
                    )
                            && Boolean.TRUE.equals(
                            r2dbcEvidence.get("sameTransactionResourceReused")
                    )
                            && Boolean.TRUE.equals(
                            r2dbcEvidence.get("schedulerHopObserved")
                    )
            );
            return result;
        }
        finally {
            jdbcDatabase.shutdown();
        }
    }

    private Map<String, Object> jdbcDuplicateKeyEvidence() {
        EmbeddedDatabase database = createJdbcDatabase();
        try {
            JdbcClient client = JdbcClient.create(database);
            client.sql("""
                    create table account (
                        id integer primary key,
                        email varchar(120) not null unique
                    )
                    """).update();
            client.sql("""
                    insert into account(id, email)
                    values (:id, :email)
                    """)
                    .param("id", 1)
                    .param("email", "learner@example.test")
                    .update();

            try {
                client.sql("""
                        insert into account(id, email)
                        values (:id, :email)
                        """)
                        .param("id", 2)
                        .param("email", "learner@example.test")
                        .update();
                return Map.of(
                        "dataAccessException", false,
                        "dataIntegrityViolation", false,
                        "springExceptionType", "NONE",
                        "rootCauseType", "NONE"
                );
            }
            catch (DataAccessException exception) {
                return exceptionEvidence(exception);
            }
        }
        finally {
            database.shutdown();
        }
    }

    private Map<String, Object> r2dbcDuplicateKeyEvidence() {
        SingleConnectionFactory connectionFactory =
                createR2dbcConnectionFactory();
        try {
            DatabaseClient client =
                    DatabaseClient.create(connectionFactory);
            blockCompletion(client.sql("""
                    create table account (
                        id integer primary key,
                        email varchar(120) not null unique
                    )
                    """).then());
            block(client.sql("""
                    insert into account(id, email)
                    values (:id, :email)
                    """)
                    .bind("id", 1)
                    .bind("email", "learner@example.test")
                    .fetch()
                    .rowsUpdated());

            try {
                block(client.sql("""
                        insert into account(id, email)
                        values (:id, :email)
                        """)
                        .bind("id", 2)
                        .bind("email", "learner@example.test")
                        .fetch()
                        .rowsUpdated());
                return Map.of(
                        "dataAccessException", false,
                        "dataIntegrityViolation", false,
                        "springExceptionType", "NONE",
                        "rootCauseType", "NONE"
                );
            }
            catch (DataAccessException exception) {
                return exceptionEvidence(exception);
            }
        }
        finally {
            connectionFactory.destroy();
        }
    }

    private Map<String, Object> exceptionEvidence(
            DataAccessException exception
    ) {
        Throwable rootCause = exception;
        boolean nativeDatabaseCausePreserved = false;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
            nativeDatabaseCausePreserved =
                    nativeDatabaseCausePreserved
                            || rootCause instanceof SQLException
                            || rootCause instanceof R2dbcException;
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("dataAccessException", true);
        evidence.put(
                "dataIntegrityViolation",
                exception instanceof DataIntegrityViolationException
        );
        evidence.put(
                "springExceptionType",
                exception.getClass().getSimpleName()
        );
        evidence.put(
                "rootCauseType",
                rootCause.getClass().getSimpleName()
        );
        evidence.put(
                "nativeDatabaseCausePreserved",
                nativeDatabaseCausePreserved
        );
        return evidence;
    }

    private Map<String, Object> observeJdbcResourceBinding(
            EmbeddedDatabase database
    ) {
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(database);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {
            Connection first =
                    DataSourceUtils.getConnection(database);
            Connection second =
                    DataSourceUtils.getConnection(database);
            try {
                Map<String, Object> evidence =
                        new LinkedHashMap<>();
                evidence.put(
                        "firstConnectionIdentityHashCode",
                        System.identityHashCode(first)
                );
                evidence.put(
                        "secondConnectionIdentityHashCode",
                        System.identityHashCode(second)
                );
                evidence.put(
                        "sameConnection",
                        first == second
                );
                evidence.put(
                        "thread",
                        Thread.currentThread().getName()
                );
                return evidence;
            }
            finally {
                DataSourceUtils.releaseConnection(
                        second,
                        database
                );
                DataSourceUtils.releaseConnection(
                        first,
                        database
                );
            }
        });
    }

    private Map<String, Object> observeR2dbcResourceBinding() {
        CountingConnectionFactory connectionFactory =
                new CountingConnectionFactory(
                        createDriverConnectionFactory()
                );
        R2dbcTransactionManager transactionManager =
                new R2dbcTransactionManager(connectionFactory);
        TransactionalOperator transactionalOperator =
                TransactionalOperator.create(transactionManager);
        DatabaseClient client =
                DatabaseClient.create(connectionFactory);

        Mono<Map<String, Object>> evidence =
                client.sql("select 1")
                        .map((row, metadata) ->
                                row.get(0, Integer.class))
                        .one()
                        .flatMap(firstResult -> {
                            int acquisitionsAfterFirstOperation =
                                    connectionFactory.acquisitionCount();
                            String firstThread =
                                    Thread.currentThread().getName();

                            return Mono.just(firstResult)
                                    .publishOn(
                                            Schedulers.boundedElastic()
                                    )
                                    .flatMap(ignored ->
                                            client.sql("select 2")
                                                    .map((row, metadata) ->
                                                            row.get(
                                                                    0,
                                                                    Integer.class
                                                            ))
                                                    .one()
                                                    .map(secondResult -> {
                                                        int acquisitionsAfterSecondOperation =
                                                                connectionFactory
                                                                        .acquisitionCount();
                                                        Map<String, Object> value =
                                                                new LinkedHashMap<>();
                                                        value.put(
                                                                "firstQueryResult",
                                                                firstResult
                                                        );
                                                        value.put(
                                                                "secondQueryResult",
                                                                secondResult
                                                        );
                                                        value.put(
                                                                "connectionAcquisitionsAfterFirstOperation",
                                                                acquisitionsAfterFirstOperation
                                                        );
                                                        value.put(
                                                                "connectionAcquisitionsAfterSecondOperation",
                                                                acquisitionsAfterSecondOperation
                                                        );
                                                        value.put(
                                                                "threadBeforeSchedulerHop",
                                                                firstThread
                                                        );
                                                        value.put(
                                                                "threadAfterSchedulerHop",
                                                                Thread.currentThread()
                                                                        .getName()
                                                        );
                                                        value.put(
                                                                "schedulerHopObserved",
                                                                !firstThread.equals(
                                                                        Thread.currentThread()
                                                                                .getName()
                                                                )
                                                        );
                                                        value.put(
                                                                "sameTransactionResourceReused",
                                                                acquisitionsAfterFirstOperation
                                                                        == 1
                                                                        && acquisitionsAfterSecondOperation
                                                                        == acquisitionsAfterFirstOperation
                                                                        && Integer.valueOf(1)
                                                                        .equals(firstResult)
                                                                        && Integer.valueOf(2)
                                                                        .equals(secondResult)
                                                        );
                                                        return value;
                                                    })
                                    );
                        })
                        .as(transactionalOperator::transactional);

        return block(evidence);
    }

    private EmbeddedDatabase createJdbcDatabase() {
        return new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }

    private SingleConnectionFactory createR2dbcConnectionFactory() {
        return new SingleConnectionFactory(
                r2dbcH2Url(),
                true
        );
    }

    private ConnectionFactory createDriverConnectionFactory() {
        return ConnectionFactories.get(r2dbcH2Url());
    }

    private String r2dbcH2Url() {
        String databaseName =
                "data_access_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "");
        return "r2dbc:h2:mem:///" + databaseName;
    }

    private <T> T block(Mono<T> publisher) {
        return publisher.block(R2DBC_TIMEOUT);
    }

    private void blockCompletion(Mono<Void> publisher) {
        publisher.block(R2DBC_TIMEOUT);
    }

    private static final class CountingConnectionFactory
            implements ConnectionFactory {

        private final ConnectionFactory delegate;
        private final AtomicInteger acquisitions =
                new AtomicInteger();

        private CountingConnectionFactory(
                ConnectionFactory delegate
        ) {
            this.delegate = delegate;
        }

        @Override
        public Publisher<? extends io.r2dbc.spi.Connection> create() {
            return Mono.defer(() -> {
                acquisitions.incrementAndGet();
                return Mono.from(delegate.create());
            });
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return delegate.getMetadata();
        }

        private int acquisitionCount() {
            return acquisitions.get();
        }
    }
}
