# Project Module Structure (Foldable)

> Cập nhật lúc: 2026-04-06 12:41:24

Sử dụng mũi tên để đóng/mở các phân cấp module.

<details open>
  <summary><b><a href='../module'>module (Root)</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure'>📁 infrastructure</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/devops'>📁 devops</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/devops/artifact-management'>📁 artifact-management</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/devops/artifact-management/central-portal'>🪄 central-portal</a>
</li>
<li>
  <a href='../module/infrastructure/devops/artifact-management/nexus'>🪄 nexus</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/infrastructure/devops/ci-cd'>🪄 ci-cd</a>
</li>
<li>
  <a href='../module/infrastructure/devops/docker'>🪄 docker</a>
</li>
<li>
  <a href='../module/infrastructure/devops/git'>🪄 git</a>
</li>
<li>
  <a href='../module/infrastructure/devops/jenkins'>🪄 jenkins</a>
</li>
<li>
  <a href='../module/infrastructure/devops/k8s'>🪄 k8s</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system'>📁 system</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database'>📁 database</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/advance/function'>🪄 function</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/advance/package'>🪄 package</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/advance/procedures'>🪄 procedures</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/connection-pool'>📁 connection-pool</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/connection-pool/hikariCP'>🪄 hikariCP</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/migration'>📁 migration</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/migration/flyway'>🪄 flyway</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/migration/liquibase'>🪄 liquibase</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/nosql'>📁 nosql</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/nosql/clickhouse'>🪄 clickhouse</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/nosql/elasticsearch'>🪄 elasticsearch</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/nosql/influxDB'>🪄 influxDB</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/nosql/loki'>🪄 loki</a>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/nosql/mongoDB'>📁 mongoDB</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/nosql/mongoDB/aggregation'>🪄 aggregation</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/infrastructure/system/database/nosql/neo4j'>🪄 neo4j</a>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/nosql/redis'>📁 redis</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/nosql/redis/lua-scripting'>🪄 lua-scripting</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/rdbms'>📁 rdbms</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/rdbms/mysql'>🪄 mysql</a>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/rdbms/oracle'>📁 oracle</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/rdbms/oracle/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/rdbms/oracle/advance/package'>🪄 package</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/rdbms/oracle/advance/procedure'>🪄 procedure</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/infrastructure/system/database/rdbms/oracle/setup'>🪄 setup</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/rdbms/postgresql'>📁 postgresql</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/database/rdbms/postgresql/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/database/rdbms/postgresql/advance/functions'>🪄 functions</a>
</li>
<li>
  <a href='../module/infrastructure/system/database/rdbms/postgresql/advance/trigger'>🪄 trigger</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/infrastructure/system/database/rdbms/postgresql/setup'>🪄 setup</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/network'>📁 network</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/network/api-gateway'>📁 api-gateway</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/network/api-gateway/kong'>🪄 kong</a>
</li>
<li>
  <a href='../module/infrastructure/system/network/api-gateway/spring-cloud-gateway'>🪄 spring-cloud-gateway</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/network/protocol'>📁 protocol</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/network/protocol/http'>🪄 http</a>
</li>
<li>
  <a href='../module/infrastructure/system/network/protocol/ssl-tls'>🪄 ssl-tls</a>
</li>
<li>
  <a href='../module/infrastructure/system/network/protocol/tcp-udp'>🪄 tcp-udp</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/network/proxy'>📁 proxy</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/network/proxy/haproxy'>🪄 haproxy</a>
</li>
<li>
  <a href='../module/infrastructure/system/network/proxy/nginx'>🪄 nginx</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/network/service-mesh'>📁 service-mesh</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/network/service-mesh/istio'>🪄 istio</a>
</li>
<li>
  <a href='../module/infrastructure/system/network/service-mesh/linkerd'>🪄 linkerd</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability'>📁 observability</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/benchmark'>📁 benchmark</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/benchmark/load-test'>📁 load-test</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/benchmark/load-test/jmeter'>🪄 jmeter</a>
</li>
<li>
  <a href='../module/infrastructure/system/observability/benchmark/load-test/k6'>🪄 k6</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/benchmark/micro'>📁 micro</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/benchmark/micro/jmh'>🪄 jmh</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/dianostic'>📁 dianostic</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/dianostic/profiling'>📁 profiling</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/dianostic/profiling/jprofiler'>🪄 jprofiler</a>
