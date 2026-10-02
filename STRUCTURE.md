# Project Structure

Sử dụng mũi tên để đóng/mở các phân cấp module.

<details open>
  <summary><b><a href='./module'>module (Root)</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure'>📁 infrastructure</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops'>📁 devops</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/artifact-management'>📁 artifact-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/artifact-management/maven-central'>🪄 maven-central</a>
</li>
<li>
  <a href='./module/infrastructure/devops/artifact-management/nexus'>🪄 nexus</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/ci-cd'>📁 ci-cd</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/ci-cd/fundamentals'>🪄 fundamentals</a>
</li>
<li>
  <a href='./module/infrastructure/devops/ci-cd/github-actions'>🪄 github-actions</a>
</li>
<li>
  <a href='./module/infrastructure/devops/ci-cd/gitlab-ci'>🪄 gitlab-ci</a>
</li>
<li>
  <a href='./module/infrastructure/devops/ci-cd/jenkins'>🪄 jenkins</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/configuration-management'>📁 configuration-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/configuration-management/ansible'>🪄 ansible</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/containerization'>📁 containerization</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/containerization/docker'>🪄 docker</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/deployment'>📁 deployment</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/deployment/deployment-strategy'>🪄 deployment-strategy</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/infrastructure/devops/environment-management'>🪄 environment-management</a>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/infrastructure-as-code'>📁 infrastructure-as-code</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/infrastructure-as-code/terraform'>🪄 terraform</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/orchestration'>📁 orchestration</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/orchestration/kubernetes'>🪄 kubernetes</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/devops/source-control'>📁 source-control</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/devops/source-control/git'>🪄 git</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system'>📁 system</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database'>📁 database</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/connection-management'>📁 connection-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/connection-management/hikari-cp'>🪄 hikari-cp</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine'>📁 engine</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/analytical'>📁 analytical</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/analytical/clickhouse'>🪄 clickhouse</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/document'>📁 document</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/document/mongodb'>📁 mongodb</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/document/mongodb/aggregation'>🪄 aggregation</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/graph'>📁 graph</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/graph/neo4j'>🪄 neo4j</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/key-value'>📁 key-value</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/key-value/redis'>📁 redis</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/key-value/redis/lua-scripting'>🪄 lua-scripting</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/relational'>📁 relational</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/mysql'>🪄 mysql</a>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/relational/oracle'>📁 oracle</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/relational/oracle/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/oracle/advance/package'>🪄 package</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/oracle/advance/procedure'>🪄 procedure</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/oracle/setup'>🪄 setup</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/relational/postgresql'>📁 postgresql</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/relational/postgresql/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/postgresql/advance/functions'>🪄 functions</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/postgresql/advance/trigger'>🪄 trigger</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/infrastructure/system/database/engine/relational/postgresql/setup'>🪄 setup</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/search'>📁 search</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/search/elasticsearch'>🪄 elasticsearch</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/engine/time-series'>📁 time-series</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/engine/time-series/influxdb'>🪄 influxdb</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/performance'>📁 performance</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/performance/database-caching'>🪄 database-caching</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/execution-plan'>🪄 execution-plan</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/indexing'>🪄 indexing</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/locking'>🪄 locking</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/partitioning'>🪄 partitioning</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/query-optimization'>🪄 query-optimization</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/performance/statistics'>🪄 statistics</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/database/schema-migration'>📁 schema-migration</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/database/schema-migration/flyway'>🪄 flyway</a>
</li>
<li>
  <a href='./module/infrastructure/system/database/schema-migration/liquibase'>🪄 liquibase</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network'>📁 network</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network/api-gateway'>📁 api-gateway</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/network/api-gateway/kong'>🪄 kong</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network/protocol'>📁 protocol</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/network/protocol/http'>🪄 http</a>
</li>
<li>
  <a href='./module/infrastructure/system/network/protocol/tcp-udp'>🪄 tcp-udp</a>
</li>
<li>
  <a href='./module/infrastructure/system/network/protocol/tls'>🪄 tls</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network/proxy'>📁 proxy</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/network/proxy/haproxy'>🪄 haproxy</a>
</li>
<li>
  <a href='./module/infrastructure/system/network/proxy/nginx'>🪄 nginx</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network/service-mesh'>📁 service-mesh</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/network/service-mesh/istio'>🪄 istio</a>
