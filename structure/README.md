# Project Module Structure (Foldable)

> Cập nhật lúc: 2026-03-20 17:37:48

Sử dụng mũi tên để đóng/mở các phân cấp module.

<details open>
  <summary><b><a href='../module'>module (Root)</a></b></summary>
  <details style='margin-left: 5px'>
    <summary><b><a href='../module/infrastructure'>infrastructure</a></b></summary>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/infrastructure/devops'>devops</a></b></summary>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/infrastructure/devops/artifact-management'>artifact-management</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/infrastructure/devops/artifact-management/central-portal'>central-portal</a></div>
        <div style='margin-left: 20px'>• <a href='../module/infrastructure/devops/artifact-management/nexus'>nexus</a></div>
      </details>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/devops/ci-cd'>ci-cd</a></div>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/devops/docker'>docker</a></div>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/devops/git'>git</a></div>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/devops/jenkins'>jenkins</a></div>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/devops/k8s'>k8s</a></div>
    </details>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/infrastructure/system'>system</a></b></summary>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/infrastructure/system/database'>database</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/database/advance'>advance</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/advance/function'>function</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/advance/package'>package</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/advance/procedures'>procedures</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/database/connection-pool'>connection-pool</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/connection-pool/hikariCP'>hikariCP</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/database/nosql'>nosql</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/nosql/clickhouse'>clickhouse</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/nosql/elasticsearch'>elasticsearch</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/nosql/influxDB'>influxDB</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/nosql/loki'>loki</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/database/nosql/mongoDB'>mongoDB</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/database/nosql/mongoDB/aggregation'>aggregation</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/nosql/neo4j'>neo4j</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/database/nosql/redis'>redis</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/database/nosql/redis/lua-scripting'>lua-scripting</a></div>
          </details>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/database/rdbms'>rdbms</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/database/rdbms/mysql'>mysql</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/database/rdbms/oracle'>oracle</a></b></summary>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/infrastructure/system/database/rdbms/oracle/advance'>advance</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/infrastructure/system/database/rdbms/oracle/advance/package'>package</a></div>
              <div style='margin-left: 35px'>• <a href='../module/infrastructure/system/database/rdbms/oracle/advance/procedure'>procedure</a></div>
            </details>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/database/rdbms/oracle/setup'>setup</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/database/rdbms/postgresql'>postgresql</a></b></summary>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/infrastructure/system/database/rdbms/postgresql/advance'>advance</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/infrastructure/system/database/rdbms/postgresql/advance/functions'>functions</a></div>
              <div style='margin-left: 35px'>• <a href='../module/infrastructure/system/database/rdbms/postgresql/advance/trigger'>trigger</a></div>
            </details>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/database/rdbms/postgresql/setup'>setup</a></div>
          </details>
        </details>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/infrastructure/system/network'>network</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/network/api-gateway'>api-gateway</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/api-gateway/kong'>kong</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/api-gateway/spring-cloud-gateway'>spring-cloud-gateway</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/network/protocol'>protocol</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/protocol/http'>http</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/protocol/ssl-tls'>ssl-tls</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/protocol/tcp-udp'>tcp-udp</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/network/proxy'>proxy</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/proxy/haproxy'>haproxy</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/proxy/nginx'>nginx</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/network/service-mesh'>service-mesh</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/service-mesh/istio'>istio</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/network/service-mesh/linkerd'>linkerd</a></div>
        </details>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/infrastructure/system/observability'>observability</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/observability/benchmark'>benchmark</a></b></summary>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/benchmark/load-test'>load-test</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/benchmark/load-test/jmeter'>jmeter</a></div>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/benchmark/load-test/k6'>k6</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/benchmark/micro'>micro</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/benchmark/micro/jmh'>jmh</a></div>
          </details>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/observability/dianostic'>dianostic</a></b></summary>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/dianostic/profiling'>profiling</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/dianostic/profiling/jprofiler'>jprofiler</a></div>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/dianostic/profiling/visual-vm'>visual-vm</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/dianostic/thread-dump'>thread-dump</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/dianostic/thread-dump/jstack'>jstack</a></div>
          </details>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/observability/logging'>logging</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/observability/logging/event-log'>event-log</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/observability/logging/log-pipeline'>log-pipeline</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/observability/metrics'>metrics</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/observability/metrics/aggregation'>aggregation</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/metrics/time-series'>time-series</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/metrics/time-series/prometheus'>prometheus</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/infrastructure/system/observability/metrics/visualization'>visualization</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/infrastructure/system/observability/metrics/visualization/grafana'>grafana</a></div>
          </details>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/infrastructure/system/observability/tracing'>tracing</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/observability/tracing/concept'>concept</a></div>
          <div style='margin-left: 25px'>• <a href='../module/infrastructure/system/observability/tracing/tooling'>tooling</a></div>
        </details>
      </details>
      <div style='margin-left: 15px'>• <a href='../module/infrastructure/system/server'>server</a></div>
    </details>
  </details>
  <details style='margin-left: 5px'>
    <summary><b><a href='../module/integration'>integration</a></b></summary>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/integration/broker'>broker</a></b></summary>
      <div style='margin-left: 15px'>• <a href='../module/integration/broker/activeMQ'>activeMQ</a></div>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/integration/broker/kafka'>kafka</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/integration/broker/kafka/service'>service</a></b></summary>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/integration/broker/kafka/service/consumer'>consumer</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/integration/broker/kafka/service/consumer/accountant'>accountant</a></div>
            <div style='margin-left: 30px'>• <a href='../module/integration/broker/kafka/service/consumer/notification'>notification</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/integration/broker/kafka/service/control'>control</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/integration/broker/kafka/service/producer'>producer</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/integration/broker/kafka/service/producer/bank'>bank</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/integration/broker/kafka/service/server'>server</a></div>
        </details>
      </details>
      <div style='margin-left: 15px'>• <a href='../module/integration/broker/rabbitMQ'>rabbitMQ</a></div>
    </details>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/integration/oneway'>oneway</a></b></summary>
      <div style='margin-left: 15px'>• <a href='../module/integration/oneway/grpc'>grpc</a></div>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/integration/oneway/httpRequest'>httpRequest</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/integration/oneway/httpRequest/blocking'>blocking</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/integration/oneway/httpRequest/blocking/feign-client'>feign-client</a></div>
          <div style='margin-left: 25px'>• <a href='../module/integration/oneway/httpRequest/blocking/http-url-connection'>http-url-connection</a></div>
          <div style='margin-left: 25px'>• <a href='../module/integration/oneway/httpRequest/blocking/rest-template'>rest-template</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/integration/oneway/httpRequest/nonBlocking'>nonBlocking</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/integration/oneway/httpRequest/nonBlocking/web-client'>web-client</a></div>
        </details>
      </details>
      <div style='margin-left: 15px'>• <a href='../module/integration/oneway/sse'>sse</a></div>
    </details>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/integration/twoway'>twoway</a></b></summary>
      <div style='margin-left: 15px'>• <a href='../module/integration/twoway/rsocket'>rsocket</a></div>
      <div style='margin-left: 15px'>• <a href='../module/integration/twoway/websocket'>websocket</a></div>
    </details>
  </details>
  <details style='margin-left: 5px'>
    <summary><b><a href='../module/microservice'>microservice</a></b></summary>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/microservice/module'>module</a></b></summary>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/microservice/module/deployments'>deployments</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/deployments/docker'>docker</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/deployments/grafana'>grafana</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/deployments/prometheus'>prometheus</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/microservice/module/infrastructure'>infrastructure</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/infrastructure/admin-server'>admin-server</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/infrastructure/api-gateway'>api-gateway</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/infrastructure/config-server'>config-server</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/infrastructure/discovery-server'>discovery-server</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/microservice/module/integration'>integration</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/integration/message-broker'>message-broker</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/integration/notification-service'>notification-service</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/microservice/module/platform'>platform</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/platform/common-lib'>common-lib</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/platform/logging-starter'>logging-starter</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/platform/resilience4j'>resilience4j</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/platform/security-starter'>security-starter</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/microservice/module/service'>service</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/service/inventory-service'>inventory-service</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/service/order-service'>order-service</a></div>
        <div style='margin-left: 20px'>• <a href='../module/microservice/module/service/payment-service'>payment-service</a></div>
      </details>
    </details>
  </details>
  <details style='margin-left: 5px'>
    <summary><b><a href='../module/platform'>platform</a></b></summary>
    <div style='margin-left: 10px'>• <a href='../module/platform/cloud'>cloud</a></div>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/platform/development'>development</a></b></summary>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/development/build-tool'>build-tool</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/build-tool/ant'>ant</a></div>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/build-tool/gradle'>gradle</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/build-tool/gradle/open-rewrite'>open-rewrite</a></div>
        </details>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/build-tool/maven'>maven</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/development/framework'>framework</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/framework/spring'>spring</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/actuator'>actuator</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/framework/spring/basic'>basic</a></b></summary>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/platform/development/framework/spring/basic/core'>core</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/framework/spring/basic/core/annotation'>annotation</a></div>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/framework/spring/basic/core/bean'>bean</a></div>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/framework/spring/basic/core/scope'>scope</a></div>
            </details>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/basic/profile'>profile</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/basic/properties'>properties</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/conditional'>conditional</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/framework/spring/data'>data</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/data/jpa'>jpa</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/data/nosql'>nosql</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/devtool'>devtool</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/import'>import</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/profile'>profile</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/framework/spring/security'>security</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/security/basic'>basic</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/security/jwt'>jwt</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring/security/oauth'>oauth</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/swagger'>swagger</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/testPropertySource'>testPropertySource</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring/web'>web</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/framework/spring-boot'>spring-boot</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/auto-configuration'>auto-configuration</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/embedded-server'>embedded-server</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/externalized-configuration'>externalized-configuration</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/native-image'>native-image</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/packaging'>packaging</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/framework/spring-boot/starter'>starter</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/framework/spring-boot/starter/custom-starter'>custom-starter</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/framework/spring-boot/testing'>testing</a></div>
        </details>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/development/language'>language</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/language/java'>java</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/language/java/advance'>advance</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/language/java/concurrency'>concurrency</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/concurrency/async-programming'>async-programming</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/concurrency/executor-service'>executor-service</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/concurrency/high-level-utils'>high-level-utils</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/concurrency/virtual-threads'>virtual-threads</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/language/java/core'>core</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/core/abstract-interface'>abstract-interface</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/language/java/mapping'>mapping</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/mapping/model-mapper'>model-mapper</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/mapping/object-mapper'>object-mapper</a></div>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/language/java/persistence'>persistence</a></b></summary>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/platform/development/language/java/persistence/orm'>orm</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/language/java/persistence/orm/hibernate'>hibernate</a></div>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/language/java/persistence/orm/jpa-specification'>jpa-specification</a></div>
            </details>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/platform/development/language/java/persistence/sql'>sql</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/language/java/persistence/sql/jdbc'>jdbc</a></div>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/language/java/persistence/sql/mybatis'>mybatis</a></div>
            </details>
          </details>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/language/java/version'>version</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/version/java11'>java11</a></div>
            <details style='margin-left: 30px'>
              <summary><b><a href='../module/platform/development/language/java/version/java16'>java16</a></b></summary>
              <div style='margin-left: 35px'>• <a href='../module/platform/development/language/java/version/java16/record'>record</a></div>
            </details>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/version/java17'>java17</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/version/java7'>java7</a></div>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/language/java/version/java8'>java8</a></div>
          </details>
        </details>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/development/paradigm'>paradigm</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/paradigm/aop'>aop</a></div>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/paradigm/concurrency'>concurrency</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/concurrency/batch'>batch</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/concurrency/forkjoin-workstealing'>forkjoin-workstealing</a></div>
          <details style='margin-left: 25px'>
            <summary><b><a href='../module/platform/development/paradigm/concurrency/schedule'>schedule</a></b></summary>
            <div style='margin-left: 30px'>• <a href='../module/platform/development/paradigm/concurrency/schedule/task'>task</a></div>
          </details>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/concurrency/thread'>thread</a></div>
        </details>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/paradigm/di-ioc'>di-ioc</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/paradigm/functional'>functional</a></div>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/paradigm/mvc-pattern'>mvc-pattern</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/mvc-pattern/controller'>controller</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/mvc-pattern/model'>model</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/mvc-pattern/service'>service</a></div>
        </details>
        <div style='margin-left: 20px'>• <a href='../module/platform/development/paradigm/object-oriented'>object-oriented</a></div>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/paradigm/reactive-stream'>reactive-stream</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/paradigm/reactive-stream/webflux'>webflux</a></div>
        </details>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/development/validation'>validation</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/validation/performance'>performance</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/performance/benchmark'>benchmark</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/performance/jmeter'>jmeter</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/performance/load-test'>load-test</a></div>
        </details>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/development/validation/testing'>testing</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/testing/e2e-test'>e2e-test</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/testing/integration-test'>integration-test</a></div>
          <div style='margin-left: 25px'>• <a href='../module/platform/development/validation/testing/unit-test'>unit-test</a></div>
        </details>
      </details>
    </details>
    <div style='margin-left: 10px'>• <a href='../module/platform/digital'>digital</a></div>
    <div style='margin-left: 10px'>• <a href='../module/platform/social'>social</a></div>
    <details style='margin-left: 10px'>
      <summary><b><a href='../module/platform/support'>support</a></b></summary>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/support/document'>document</a></b></summary>
        <details style='margin-left: 20px'>
          <summary><b><a href='../module/platform/support/document/diagram'>diagram</a></b></summary>
          <div style='margin-left: 25px'>• <a href='../module/platform/support/document/diagram/mermaid'>mermaid</a></div>
        </details>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/document/excel'>excel</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/document/pdf'>pdf</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/document/word'>word</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/document/xml'>xml</a></div>
      </details>
      <details style='margin-left: 15px'>
        <summary><b><a href='../module/platform/support/notification'>notification</a></b></summary>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/email'>email</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/kakao'>kakao</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/microsoft-team'>microsoft-team</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/mobile-push'>mobile-push</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/slack'>slack</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/sms'>sms</a></div>
        <div style='margin-left: 20px'>• <a href='../module/platform/support/notification/telegram'>telegram</a></div>
      </details>
    </details>
  </details>
</details>