</li>
<li>
  <a href='../module/infrastructure/system/observability/dianostic/profiling/visual-vm'>🪄 visual-vm</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/dianostic/thread-dump'>📁 thread-dump</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/dianostic/thread-dump/jstack'>🪄 jstack</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/logging'>📁 logging</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/logging/event-log'>🪄 event-log</a>
</li>
<li>
  <a href='../module/infrastructure/system/observability/logging/log-pipeline'>🪄 log-pipeline</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/metrics'>📁 metrics</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/metrics/aggregation'>🪄 aggregation</a>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/metrics/time-series'>📁 time-series</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/metrics/time-series/prometheus'>🪄 prometheus</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/metrics/visualization'>📁 visualization</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/metrics/visualization/grafana'>🪄 grafana</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/infrastructure/system/observability/tracing'>📁 tracing</a></b></summary>
<ul>
<li>
  <a href='../module/infrastructure/system/observability/tracing/concept'>🪄 concept</a>
</li>
<li>
  <a href='../module/infrastructure/system/observability/tracing/tooling'>🪄 tooling</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/infrastructure/system/server'>🪄 server</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/integration'>📁 integration</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/integration/broker'>📁 broker</a></b></summary>
<ul>
<li>
  <a href='../module/integration/broker/activeMQ'>🪄 activeMQ</a>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/broker/kafka'>📁 kafka</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/integration/broker/kafka/service'>📁 service</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/integration/broker/kafka/service/consumer'>📁 consumer</a></b></summary>
<ul>
<li>
  <a href='../module/integration/broker/kafka/service/consumer/accountant'>🪄 accountant</a>
</li>
<li>
  <a href='../module/integration/broker/kafka/service/consumer/notification'>🪄 notification</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/integration/broker/kafka/service/control'>🪄 control</a>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/broker/kafka/service/producer'>📁 producer</a></b></summary>
<ul>
<li>
  <a href='../module/integration/broker/kafka/service/producer/bank'>🪄 bank</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/integration/broker/kafka/service/server'>🪄 server</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/integration/broker/rabbitMQ'>🪄 rabbitMQ</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/oneway'>📁 oneway</a></b></summary>
<ul>
<li>
  <a href='../module/integration/oneway/grpc'>🪄 grpc</a>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/oneway/httpRequest'>📁 httpRequest</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/integration/oneway/httpRequest/blocking'>📁 blocking</a></b></summary>
<ul>
<li>
  <a href='../module/integration/oneway/httpRequest/blocking/feign-client'>🪄 feign-client</a>
</li>
<li>
  <a href='../module/integration/oneway/httpRequest/blocking/http-url-connection'>🪄 http-url-connection</a>
</li>
<li>
  <a href='../module/integration/oneway/httpRequest/blocking/rest-template'>🪄 rest-template</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/oneway/httpRequest/nonBlocking'>📁 nonBlocking</a></b></summary>
<ul>
<li>
  <a href='../module/integration/oneway/httpRequest/nonBlocking/web-client'>🪄 web-client</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/integration/oneway/sse'>🪄 sse</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/integration/twoway'>📁 twoway</a></b></summary>
<ul>
<li>
  <a href='../module/integration/twoway/rsocket'>🪄 rsocket</a>
</li>
<li>
  <a href='../module/integration/twoway/websocket'>🪄 websocket</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/microservice'>📁 microservice</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/microservice/module'>📁 module</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/microservice/module/deployments'>📁 deployments</a></b></summary>
<ul>
<li>
  <a href='../module/microservice/module/deployments/docker'>🪄 docker</a>
</li>
<li>
  <a href='../module/microservice/module/deployments/grafana'>🪄 grafana</a>
</li>
<li>
  <a href='../module/microservice/module/deployments/prometheus'>🪄 prometheus</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/microservice/module/infrastructure'>📁 infrastructure</a></b></summary>
<ul>
<li>
  <a href='../module/microservice/module/infrastructure/admin-server'>🪄 admin-server</a>
</li>
<li>
  <a href='../module/microservice/module/infrastructure/api-gateway'>🪄 api-gateway</a>
</li>
<li>
  <a href='../module/microservice/module/infrastructure/config-server'>🪄 config-server</a>
</li>
<li>
  <a href='../module/microservice/module/infrastructure/eureka-server'>🪄 eureka-server</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/microservice/module/integration'>📁 integration</a></b></summary>