</li>
<li>
  <a href='./module/infrastructure/system/network/service-mesh/linkerd'>🪄 linkerd</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/network/traffic-management'>📁 traffic-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/network/traffic-management/rate-limiting'>🪄 rate-limiting</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability'>📁 observability</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/diagnostic'>📁 diagnostic</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/diagnostic/profiling'>📁 profiling</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/diagnostic/profiling/jprofiler'>🪄 jprofiler</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/diagnostic/profiling/visualvm'>🪄 visualvm</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/diagnostic/runtime-analysis'>📁 runtime-analysis</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/diagnostic/runtime-analysis/arthas'>🪄 arthas</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/diagnostic/runtime-monitoring'>📁 runtime-monitoring</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/diagnostic/runtime-monitoring/jconsole'>🪄 jconsole</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/diagnostic/thread-dump'>📁 thread-dump</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/diagnostic/thread-dump/jstack'>🪄 jstack</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging'>📁 logging</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging/event-log'>📁 event-log</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/logging/event-log/audit-log'>🪄 audit-log</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/event-log/business-log'>🪄 business-log</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/event-log/compliance'>🪄 compliance</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/event-log/security-event'>🪄 security-event</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging/log-pipeline'>📁 log-pipeline</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/logging/log-pipeline/fluentd'>🪄 fluentd</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/log-pipeline/logstash'>🪄 logstash</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/log-pipeline/promtail'>🪄 promtail</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging/sanitization'>📁 sanitization</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/logging/sanitization/masking'>🪄 masking</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/sanitization/redaction'>🪄 redaction</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging/storage-analysis'>📁 storage-analysis</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/logging/storage-analysis/elk-stack'>📁 elk-stack</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/logging/storage-analysis/elk-stack/kibana'>🪄 kibana</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/infrastructure/system/observability/logging/storage-analysis/loki-stack'>🪄 loki-stack</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/management-console'>📁 management-console</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/management-console/hawtio'>🪄 hawtio</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/metrics'>📁 metrics</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/metrics/aggregation'>🪄 aggregation</a>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/metrics/time-series'>📁 time-series</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/metrics/time-series/prometheus'>🪄 prometheus</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/metrics/visualization'>📁 visualization</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/metrics/visualization/grafana'>🪄 grafana</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/tracing'>📁 tracing</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/tracing/concept'>🪄 concept</a>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/observability/tracing/tooling'>📁 tooling</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/observability/tracing/tooling/datadog'>🪄 datadog</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/tracing/tooling/jaeger'>🪄 jaeger</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/tracing/tooling/tempo'>🪄 tempo</a>
</li>
<li>
  <a href='./module/infrastructure/system/observability/tracing/tooling/zipkin'>🪄 zipkin</a>
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
  <summary><b><a href='./module/infrastructure/system/runtime'>📁 runtime</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/runtime/servlet-container'>📁 servlet-container</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/runtime/servlet-container/jetty'>🪄 jetty</a>
</li>
<li>
  <a href='./module/infrastructure/system/runtime/servlet-container/tomcat'>🪄 tomcat</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/security'>📁 security</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/security/identity-access-management'>📁 identity-access-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/security/identity-access-management/keycloak'>🪄 keycloak</a>
</li>
<li>
  <a href='./module/infrastructure/system/security/identity-access-management/token-lifecycle'>🪄 token-lifecycle</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/infrastructure/system/security/secrets-management'>📁 secrets-management</a></b></summary>
<ul>
<li>
  <a href='./module/infrastructure/system/security/secrets-management/hashicorp-vault'>🪄 hashicorp-vault</a>
</li>
<li>
  <a href='./module/infrastructure/system/security/secrets-management/secret-lifecycle'>🪄 secret-lifecycle</a>
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
</li>
<li>
<details>
  <summary><b><a href='./module/integration'>📁 integration</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/http'>📁 http</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/http/request-response'>📁 request-response</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/http/request-response/client'>📁 client</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/http/request-response/client/java'>📁 java</a></b></summary>
<ul>
<li>
  <a href='./module/integration/http/request-response/client/java/http-client'>🪄 http-client</a>
</li>
<li>
  <a href='./module/integration/http/request-response/client/java/http-url-connection'>🪄 http-url-connection</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/http/request-response/client/spring-cloud'>📁 spring-cloud</a></b></summary>
<ul>
<li>
  <a href='./module/integration/http/request-response/client/spring-cloud/feign-client'>🪄 feign-client</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/http/request-response/client/spring-framework'>📁 spring-framework</a></b></summary>
<ul>
<li>
  <a href='./module/integration/http/request-response/client/spring-framework/rest-client'>🪄 rest-client</a>
</li>
<li>
  <a href='./module/integration/http/request-response/client/spring-framework/rest-template'>🪄 rest-template</a>
</li>
<li>
  <a href='./module/integration/http/request-response/client/spring-framework/web-client'>🪄 web-client</a>
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
  <a href='./module/integration/http/server-sent-events'>🪄 server-sent-events</a>
