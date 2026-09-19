import type {
  ApiOperation,
  KnowledgeItem,
  KnowledgeTopic,
  LearningModule,
  ModuleTreeNode,
  QuizItem,
} from '../types/learning';

export const learningModules: LearningModule[] = [
  {
    id: 'THREAD',
    shortName: 'Thread',
    name: { vi: 'Thread & Concurrency', en: 'Thread & Concurrency' },
    description: {
      vi: 'Học cách tạo, phối hợp và quan sát thread, synchronization, executor và virtual thread trong Java.',
      en: 'Learn how to create, coordinate and observe threads, synchronization, executors and virtual threads in Java.',
    },
    path: ['Platform', 'Development', 'Paradigm', 'Concurrency'],
    questionCount: 42,
    capabilities: { knowledge: true, quiz: true, apiDocs: true, execution: true, download: true },
  },
  {
    id: 'ASPECT',
    shortName: 'AOP',
    name: { vi: 'Aspect Oriented Programming', en: 'Aspect Oriented Programming' },
    description: {
      vi: 'Khám phá proxy, advice, pointcut và luồng thực thi AOP trong Spring.',
      en: 'Explore proxies, advice, pointcuts and Spring AOP execution flows.',
    },
    path: ['Platform', 'Development', 'Paradigm'],
    questionCount: 22,
    capabilities: { knowledge: true, quiz: true, apiDocs: true, execution: true, download: true },
  },
  {
    id: 'KAFKA',
    shortName: 'Kafka',
    name: { vi: 'Apache Kafka', en: 'Apache Kafka' },
    description: {
      vi: 'Producer, consumer, delivery semantics và các pattern xử lý message trong Kafka.',
      en: 'Producers, consumers, delivery semantics and message-processing patterns with Kafka.',
    },
    path: ['Integration', 'Broker'],
    questionCount: 31,
    capabilities: { knowledge: true, quiz: true, apiDocs: true, execution: false, download: true },
  },
  {
    id: 'OPEN_REWRITE_GRADLE',
    shortName: 'Gradle Cache',
    name: { vi: 'Gradle Cache & Build Tooling', en: 'Gradle Cache & Build Tooling' },
    description: {
      vi: 'Module không runnable minh hoạ knowledge và quiz độc lập với Spring Boot runtime.',
      en: 'A non-runnable module demonstrating knowledge and quizzes independently from a Spring Boot runtime.',
    },
    path: ['Platform', 'Development', 'Build Tool', 'Gradle'],
    questionCount: 16,
    capabilities: { knowledge: true, quiz: true, apiDocs: false, execution: false, download: true },
  },
  {
    id: 'SPRING_JPA',
    shortName: 'Spring JPA',
    name: { vi: 'Spring Data JPA', en: 'Spring Data JPA' },
    description: {
      vi: 'Repository abstraction, persistence context và các pattern truy cập dữ liệu với Spring Data JPA.',
      en: 'Repository abstractions, persistence contexts and data-access patterns with Spring Data JPA.',
    },
    path: ['Platform', 'Development', 'Framework', 'Spring', 'Data'],
    questionCount: 28,
    capabilities: { knowledge: true, quiz: true, apiDocs: true, execution: false, download: true },
  },
  {
    id: 'DATABASE_MYSQL',
    shortName: 'MySQL',
    name: { vi: 'MySQL', en: 'MySQL' },
    description: {
      vi: 'SQL, transaction, lock và các chủ đề database nền tảng cho Java backend.',
      en: 'SQL, transactions, locks and foundational database topics for Java backend development.',
    },
    path: ['Infrastructure', 'System', 'Database', 'RDBMS'],
    questionCount: 24,
    capabilities: { knowledge: true, quiz: true, apiDocs: false, execution: false, download: true },
  },
];

