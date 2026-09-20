import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, '../../..');
const gradleWrapper = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

const result = spawnSync(
  gradleWrapper,
  [':generatePortalData', '--no-daemon', '--console=plain'],
  {
    cwd: repositoryRoot,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  },
);

if (result.error) {
  throw result.error;
}

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}