</li>
<li>
  <a href='./module/integration/http/webhook'>🪄 webhook</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/messaging'>📁 messaging</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/messaging/event-streaming'>📁 event-streaming</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/messaging/event-streaming/kafka'>📁 kafka</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/messaging/event-streaming/kafka/service'>📁 service</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/integration/messaging/event-streaming/kafka/service/consumer'>📁 consumer</a></b></summary>
<ul>
<li>
  <a href='./module/integration/messaging/event-streaming/kafka/service/consumer/accountant'>🪄 accountant</a>
</li>
<li>
  <a href='./module/integration/messaging/event-streaming/kafka/service/consumer/notification'>🪄 notification</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/integration/messaging/event-streaming/kafka/service/control'>🪄 control</a>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/messaging/event-streaming/kafka/service/producer'>📁 producer</a></b></summary>
<ul>
<li>
  <a href='./module/integration/messaging/event-streaming/kafka/service/producer/bank'>🪄 bank</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/integration/messaging/event-streaming/kafka/service/server'>🪄 server</a>
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
  <summary><b><a href='./module/integration/messaging/message-broker'>📁 message-broker</a></b></summary>
<ul>
<li>
  <a href='./module/integration/messaging/message-broker/activemq'>🪄 activemq</a>
</li>
<li>
  <a href='./module/integration/messaging/message-broker/rabbitmq'>🪄 rabbitmq</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/realtime'>📁 realtime</a></b></summary>
<ul>
<li>
  <a href='./module/integration/realtime/rsocket'>🪄 rsocket</a>
</li>
<li>
  <a href='./module/integration/realtime/websocket'>🪄 websocket</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/integration/rpc'>📁 rpc</a></b></summary>
<ul>
<li>
  <a href='./module/integration/rpc/grpc'>🪄 grpc</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice'>📁 microservice</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/microservice/module'>📁 module</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/microservice/module/deployments'>📁 deployments</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/deployments/docker'>🪄 docker</a>
</li>
<li>
  <a href='./module/microservice/module/deployments/prometheus-grafana'>🪄 prometheus-grafana</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice/module/infrastructure'>📁 infrastructure</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/infrastructure/admin-server'>🪄 admin-server</a>
</li>
<li>
  <a href='./module/microservice/module/infrastructure/api-gateway'>🪄 api-gateway</a>
</li>
<li>
  <a href='./module/microservice/module/infrastructure/config-server'>🪄 config-server</a>
</li>
<li>
  <a href='./module/microservice/module/infrastructure/eureka-client'>🪄 eureka-client</a>
</li>
<li>
  <a href='./module/microservice/module/infrastructure/eureka-server'>🪄 eureka-server</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice/module/integration'>📁 integration</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/integration/message-broker'>🪄 message-broker</a>
</li>
<li>
  <a href='./module/microservice/module/integration/notification-service'>🪄 notification-service</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice/module/platform'>📁 platform</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/platform/common-lib'>🪄 common-lib</a>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice/module/platform/logging-starter'>📁 logging-starter</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/platform/logging-starter/elk-stack'>🪄 elk-stack</a>
</li>
<li>
  <a href='./module/microservice/module/platform/logging-starter/loki'>🪄 loki</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/microservice/module/platform/resilience4j'>🪄 resilience4j</a>
</li>
<li>
  <a href='./module/microservice/module/platform/security-starter'>🪄 security-starter</a>
</li>
<li>
  <a href='./module/microservice/module/platform/temporal-starter'>🪄 temporal-starter</a>
</li>
<li>
  <a href='./module/microservice/module/platform/tracing'>🪄 tracing</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/microservice/module/service'>📁 service</a></b></summary>
<ul>
<li>
  <a href='./module/microservice/module/service/inventory-service'>🪄 inventory-service</a>
</li>
<li>
  <a href='./module/microservice/module/service/order-service'>🪄 order-service</a>
</li>
<li>
  <a href='./module/microservice/module/service/payment-service'>🪄 payment-service</a>
</li>
<li>
  <a href='./module/microservice/module/service/product-service'>🪄 product-service</a>
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
  <summary><b><a href='./module/platform'>📁 platform</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/cloud'>📁 cloud</a></b></summary>
<ul>
<li>
  <a href='./module/platform/cloud/alibaba'>🪄 alibaba</a>
</li>
<li>
  <a href='./module/platform/cloud/aws'>🪄 aws</a>
</li>
<li>
  <a href='./module/platform/cloud/azure'>🪄 azure</a>
</li>
<li>
  <a href='./module/platform/cloud/gcp'>🪄 gcp</a>
</li>
<li>
  <a href='./module/platform/cloud/ibm'>🪄 ibm</a>