export const moduleTree: ModuleTreeNode[] = [
  {
    id: 'platform',
    label: { vi: 'Platform', en: 'Platform' },
    children: [
      {
        id: 'development',
        label: { vi: 'Development', en: 'Development' },
        children: [
          {
            id: 'paradigm',
            label: { vi: 'Paradigm', en: 'Paradigm' },
            children: [
              { id: 'aspect', label: { vi: 'AOP', en: 'AOP' }, moduleId: 'ASPECT', count: 22 },
              {
                id: 'concurrency',
                label: { vi: 'Concurrency', en: 'Concurrency' },
                children: [
                  { id: 'thread', label: { vi: 'Thread', en: 'Thread' }, moduleId: 'THREAD', count: 42 },
                ],
              },
            ],
          },
          {
            id: 'build-tool',
            label: { vi: 'Build Tool', en: 'Build Tool' },
            children: [
              {
                id: 'gradle',
                label: { vi: 'Gradle', en: 'Gradle' },
                children: [
                  {
                    id: 'gradle-cache',
                    label: { vi: 'Gradle Cache', en: 'Gradle Cache' },
                    moduleId: 'OPEN_REWRITE_GRADLE',
                    count: 16,
                  },
                ],
              },
            ],
          },
          {
            id: 'framework',
            label: { vi: 'Framework', en: 'Framework' },
            children: [
              {
                id: 'spring',
                label: { vi: 'Spring', en: 'Spring' },
                children: [
                  { id: 'spring-jpa', label: { vi: 'Spring Data JPA', en: 'Spring Data JPA' }, moduleId: 'SPRING_JPA', count: 28 },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  {
    id: 'integration',
    label: { vi: 'Integration', en: 'Integration' },
    children: [
      {
        id: 'broker',
        label: { vi: 'Broker', en: 'Broker' },
        children: [
          { id: 'kafka', label: { vi: 'Kafka', en: 'Kafka' }, moduleId: 'KAFKA', count: 31 },
        ],
      },
    ],
  },
  {
    id: 'infrastructure',
    label: { vi: 'Infrastructure', en: 'Infrastructure' },
    children: [
      {
        id: 'system',
        label: { vi: 'System', en: 'System' },
        children: [
          {
            id: 'database',
            label: { vi: 'Database', en: 'Database' },
            children: [
              { id: 'mysql', label: { vi: 'MySQL', en: 'MySQL' }, moduleId: 'DATABASE_MYSQL', count: 24 },
            ],
          },
        ],
      },
    ],
  },
];

export const knowledgeTopics: KnowledgeTopic[] = [
  { id: 'all', label: { vi: 'Tất cả', en: 'All' } },
  { id: 'intro', label: { vi: 'Nhập môn', en: 'Introduction' } },
  { id: 'oop', label: { vi: 'OOP & Design', en: 'OOP & Design' } },
  { id: 'concurrency', label: { vi: 'Concurrency', en: 'Concurrency' } },
  { id: 'collections', label: { vi: 'Collections', en: 'Collections' } },
  { id: 'jvm', label: { vi: 'JVM & Memory', en: 'JVM & Memory' } },
  { id: 'threading', label: { vi: 'Threading', en: 'Threading' } },
];

export const knowledgeItems: KnowledgeItem[] = [
  {
    id: 'thread-1',
    moduleId: 'THREAD',
    topicId: 'intro',
    title: {
      vi: 'Thread trong Java là gì và tại sao cần concurrency?',
      en: 'What is a Java thread and why do we need concurrency?',
    },
    level: 'basic',
    summary: {
      vi: 'Thread là một luồng thực thi độc lập bên trong process Java và là nền tảng để mô hình hoá nhiều công việc cùng tiến triển.',
      en: 'A thread is an independent execution flow inside a Java process and is the foundation for modelling work that progresses concurrently.',
    },
    paragraphs: [
      {
        vi: 'Một process Java có thể chứa nhiều thread cùng chia sẻ heap nhưng mỗi thread có stack riêng. Điều này vừa tạo sức mạnh cho concurrency vừa tạo ra race condition nếu state dùng chung không được bảo vệ.',
        en: 'A Java process can contain many threads sharing the heap while each thread owns its own stack. This enables concurrency but also creates race conditions when shared state is not protected.',
      },
      {
        vi: 'Trong module học tập, mục tiêu không chỉ là tạo thread mà còn quan sát lifecycle, scheduling, coordination và các abstraction cấp cao hơn.',
        en: 'In this learning module, the goal is not only to create threads but also to observe lifecycle, scheduling, coordination and higher-level abstractions.',
      },
    ],
    bullets: [
      { vi: 'Thread chia sẻ heap nhưng có stack riêng.', en: 'Threads share the heap but have separate stacks.' },
      { vi: 'Concurrency không đồng nghĩa mọi thứ chạy song song vật lý.', en: 'Concurrency does not mean everything runs physically in parallel.' },
      { vi: 'Shared mutable state là nguồn gốc của nhiều lỗi concurrency.', en: 'Shared mutable state is a common source of concurrency bugs.' },
    ],
    code: 'Thread worker = new Thread(() -> doWork());\nworker.start();',
  },
  {
    id: 'thread-2',
    moduleId: 'THREAD',
    topicId: 'threading',
    title: { vi: 'start() và run() khác nhau thế nào?', en: 'How are start() and run() different?' },
    level: 'basic',
    summary: {
      vi: 'start() yêu cầu JVM khởi động execution trên thread mới, còn gọi run() trực tiếp chỉ là một method call bình thường.',
      en: 'start() asks the JVM to begin execution on a new thread, while calling run() directly is just a normal method call.',
    },
    paragraphs: [
      {
        vi: 'Đây là khác biệt nền tảng khi quan sát tên thread, call stack và thứ tự thực thi trong demo.',
        en: 'This difference is fundamental when observing thread names, call stacks and execution order in a demo.',
      },
    ],
    bullets: [
      { vi: 'start() chỉ được gọi một lần trên cùng một Thread instance.', en: 'start() can be called only once on the same Thread instance.' },
      { vi: 'run() trực tiếp không tạo thread mới.', en: 'Calling run() directly does not create a new thread.' },
    ],
  },
  {
    id: 'thread-3',
    moduleId: 'THREAD',
    topicId: 'concurrency',
    title: { vi: 'Race condition xuất hiện khi nào?', en: 'When does a race condition occur?' },
    level: 'intermediate',
    summary: {
      vi: 'Race condition xảy ra khi kết quả phụ thuộc vào timing/interleaving của nhiều thread truy cập state dùng chung.',
      en: 'A race condition occurs when the result depends on the timing or interleaving of multiple threads accessing shared state.',
    },
    paragraphs: [
      {
        vi: 'Các thao tác nhìn đơn giản như count++ thực tế gồm nhiều bước đọc, tính toán và ghi nên không mặc định atomic.',
        en: 'Simple-looking operations such as count++ involve read, compute and write steps, so they are not atomic by default.',
      },
    ],
    bullets: [
      { vi: 'Có shared mutable state.', en: 'Shared mutable state exists.' },
      { vi: 'Có nhiều execution cùng truy cập.', en: 'Multiple executions access it.' },
      { vi: 'Thiếu coordination/synchronization phù hợp.', en: 'Appropriate coordination or synchronization is missing.' },
    ],
    code: 'counter++; // read -> increment -> write',
  },
  {
    id: 'thread-4',
    moduleId: 'THREAD',
    topicId: 'concurrency',
    title: { vi: 'volatile có làm count++ atomic không?', en: 'Does volatile make count++ atomic?' },
    level: 'intermediate',
    summary: {
      vi: 'Không. volatile chủ yếu cung cấp visibility và ordering guarantee, không biến compound operation thành atomic operation.',
      en: 'No. volatile primarily provides visibility and ordering guarantees; it does not turn a compound operation into an atomic operation.',
    },
    paragraphs: [
      {
        vi: 'Nếu cần atomic increment, có thể dùng AtomicInteger hoặc bảo vệ critical section bằng lock/synchronized tuỳ bài toán.',
        en: 'For atomic increments, use AtomicInteger or protect the critical section with a lock or synchronized depending on the problem.',
      },
    ],
    bullets: [
      { vi: 'Visibility khác atomicity.', en: 'Visibility is different from atomicity.' },
      { vi: 'Compound operation cần coordination riêng.', en: 'Compound operations need separate coordination.' },
    ],
  },
  {
    id: 'aspect-1',
    moduleId: 'ASPECT',
    topicId: 'intro',
    title: { vi: 'Spring AOP proxy được tạo để làm gì?', en: 'Why does Spring AOP create a proxy?' },
    level: 'basic',
    summary: {
      vi: 'Proxy đứng giữa caller và target bean để áp dụng advice mà không buộc business class chứa cross-cutting logic.',
      en: 'A proxy sits between the caller and target bean so advice can be applied without placing cross-cutting logic in the business class.',
    },
    paragraphs: [],
    bullets: [
      { vi: 'Logging, security và transaction là ví dụ cross-cutting concern.', en: 'Logging, security and transactions are common cross-cutting concerns.' },
    ],
  },
  {
    id: 'kafka-1',
    moduleId: 'KAFKA',
    topicId: 'intro',
    title: { vi: 'Consumer group giải quyết vấn đề gì?', en: 'What problem does a consumer group solve?' },
    level: 'basic',
    summary: {
      vi: 'Consumer group cho phép phân phối partition giữa nhiều consumer để scale processing trong cùng một logical subscriber.',
      en: 'A consumer group distributes partitions across consumers to scale processing within one logical subscriber.',
    },
    paragraphs: [],
    bullets: [
      { vi: 'Một partition chỉ được gán cho tối đa một consumer trong cùng group tại một thời điểm.', en: 'A partition is assigned to at most one consumer in the same group at a time.' },
    ],
  },
];

export const quizItems: QuizItem[] = [
  {
    id: 'quiz-thread-1',
    moduleId: 'THREAD',
    question: { vi: 'Phương thức nào bắt đầu execution trên một thread mới?', en: 'Which method starts execution on a new thread?' },
    answers: [
      { vi: 'start()', en: 'start()' },
      { vi: 'run()', en: 'run()' },
      { vi: 'join()', en: 'join()' },
      { vi: 'yield()', en: 'yield()' },
    ],
  },
  {
    id: 'quiz-thread-2',
    moduleId: 'THREAD',
    question: { vi: 'volatile chủ yếu giải quyết guarantee nào?', en: 'Which guarantee does volatile primarily address?' },
    answers: [
      { vi: 'Visibility', en: 'Visibility' },
      { vi: 'Mutual exclusion', en: 'Mutual exclusion' },
      { vi: 'Transaction rollback', en: 'Transaction rollback' },
      { vi: 'Thread creation', en: 'Thread creation' },
    ],
  },
];

export const apiOperations: ApiOperation[] = [
  {
    id: 'api-thread-start',
    moduleId: 'THREAD',
    method: 'GET',
    path: '/thread/basic/start-vs-run',
    summary: { vi: 'Quan sát start() và run()', en: 'Observe start() versus run()' },
    description: {
      vi: 'Pseudo API mô tả experiment so sánh execution thread giữa start() và run().',
      en: 'Pseudo API describing an experiment that compares thread execution between start() and run().',
    },
  },
  {
    id: 'api-thread-race',
    moduleId: 'THREAD',
    method: 'POST',
    path: '/thread/concurrency/race-condition',
    summary: { vi: 'Tạo race condition có kiểm soát', en: 'Create a controlled race condition' },
    description: {
      vi: 'Pseudo API dùng dữ liệu tĩnh; nút execute thật sẽ được nối vào runtime capability ở phase sau.',
      en: 'This pseudo API uses static data; real execution will be connected to a runtime capability in a later phase.',
    },
  },
  {
    id: 'api-aspect-proxy',
    moduleId: 'ASPECT',
    method: 'GET',
    path: '/aspect/proxy/inspect',
    summary: { vi: 'Kiểm tra proxy AOP', en: 'Inspect an AOP proxy' },
    description: {
      vi: 'Hiển thị metadata giả lập cho API demo của AOP.',
      en: 'Displays mock metadata for an AOP API demo.',
    },
  },
];