<ul>
<li>
  <a href='../module/microservice/module/integration/message-broker'>🪄 message-broker</a>
</li>
<li>
  <a href='../module/microservice/module/integration/notification-service'>🪄 notification-service</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/microservice/module/platform'>📁 platform</a></b></summary>
<ul>
<li>
  <a href='../module/microservice/module/platform/common-lib'>🪄 common-lib</a>
</li>
<li>
  <a href='../module/microservice/module/platform/logging-starter'>🪄 logging-starter</a>
</li>
<li>
  <a href='../module/microservice/module/platform/resilience4j'>🪄 resilience4j</a>
</li>
<li>
  <a href='../module/microservice/module/platform/security-starter'>🪄 security-starter</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/microservice/module/service'>📁 service</a></b></summary>
<ul>
<li>
  <a href='../module/microservice/module/service/inventory-service'>🪄 inventory-service</a>
</li>
<li>
  <a href='../module/microservice/module/service/order-service'>🪄 order-service</a>
</li>
<li>
  <a href='../module/microservice/module/service/payment-service'>🪄 payment-service</a>
</li>
<li>
  <a href='../module/microservice/module/service/product-service'>🪄 product-service</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform'>📁 platform</a></b></summary>
<ul>
<li>
  <a href='../module/platform/cloud'>🪄 cloud</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development'>📁 development</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/build-tool'>📁 build-tool</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/build-tool/ant'>🪄 ant</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/build-tool/gradle'>📁 gradle</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/build-tool/gradle/open-rewrite'>🪄 open-rewrite</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/build-tool/maven'>🪄 maven</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework'>📁 framework</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring'>📁 spring</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring/actuator'>🪄 actuator</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring/basic'>📁 basic</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring/basic/core'>📁 core</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring/basic/core/annotation'>🪄 annotation</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/basic/core/bean'>🪄 bean</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/basic/core/scope'>🪄 scope</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/framework/spring/basic/profile'>🪄 profile</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/basic/properties'>🪄 properties</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/framework/spring/conditional'>🪄 conditional</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring/data'>📁 data</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring/data/jpa'>🪄 jpa</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/framework/spring/devtool'>🪄 devtool</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/functional-endpoint'>🪄 functional-endpoint</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/global-exception-handler'>🪄 global-exception-handler</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/import'>🪄 import</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/profile'>🪄 profile</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring/security'>📁 security</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring/security/basic'>🪄 basic</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/security/jwt'>🪄 jwt</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/security/oauth'>🪄 oauth</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/framework/spring/swagger'>🪄 swagger</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/test-property-source'>🪄 test-property-source</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring/web'>🪄 web</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring-boot'>📁 spring-boot</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring-boot/auto-configuration'>🪄 auto-configuration</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring-boot/embedded-server'>🪄 embedded-server</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring-boot/externalized-configuration'>🪄 externalized-configuration</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring-boot/native-image'>🪄 native-image</a>
</li>
<li>
  <a href='../module/platform/development/framework/spring-boot/packaging'>🪄 packaging</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/framework/spring-boot/starter'>📁 starter</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/framework/spring-boot/starter/custom-starter'>🪄 custom-starter</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/framework/spring-boot/testing'>🪄 testing</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language'>📁 language</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java'>📁 java</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/advance'>🪄 advance</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/concurrency'>📁 concurrency</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/concurrency/async-programming'>🪄 async-programming</a>
</li>
<li>
  <a href='../module/platform/development/language/java/concurrency/executor-service'>🪄 executor-service</a>
</li>
<li>
  <a href='../module/platform/development/language/java/concurrency/high-level-utils'>🪄 high-level-utils</a>
</li>
<li>
  <a href='../module/platform/development/language/java/concurrency/virtual-threads'>🪄 virtual-threads</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/core'>📁 core</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/core/abstract-interface'>🪄 abstract-interface</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/mapping'>📁 mapping</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/mapping/mapstruct'>🪄 mapstruct</a>
</li>
<li>
  <a href='../module/platform/development/language/java/mapping/model-mapper'>🪄 model-mapper</a>
</li>
<li>
  <a href='../module/platform/development/language/java/mapping/object-mapper'>🪄 object-mapper</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/persistence'>📁 persistence</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/persistence/orm'>📁 orm</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/persistence/orm/hibernate'>🪄 hibernate</a>
