import type { LearningModule, ModuleCatalog, ModuleCatalogNode, ModuleStats } from '../types/learning';

const acronymLabels: Record<string, string> = {
  aop: 'AOP',
  api: 'API',
  ci: 'CI',
  cd: 'CD',
  db: 'DB',
  elk: 'ELK',
  grpc: 'gRPC',
  iam: 'IAM',
  jvm: 'JVM',
  k8s: 'K8s',
  mq: 'MQ',
  nosql: 'NoSQL',
  rdbms: 'RDBMS',
  sse: 'SSE',
  sql: 'SQL',
  ssl: 'SSL',
  tcp: 'TCP',
  tls: 'TLS',
  udp: 'UDP',
};

export async function loadModuleCatalog(): Promise<ModuleCatalog> {
  const response = await fetch('/module-catalog.json', { cache: 'no-cache' });

  if (!response.ok) {
    throw new Error(`Unable to load module catalog: ${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<ModuleCatalog>;
}

export function formatCatalogName(name: string): string {
  return name
    .split(/[-_]/g)
    .filter(Boolean)
    .map((part) => acronymLabels[part.toLowerCase()] ?? `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join(' ');
}

export function findModuleByRouteId(
  node: ModuleCatalogNode,
  routeId: string,
): ModuleCatalogNode | undefined {
  if (node.kind === 'MODULE' && node.routeId === routeId) {
    return node;
  }

  for (const child of node.children) {
    const match = findModuleByRouteId(child, routeId);

    if (match) {
      return match;
    }
  }

  return undefined;
}

export function collectRealModules(node: ModuleCatalogNode): ModuleCatalogNode[] {
  const result: ModuleCatalogNode[] = node.kind === 'MODULE' ? [node] : [];

  for (const child of node.children) {
    result.push(...collectRealModules(child));
  }

  return result;
}

export function toLearningModule(node: ModuleCatalogNode, stats: ModuleStats): LearningModule {
  const displayName = formatCatalogName(node.name);
  const pathParts = node.path.split('/').filter(Boolean);
  const hierarchy = pathParts.slice(1, -1).map(formatCatalogName);
  const description = node.description?.trim() || 'No module description has been provided yet.';

  return {
    id: node.routeId ?? node.serviceName ?? node.name,
    shortName: displayName,
    name: { vi: displayName, en: displayName },
    description: { vi: description, en: description },
    path: hierarchy,
    questionCount: stats.knowledge,
    overview: node.overview ?? {},
    knowledge: node.knowledge ?? {},
    quiz: node.quiz ?? {},
    interview: node.interview ?? {},
    api: node.api ?? {},
    capabilities: {
      knowledge: true,
      quiz: true,
      interview: true,
      apiDocs: true,
      execution: node.moduleType === 'SERVLET' || node.moduleType === 'REACTIVE',
      download: true,
    },
  };
}