</li>
<li>
  <a href='./module/platform/cloud/oracle'>🪄 oracle</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/code-quality'>📁 code-quality</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/code-quality/continuous-inspection'>📁 continuous-inspection</a></b></summary>
<ul>
<li>
  <a href='./module/platform/code-quality/continuous-inspection/sonarqube'>🪄 sonarqube</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/code-quality/formatting'>📁 formatting</a></b></summary>
<ul>
<li>
  <a href='./module/platform/code-quality/formatting/spotless'>🪄 spotless</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/code-quality/static-analysis'>📁 static-analysis</a></b></summary>
<ul>
<li>
  <a href='./module/platform/code-quality/static-analysis/checkstyle'>🪄 checkstyle</a>
</li>
<li>
  <a href='./module/platform/code-quality/static-analysis/error-prone'>🪄 error-prone</a>
</li>
<li>
  <a href='./module/platform/code-quality/static-analysis/pmd'>🪄 pmd</a>
</li>
<li>
  <a href='./module/platform/code-quality/static-analysis/spotbugs'>🪄 spotbugs</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development'>📁 development</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/data'>📁 data</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/data/data-mapping'>📁 data-mapping</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/data/data-mapping/object-mapping'>🪄 object-mapping</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/data/persistence'>📁 persistence</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/data/persistence/orm'>📁 orm</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/data/persistence/orm/hibernate'>🪄 hibernate</a>
</li>
<li>
  <a href='./module/platform/development/data/persistence/orm/jpa'>🪄 jpa</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/data/persistence/relational-access'>📁 relational-access</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/data/persistence/relational-access/jdbc'>🪄 jdbc</a>
</li>
<li>
  <a href='./module/platform/development/data/persistence/relational-access/mybatis'>🪄 mybatis</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/data/serialization'>📁 serialization</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/data/serialization/jackson'>🪄 jackson</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering'>📁 engineering</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool'>📁 build-tool</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/build-tool/ant'>🪄 ant</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle'>📁 gradle</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/cache'>🪄 cache</a>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/open-rewrite'>🪄 open-rewrite</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle/plugin'>📁 plugin</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle/plugin/development'>📁 development</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/internal-plugin'>📁 internal-plugin</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/internal-plugin/build-src'>🪄 build-src</a>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/internal-plugin/convention-plugin'>🪄 convention-plugin</a>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/internal-plugin/included-plugin'>🪄 included-plugin</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/published-plugin'>📁 published-plugin</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/published-plugin/gradle-plugin-portal'>🪄 gradle-plugin-portal</a>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/development/published-plugin/maven-repository'>🪄 maven-repository</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/build-tool/gradle/plugin/usage'>📁 usage</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/usage/core-plugin'>🪄 core-plugin</a>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/plugin/usage/development-plugin'>🪄 development-plugin</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/gradle/task'>🪄 task</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/engineering/build-tool/maven'>🪄 maven</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation'>📁 validation</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/benchmark'>📁 benchmark</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/benchmark/microbenchmark'>📁 microbenchmark</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/validation/benchmark/microbenchmark/jmh'>🪄 jmh</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/benchmark/system-benchmark'>🪄 system-benchmark</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/performance'>📁 performance</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/validation/performance/benchmark'>🪄 benchmark</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/performance/jmeter'>🪄 jmeter</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/performance/load-test'>🪄 load-test</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing'>📁 testing</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing/functional'>📁 functional</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/validation/testing/functional/e2e-test'>🪄 e2e-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/functional/integration-test'>🪄 integration-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/functional/unit-test'>🪄 unit-test</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing/non-functional'>📁 non-functional</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test'>📁 performance-test</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept'>📁 concept</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/endurance-test'>🪄 endurance-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/load-test'>🪄 load-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/scalability-test'>🪄 scalability-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/spike-test'>🪄 spike-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/stress-test'>🪄 stress-test</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/performance-test/concept/volume-test'>🪄 volume-test</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/engineering/validation/testing/non-functional/tooling'>📁 tooling</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/tooling/gatling'>🪄 gatling</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/tooling/jmeter'>🪄 jmeter</a>