</li>
<li>
  <a href='../module/platform/development/language/java/persistence/orm/jpa-specification'>🪄 jpa-specification</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/persistence/sql'>📁 sql</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/persistence/sql/jdbc'>🪄 jdbc</a>
</li>
<li>
  <a href='../module/platform/development/language/java/persistence/sql/mybatis'>🪄 mybatis</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/version'>📁 version</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/version/java11'>🪄 java11</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/language/java/version/java16'>📁 java16</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/language/java/version/java16/record'>🪄 record</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/language/java/version/java17'>🪄 java17</a>
</li>
<li>
  <a href='../module/platform/development/language/java/version/java7'>🪄 java7</a>
</li>
<li>
  <a href='../module/platform/development/language/java/version/java8'>🪄 java8</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/paradigm'>📁 paradigm</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/paradigm/aop'>🪄 aop</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/paradigm/concurrency'>📁 concurrency</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/paradigm/concurrency/batch'>🪄 batch</a>
</li>
<li>
  <a href='../module/platform/development/paradigm/concurrency/forkjoin-workstealing'>🪄 forkjoin-workstealing</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/paradigm/concurrency/schedule'>📁 schedule</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/paradigm/concurrency/schedule/task'>🪄 task</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/paradigm/concurrency/thread'>🪄 thread</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/paradigm/di-ioc'>🪄 di-ioc</a>
</li>
<li>
  <a href='../module/platform/development/paradigm/functional'>🪄 functional</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/paradigm/mvc-pattern'>📁 mvc-pattern</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/paradigm/mvc-pattern/controller'>🪄 controller</a>
</li>
<li>
  <a href='../module/platform/development/paradigm/mvc-pattern/model'>🪄 model</a>
</li>
<li>
  <a href='../module/platform/development/paradigm/mvc-pattern/service'>🪄 service</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/development/paradigm/object-oriented'>🪄 object-oriented</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/paradigm/reactive-stream'>📁 reactive-stream</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/paradigm/reactive-stream/webflux'>🪄 webflux</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/validation'>📁 validation</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/development/validation/performance'>📁 performance</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/validation/performance/benchmark'>🪄 benchmark</a>
</li>
<li>
  <a href='../module/platform/development/validation/performance/jmeter'>🪄 jmeter</a>
</li>
<li>
  <a href='../module/platform/development/validation/performance/load-test'>🪄 load-test</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/development/validation/testing'>📁 testing</a></b></summary>
<ul>
<li>
  <a href='../module/platform/development/validation/testing/e2e-test'>🪄 e2e-test</a>
</li>
<li>
  <a href='../module/platform/development/validation/testing/integration-test'>🪄 integration-test</a>
</li>
<li>
  <a href='../module/platform/development/validation/testing/unit-test'>🪄 unit-test</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/digital'>🪄 digital</a>
</li>
<li>
  <a href='../module/platform/social'>🪄 social</a>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/support'>📁 support</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/support/document'>📁 document</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='../module/platform/support/document/diagram'>📁 diagram</a></b></summary>
<ul>
<li>
  <a href='../module/platform/support/document/diagram/mermaid'>🪄 mermaid</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='../module/platform/support/document/excel'>🪄 excel</a>
</li>
<li>
  <a href='../module/platform/support/document/pdf'>🪄 pdf</a>
</li>
<li>
  <a href='../module/platform/support/document/word'>🪄 word</a>
</li>
<li>
  <a href='../module/platform/support/document/xml'>🪄 xml</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='../module/platform/support/notification'>📁 notification</a></b></summary>
<ul>
<li>
  <a href='../module/platform/support/notification/email'>🪄 email</a>
</li>
<li>
  <a href='../module/platform/support/notification/kakao'>🪄 kakao</a>
</li>
<li>
  <a href='../module/platform/support/notification/microsoft-team'>🪄 microsoft-team</a>
</li>
<li>
  <a href='../module/platform/support/notification/mobile-push'>🪄 mobile-push</a>
</li>
<li>
  <a href='../module/platform/support/notification/slack'>🪄 slack</a>
</li>
<li>
  <a href='../module/platform/support/notification/sms'>🪄 sms</a>
</li>
<li>
  <a href='../module/platform/support/notification/telegram'>🪄 telegram</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
</ul>
</details>