</li>
<li>
  <a href='./module/platform/development/engineering/validation/testing/non-functional/tooling/k6'>🪄 k6</a>
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
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming'>📁 programming</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework'>📁 framework</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-ai'>🪄 spring-ai</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-amqp'>🪄 spring-amqp</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-batch'>🪄 spring-batch</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/spring-boot'>📁 spring-boot</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/actuator'>🪄 actuator</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/auto-configuration'>🪄 auto-configuration</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/externalized-configuration'>🪄 externalized-configuration</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/fundamentals'>🪄 fundamentals</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/native-image'>🪄 native-image</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-boot/testing'>🪄 testing</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/spring-cloud'>📁 spring-cloud</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-cloud/gateway'>🪄 gateway</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/spring-data'>📁 spring-data</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-data/jdbc'>🪄 jdbc</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-data/jpa'>🪄 jpa</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-data/mongodb'>🪄 mongodb</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-data/r2dbc'>🪄 r2dbc</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-data/redis'>🪄 redis</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/spring-framework'>📁 spring-framework</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/aspect'>🪄 aspect</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/concurrency'>🪄 concurrency</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/core-container'>🪄 core-container</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/global-exception-handler'>🪄 global-exception-handler</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/reactive'>🪄 reactive</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/testing'>🪄 testing</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-framework/web'>🪄 web</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-graphql'>🪄 spring-graphql</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-grpc'>🪄 spring-grpc</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-hateoas'>🪄 spring-hateoas</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-integration'>🪄 spring-integration</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-kafka'>🪄 spring-kafka</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-ldap'>🪄 spring-ldap</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-modulith'>🪄 spring-modulith</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-rest-docs'>🪄 spring-rest-docs</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/spring-security'>📁 spring-security</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/spring-security/fundamentals'>🪄 fundamentals</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-security/oauth2'>🪄 oauth2</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-session'>🪄 spring-session</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-shell'>🪄 spring-shell</a>
</li>
<li>
  <a href='./module/platform/development/programming/framework/spring-web-services'>🪄 spring-web-services</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/framework/springdoc'>📁 springdoc</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/framework/springdoc/openapi'>🪄 openapi</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language'>📁 language</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/domain-specific-language'>🪄 domain-specific-language</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java'>📁 java</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/advance'>📁 advance</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/advance/jvm'>🪄 jvm</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/dynamic-runtime'>🪄 dynamic-runtime</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/runtime-extensibility'>🪄 runtime-extensibility</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/networking'>🪄 networking</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/security-cryptography'>🪄 security-cryptography</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/native-interoperability'>🪄 native-interoperability</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/instrumentation'>🪄 instrumentation</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/advance/runtime-diagnostics'>🪄 runtime-diagnostics</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/concurrency'>📁 concurrency</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/fundamentals'>🪄 fundamentals</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/high-level-utils'>🪄 high-level-utils</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/executor-service'>🪄 executor-service</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/fork-join'>🪄 fork-join</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/async-programming'>🪄 async-programming</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/concurrency/virtual-threads'>🪄 virtual-threads</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/core'>📁 core</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/core/language-basics'>🪄 language-basics</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/numbers'>🪄 numbers</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/class-object'>🪄 class-object</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/oop'>🪄 oop</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/abstract-interface'>🪄 abstract-interface</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/object-contract'>🪄 object-contract</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/string'>🪄 string</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/exception'>🪄 exception</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/generics'>🪄 generics</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/collection'>🪄 collection</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/annotation'>🪄 annotation</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/reflection'>🪄 reflection</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/classloader'>🪄 classloader</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/date-time'>🪄 date-time</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/io'>🪄 io</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/localization'>🪄 localization</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/core/functional-programming'>🪄 functional-programming</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version'>📁 version</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java10'>📁 java10</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java10/application-class-data-sharing'>🪄 application-class-data-sharing</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java10/container-awareness'>🪄 container-awareness</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java10/local-variable-type-inference'>🪄 local-variable-type-inference</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java10/parallel-full-gc-for-g1'>🪄 parallel-full-gc-for-g1</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java11'>📁 java11</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/deployment-stack-removal'>🪄 deployment-stack-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/epsilon-gc'>🪄 epsilon-gc</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/flight-recorder'>🪄 flight-recorder</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/http-client'>🪄 http-client</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/java-ee-corba-removal'>🪄 java-ee-corba-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/lambda-parameter-var-syntax'>🪄 lambda-parameter-var-syntax</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/single-file-source-code-launch'>🪄 single-file-source-code-launch</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/tls13'>🪄 tls13</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java11/zgc-experimental'>🪄 zgc-experimental</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java14'>📁 java14</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/cms-gc-removal'>🪄 cms-gc-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/helpful-null-pointer-exceptions'>🪄 helpful-null-pointer-exceptions</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/jfr-event-streaming'>🪄 jfr-event-streaming</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/numa-aware-g1'>🪄 numa-aware-g1</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/pack200-removal'>🪄 pack200-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java14/switch-expressions'>🪄 switch-expressions</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java15'>📁 java15</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/biased-locking-disabled'>🪄 biased-locking-disabled</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/eddsa'>🪄 eddsa</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/hidden-classes'>🪄 hidden-classes</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/nashorn-removal'>🪄 nashorn-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/shenandoah-production'>🪄 shenandoah-production</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/solaris-sparc-port-removal'>🪄 solaris-sparc-port-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/text-blocks'>🪄 text-blocks</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java15/zgc-production'>🪄 zgc-production</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java16'>📁 java16</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/elastic-metaspace'>🪄 elastic-metaspace</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/jpackage'>🪄 jpackage</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/pattern-matching-instanceof'>🪄 pattern-matching-instanceof</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/record'>🪄 record</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/strong-encapsulation-by-default'>🪄 strong-encapsulation-by-default</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java16/unix-domain-socket-channels'>🪄 unix-domain-socket-channels</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java17'>📁 java17</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/context-specific-deserialization-filters'>🪄 context-specific-deserialization-filters</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/enhanced-prng'>🪄 enhanced-prng</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/macos-aarch64-port'>🪄 macos-aarch64-port</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/sealed-classes'>🪄 sealed-classes</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/security-manager-deprecated-for-removal'>🪄 security-manager-deprecated-for-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/strict-floating-point-semantics'>🪄 strict-floating-point-semantics</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java17/strong-encapsulation'>🪄 strong-encapsulation</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java18'>📁 java18</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/core-reflection-method-handles'>🪄 core-reflection-method-handles</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/finalization-deprecated-for-removal'>🪄 finalization-deprecated-for-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/inet-address-resolver-spi'>🪄 inet-address-resolver-spi</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/javadoc-code-snippets'>🪄 javadoc-code-snippets</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/simple-web-server'>🪄 simple-web-server</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java18/utf8-by-default'>🪄 utf8-by-default</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java21'>📁 java21</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/generational-zgc'>🪄 generational-zgc</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/kem-api'>🪄 kem-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/pattern-matching-switch'>🪄 pattern-matching-switch</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/record-patterns'>🪄 record-patterns</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/sequenced-collections'>🪄 sequenced-collections</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java21/virtual-threads'>🪄 virtual-threads</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java22'>📁 java22</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java22/foreign-function-memory-api'>🪄 foreign-function-memory-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java22/g1-region-pinning'>🪄 g1-region-pinning</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java22/multi-file-source-code-launch'>🪄 multi-file-source-code-launch</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java22/unnamed-variables-and-patterns'>🪄 unnamed-variables-and-patterns</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java23'>📁 java23</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java23/generational-zgc-default'>🪄 generational-zgc-default</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java23/markdown-documentation-comments'>🪄 markdown-documentation-comments</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java24'>📁 java24</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/aot-class-loading-linking'>🪄 aot-class-loading-linking</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/class-file-api'>🪄 class-file-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/generational-zgc-only'>🪄 generational-zgc-only</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/ml-dsa'>🪄 ml-dsa</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/ml-kem'>🪄 ml-kem</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/security-manager-disabled'>🪄 security-manager-disabled</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/stream-gatherers'>🪄 stream-gatherers</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java24/virtual-thread-synchronization'>🪄 virtual-thread-synchronization</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java25'>📁 java25</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java25/compact-object-headers'>🪄 compact-object-headers</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java25/compact-source-files-instance-main-methods'>🪄 compact-source-files-instance-main-methods</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java25/flexible-constructor-bodies'>🪄 flexible-constructor-bodies</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java25/module-import-declarations'>🪄 module-import-declarations</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java25/scoped-values'>🪄 scoped-values</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java26'>📁 java26</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java26/aot-object-caching-any-gc'>🪄 aot-object-caching-any-gc</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java26/applet-api-removal'>🪄 applet-api-removal</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java26/final-field-mutation-warnings'>🪄 final-field-mutation-warnings</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java26/g1-throughput'>🪄 g1-throughput</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java26/http3-client'>🪄 http3-client</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java27'>📁 java27</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java27/compact-object-headers-default'>🪄 compact-object-headers-default</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java27/g1-default-all-environments'>🪄 g1-default-all-environments</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java27/jfr-in-process-data-redaction'>🪄 jfr-in-process-data-redaction</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java27/post-quantum-hybrid-tls'>🪄 post-quantum-hybrid-tls</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java5'>📁 java5</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/annotations'>🪄 annotations</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/autoboxing-unboxing'>🪄 autoboxing-unboxing</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/class-data-sharing'>🪄 class-data-sharing</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/concurrency-utilities'>🪄 concurrency-utilities</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/enhanced-for-loop'>🪄 enhanced-for-loop</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/enums'>🪄 enums</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/generics'>🪄 generics</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/static-import'>🪄 static-import</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java5/varargs'>🪄 varargs</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java6'>📁 java6</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java6/annotation-processing-api'>🪄 annotation-processing-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java6/compiler-api'>🪄 compiler-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java6/jdbc4'>🪄 jdbc4</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java6/scripting-api'>🪄 scripting-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java6/service-loader'>🪄 service-loader</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java7'>📁 java7</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/binary-literals'>🪄 binary-literals</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/diamond-operator'>🪄 diamond-operator</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/fork-join'>🪄 fork-join</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/invokedynamic'>🪄 invokedynamic</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/method-handles'>🪄 method-handles</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/multi-catch'>🪄 multi-catch</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/nio2'>🪄 nio2</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/precise-rethrow'>🪄 precise-rethrow</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/strings-in-switch'>🪄 strings-in-switch</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/try-with-resources'>🪄 try-with-resources</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java7/underscores-in-numeric-literals'>🪄 underscores-in-numeric-literals</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java8'>📁 java8</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/base64-api'>🪄 base64-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/completable-future'>🪄 completable-future</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/date-time-api'>🪄 date-time-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/default-interface-methods'>🪄 default-interface-methods</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/functional-interfaces'>🪄 functional-interfaces</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/lambda-expressions'>🪄 lambda-expressions</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/metaspace'>🪄 metaspace</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/method-references'>🪄 method-references</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/nashorn-javascript-engine'>🪄 nashorn-javascript-engine</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/optional'>🪄 optional</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/repeatable-annotations'>🪄 repeatable-annotations</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/stream-api'>🪄 stream-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java8/type-annotations'>🪄 type-annotations</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/language/java/version/java9'>📁 java9</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/collection-factory-methods'>🪄 collection-factory-methods</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/compact-strings'>🪄 compact-strings</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/flow-api'>🪄 flow-api</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/g1-default-gc'>🪄 g1-default-gc</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/jlink'>🪄 jlink</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/jshell'>🪄 jshell</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/module-system'>🪄 module-system</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/multi-release-jar'>🪄 multi-release-jar</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/private-interface-methods'>🪄 private-interface-methods</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/process-api-updates'>🪄 process-api-updates</a>
</li>
<li>
  <a href='./module/platform/development/programming/language/java/version/java9/stack-walking-api'>🪄 stack-walking-api</a>
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
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/programming/paradigm'>📁 paradigm</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/programming/paradigm/aop'>🪄 aop</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/data-oriented'>🪄 data-oriented</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/declarative'>🪄 declarative</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/functional'>🪄 functional</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/imperative'>🪄 imperative</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/object-oriented'>🪄 object-oriented</a>
</li>
<li>
  <a href='./module/platform/development/programming/paradigm/reactive'>🪄 reactive</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design'>📁 software-design</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture'>📁 architecture</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/architecture/fundamentals'>🪄 fundamentals</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture/system-architecture'>📁 system-architecture</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture/system-architecture/monolithic'>📁 monolithic</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/architecture/system-architecture/monolithic/modular-monolith'>🪄 modular-monolith</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/system-architecture/microservices'>🪄 microservices</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture/architectural-pattern'>📁 architectural-pattern</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/layered'>🪄 layered</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/mvc'>🪄 mvc</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/hexagonal'>🪄 hexagonal</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/clean-architecture'>🪄 clean-architecture</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/onion-architecture'>🪄 onion-architecture</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/client-server'>🪄 client-server</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/event-driven'>🪄 event-driven</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/microkernel'>🪄 microkernel</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/pipes-and-filters'>🪄 pipes-and-filters</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/architectural-pattern/service-oriented-architecture'>🪄 service-oriented-architecture</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture/distributed-system'>📁 distributed-system</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/architecture/distributed-system/bulkhead'>🪄 bulkhead</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/distributed-system/cqrs'>🪄 cqrs</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/distributed-system/event-sourcing'>🪄 event-sourcing</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/distributed-system/saga'>🪄 saga</a>
</li>
<li>
  <a href='./module/platform/development/software-design/architecture/distributed-system/sidecar'>🪄 sidecar</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/architecture/domain-modeling'>📁 domain-modeling</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/architecture/domain-modeling/domain-driven-design'>🪄 domain-driven-design</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-pattern'>📁 design-pattern</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-pattern/behavioral'>📁 behavioral</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/chain-of-responsibility'>🪄 chain-of-responsibility</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/command'>🪄 command</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/interpreter'>🪄 interpreter</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/iterator'>🪄 iterator</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/mediator'>🪄 mediator</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/memento'>🪄 memento</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/observer'>🪄 observer</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/state'>🪄 state</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/strategy'>🪄 strategy</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/template-method'>🪄 template-method</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/behavioral/visitor'>🪄 visitor</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-pattern/creational'>📁 creational</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-pattern/creational/abstract-factory'>🪄 abstract-factory</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/creational/builder'>🪄 builder</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/creational/factory-method'>🪄 factory-method</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/creational/prototype'>🪄 prototype</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/creational/singleton'>🪄 singleton</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/fundamentals'>🪄 fundamentals</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-pattern/structural'>📁 structural</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/adapter'>🪄 adapter</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/bridge'>🪄 bridge</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/composite'>🪄 composite</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/decorator'>🪄 decorator</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/facade'>🪄 facade</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/flyweight'>🪄 flyweight</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-pattern/structural/proxy'>🪄 proxy</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-principle'>📁 design-principle</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-principle/solid-principles'>📁 solid-principles</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-principle/solid-principles/single-responsibility'>🪄 single-responsibility</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/solid-principles/open-closed'>🪄 open-closed</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/solid-principles/liskov-substitution'>🪄 liskov-substitution</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/solid-principles/interface-segregation'>🪄 interface-segregation</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/solid-principles/dependency-inversion'>🪄 dependency-inversion</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/separation-of-concerns'>🪄 separation-of-concerns</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/loose-coupling'>🪄 loose-coupling</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/high-cohesion'>🪄 high-cohesion</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/composition-over-inheritance'>🪄 composition-over-inheritance</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/law-of-demeter'>🪄 law-of-demeter</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/dry'>🪄 dry</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/kiss'>🪄 kiss</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-principle/yagni'>🪄 yagni</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-technique'>📁 design-technique</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-technique/dependency-injection'>🪄 dependency-injection</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-design/design-problem'>📁 design-problem</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-design/design-problem/anti-pattern'>🪄 anti-pattern</a>
</li>
<li>
  <a href='./module/platform/development/software-design/design-problem/code-smell'>🪄 code-smell</a>
</li>
</ul>
</details>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-process'>📁 software-process</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/development/software-process/methodology'>📁 methodology</a></b></summary>
<ul>
<li>
  <a href='./module/platform/development/software-process/methodology/agile-development'>🪄 agile-development</a>
</li>
<li>
  <a href='./module/platform/development/software-process/methodology/driven-development'>🪄 driven-development</a>
</li>
<li>
  <a href='./module/platform/development/software-process/methodology/lifecycle-models'>🪄 lifecycle-models</a>
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
  <a href='./module/platform/digital'>🪄 digital</a>
</li>
<li>
  <a href='./module/platform/social'>🪄 social</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/support'>📁 support</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/support/document'>📁 document</a></b></summary>
<ul>
<li>
<details>
  <summary><b><a href='./module/platform/support/document/diagram'>📁 diagram</a></b></summary>
<ul>
<li>
  <a href='./module/platform/support/document/diagram/mermaid'>🪄 mermaid</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/support/document/excel'>🪄 excel</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/support/document/markdown'>📁 markdown</a></b></summary>
<ul>
<li>
  <a href='./module/platform/support/document/markdown/flexmark'>🪄 flexmark</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/support/document/pdf'>🪄 pdf</a>
</li>
<li>
  <a href='./module/platform/support/document/reporting'>🪄 reporting</a>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/support/document/translate'>📁 translate</a></b></summary>
<ul>
<li>
  <a href='./module/platform/support/document/translate/argos'>🪄 argos</a>
</li>
<li>
  <a href='./module/platform/support/document/translate/azure'>🪄 azure</a>
</li>
<li>
  <a href='./module/platform/support/document/translate/libre'>🪄 libre</a>
</li>
</ul>
</details>
</li>
<li>
  <a href='./module/platform/support/document/word'>🪄 word</a>
</li>
<li>
  <a href='./module/platform/support/document/xml'>🪄 xml</a>
</li>
</ul>
</details>
</li>
<li>
<details>
  <summary><b><a href='./module/platform/support/notification'>📁 notification</a></b></summary>
<ul>
<li>
  <a href='./module/platform/support/notification/email'>🪄 email</a>
</li>
<li>
  <a href='./module/platform/support/notification/kakao'>🪄 kakao</a>
</li>
<li>
  <a href='./module/platform/support/notification/microsoft-team'>🪄 microsoft-team</a>
</li>
<li>
  <a href='./module/platform/support/notification/mobile-push'>🪄 mobile-push</a>
</li>
<li>
  <a href='./module/platform/support/notification/slack'>🪄 slack</a>
</li>
<li>
  <a href='./module/platform/support/notification/sms'>🪄 sms</a>
</li>
<li>
  <a href='./module/platform/support/notification/telegram'>🪄 telegram</a>
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
