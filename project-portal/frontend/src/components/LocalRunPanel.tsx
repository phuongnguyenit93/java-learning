import { useEffect, useMemo, useState } from 'react';
import { localRunApi, type LocalRunArtifactResponse, type LocalRunBuildStatus } from '../data/localRun';
import { useLanguage } from '../state/LanguageContext';

interface LocalRunPanelProps {
  moduleId: string;
  moduleName: string;
  sourceFingerprint: string;
}

const DEFAULT_PORT = 9000;
const DEFAULT_CONTEXT_PATH = '/api';
const DEFAULT_APPLICATION_NAME = 'api';
const SWAGGER_ENABLED = 'TRUE';
const BUILD_STATUS_POLL_INTERVAL_MS = 2500;

function toJarFileName(moduleId: string): string {
  const normalized = moduleId
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return `${normalized || 'module'}.jar`;
}

export function LocalRunPanel({
  moduleId,
  moduleName,
  sourceFingerprint,
}: LocalRunPanelProps) {
  const { language } = useLanguage();
  const [copied, setCopied] = useState(false);
  const [buildStatus, setBuildStatus] = useState<LocalRunBuildStatus | 'IDLE'>('IDLE');
  const [buildMessage, setBuildMessage] = useState<string | null>(null);
  const [artifactChecking, setArtifactChecking] = useState(true);
  const [artifact, setArtifact] = useState<LocalRunArtifactResponse>({
    moduleId,
    available: false,
    fileName: toJarFileName(moduleId),
  });
  const [error, setError] = useState<string | null>(null);

  const jarFileName = useMemo(() => toJarFileName(moduleId), [moduleId]);
  const runCommand = `java -jar ${jarFileName} --server.port=${DEFAULT_PORT} --server.servlet.context-path=${DEFAULT_CONTEXT_PATH} --spring.application.name=${DEFAULT_APPLICATION_NAME} --swagger.enabled=${SWAGGER_ENABLED}`;
  const localUrl = `http://localhost:${DEFAULT_PORT}${DEFAULT_CONTEXT_PATH}`;

  const text = language === 'vi'
    ? {
        packageTitle: 'Build / Download JAR',
        module: 'Module',
        java: 'Java',
        missing: 'Chưa có executable JAR cho phiên bản này.',
        stale: 'Executable JAR hiện có đã cũ so với module hiện tại.',
        checking: 'Đang kiểm tra executable JAR...',
        fingerprintMissing: 'Không xác định được source fingerprint cho module này.',
        ready: 'Executable JAR đã sẵn sàng.',
        build: 'Build JAR',
        download: 'Download JAR',
        buildStarting: 'Đang gửi yêu cầu build...',
        queued: 'Queued',
        building: 'Building',
        successful: 'Build successful',
        failed: 'Build failed',
        artifactPending: 'Artifact đã sẵn sàng nhưng chưa có download URL.',
        downloadPending: 'Artifact đã sẵn sàng nhưng chưa có download URL.',
        runTitle: 'Run Locally',
        runIntro: 'Tải JAR về máy và chạy bằng Java 21.',
        installTitle: 'Cài Java 21',
        installCopy: 'Cài JDK 21 nếu máy chưa có Java.',
        installAction: 'Download Java 21',
        envTitle: 'Thiết lập JAVA_HOME',
        envCopy: 'Trỏ JAVA_HOME tới thư mục JDK 21 và thêm %JAVA_HOME%\\bin vào Path.',
        verifyTitle: 'Kiểm tra Java',
        runJarTitle: 'Chạy JAR',
        openTitle: 'Mở ứng dụng',
        copy: 'Copy',
        copied: 'Đã copy',
        open: 'Open',
        portNote: 'Port 9000 là mặc định cho Local Run. Có thể đổi --server.port nếu port bị trùng.',
      }
    : {
        packageTitle: 'Build / Download JAR',
        module: 'Module',
        java: 'Java',
        missing: 'No executable JAR is available for this version.',
        stale: 'The available executable JAR is outdated for the current module version.',
        checking: 'Checking executable JAR...',
        fingerprintMissing: 'The source fingerprint is unavailable for this module.',
        ready: 'The executable JAR is ready.',
        build: 'Build JAR',
        download: 'Download JAR',
        buildStarting: 'Sending build request...',
        queued: 'Queued',
        building: 'Building',
        successful: 'Build successful',
        failed: 'Build failed',
        artifactPending: 'The artifact is ready, but a download URL is not available.',
        downloadPending: 'The artifact is ready, but a download URL is not available.',
        runTitle: 'Run Locally',
        runIntro: 'Download the JAR and run it locally with Java 21.',
        installTitle: 'Install Java 21',
        installCopy: 'Install JDK 21 if Java is not available on your machine.',
        installAction: 'Download Java 21',
        envTitle: 'Configure JAVA_HOME',
        envCopy: 'Point JAVA_HOME to your JDK 21 folder and add %JAVA_HOME%\\bin to Path.',
        verifyTitle: 'Verify Java',
        runJarTitle: 'Run the JAR',
        openTitle: 'Open the application',
        copy: 'Copy',
        copied: 'Copied',
        open: 'Open',
        portNote: 'Port 9000 is the Local Run default. Change --server.port if the port is already in use.',
      };

  useEffect(() => {
    let cancelled = false;

    setBuildStatus('IDLE');
    setBuildMessage(null);
    setError(null);
    setArtifactChecking(true);
    setArtifact({
      moduleId,
      available: false,
      fileName: toJarFileName(moduleId),
    });

    if (!sourceFingerprint) {
      setArtifactChecking(false);
      setError(text.fingerprintMissing);
      return () => {
        cancelled = true;
      };
    }

    localRunApi.getArtifact(moduleId, sourceFingerprint)
      .then((artifactResponse) => {
        if (!cancelled) {
          setArtifact(artifactResponse);
        }
      })
      .catch((artifactError) => {
        if (!cancelled) {
          setError(artifactError instanceof Error ? artifactError.message : text.failed);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setArtifactChecking(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [moduleId, sourceFingerprint, text.failed, text.fingerprintMissing]);

  const handleBuild = async () => {
    if (!sourceFingerprint) {
      setError(text.fingerprintMissing);
      return;
    }

    setError(null);
    setBuildStatus('QUEUED');
    setBuildMessage(text.buildStarting);

    try {
      const started = await localRunApi.startBuild({ moduleId, sourceFingerprint });
      setBuildStatus(started.status);
      setBuildMessage(null);

      if (started.result === 'AVAILABLE') {
        const artifactResponse = await localRunApi.getArtifact(moduleId, sourceFingerprint);
        setArtifact(artifactResponse);
        return;
      }

      if (!started.runId) {
        throw new Error('Local Run API did not return a workflow run id.');
      }

      let currentStatus = started.status;
      while (currentStatus !== 'SUCCESS' && currentStatus !== 'FAILED') {
        await new Promise((resolve) => window.setTimeout(resolve, BUILD_STATUS_POLL_INTERVAL_MS));
        const statusResponse = await localRunApi.getBuildStatus(started.runId, moduleId);
        currentStatus = statusResponse.status;
        setBuildStatus(statusResponse.status);
        setBuildMessage(null);

        if (statusResponse.status === 'FAILED') {
          setError(statusResponse.message ?? text.failed);
        }
      }

      if (currentStatus === 'SUCCESS') {
        const artifactResponse = await localRunApi.getArtifact(moduleId, sourceFingerprint);
        setArtifact(artifactResponse);

        if (!artifactResponse.available) {
          setError(text.artifactPending);
        }
      }
    } catch (buildError) {
      setBuildStatus('FAILED');
      setBuildMessage(null);
      setError(buildError instanceof Error ? buildError.message : text.failed);
    }
  };

  const buildInProgress = buildStatus === 'QUEUED' || buildStatus === 'BUILDING';

  const statusLabel = buildStatus === 'QUEUED'
    ? text.queued
    : buildStatus === 'BUILDING'
      ? text.building
      : buildStatus === 'SUCCESS'
        ? text.successful
        : buildStatus === 'FAILED'
          ? text.failed
          : null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(runCommand);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section className="local-run-panel">
      <div className="local-run-grid">
        <article className="local-run-card local-run-card--artifact">
          <div className="local-run-card__heading">
            <span className="local-run-card__eyebrow">Local Run</span>
            <h2>{text.packageTitle}</h2>
          </div>

          <dl className="local-run-meta">
            <div>
              <dt>{text.module}</dt>
              <dd>{moduleName}</dd>
            </div>
            <div>
              <dt>{text.java}</dt>
              <dd>21</dd>
            </div>
          </dl>

          <div className={`local-run-artifact-state${artifact.available ? ' is-ready' : ''}`}>
            <span className="local-run-artifact-state__icon" aria-hidden="true">
              {artifact.available ? '✓' : '○'}
            </span>
            <div>
              <strong>
                {artifactChecking
                  ? text.checking
                  : artifact.available
                    ? text.ready
                    : artifact.stale
                      ? text.stale
                      : text.missing}
              </strong>
              <span>{jarFileName}</span>
            </div>
          </div>

          {buildStatus !== 'IDLE' && (
            <div className="local-run-progress" aria-live="polite">
              <div className={`local-run-progress__step${buildStatus === 'QUEUED' || buildStatus === 'BUILDING' || buildStatus === 'SUCCESS' ? ' is-active' : ''}${buildStatus === 'QUEUED' ? ' is-current' : ''}`}>
                <span>1</span>
                <strong>{text.queued}</strong>
              </div>
              <span className={`local-run-progress__connector${buildStatus === 'QUEUED' || buildStatus === 'BUILDING' ? ' is-running' : buildStatus === 'SUCCESS' ? ' is-complete' : ''}`} aria-hidden="true">↓</span>
              <div className={`local-run-progress__step${buildStatus === 'BUILDING' || buildStatus === 'SUCCESS' ? ' is-active' : ''}${buildStatus === 'BUILDING' ? ' is-current' : ''}`}>
                <span>2</span>
                <strong>{text.building}</strong>
              </div>
              <span className={`local-run-progress__connector${buildStatus === 'BUILDING' ? ' is-running' : buildStatus === 'SUCCESS' ? ' is-complete' : ''}`} aria-hidden="true">↓</span>
              <div className={`local-run-progress__step${buildStatus === 'SUCCESS' ? ' is-active is-success' : buildStatus === 'FAILED' ? ' is-active is-failed' : ''}`}>
                <span>{buildStatus === 'SUCCESS' ? '✓' : buildStatus === 'FAILED' ? '×' : '3'}</span>
                <strong>{buildStatus === 'FAILED' ? text.failed : text.successful}</strong>
              </div>

              {buildInProgress && (
                <div className="local-run-progress__activity" aria-hidden="true">
                  <span />
                </div>
              )}
            </div>
          )}

          <div className="local-run-card__actions">
            {artifact.available ? (
              artifact.downloadUrl ? (
                <a className="local-run-button local-run-button--download" href={artifact.downloadUrl} download={jarFileName}>
                  ↓ {text.download}
                </a>
              ) : (
                <button className="local-run-button local-run-button--download" type="button" disabled title={text.downloadPending}>
                  ↓ {text.download}
                </button>
              )
            ) : (
              <button
                className="local-run-button local-run-button--build"
                type="button"
                onClick={handleBuild}
                disabled={buildInProgress || artifactChecking || !sourceFingerprint}
              >
                ▶ {buildInProgress ? statusLabel : text.build}
              </button>
            )}
          </div>

          {buildMessage && !artifact.available && (
            <p className="local-run-backend-note" role="status">{buildMessage}</p>
          )}

          {artifact.available && !artifact.downloadUrl && (
            <p className="local-run-backend-note local-run-backend-note--success" role="status">{text.artifactPending}</p>
          )}

          {error && (
            <p className="local-run-backend-note local-run-backend-note--error" role="alert">{error}</p>
          )}
        </article>

        <article className="local-run-card local-run-card--guide">
          <div className="local-run-card__heading">
            <span className="local-run-card__eyebrow">Java 21</span>
            <h2>{text.runTitle}</h2>
            <p>{text.runIntro}</p>
          </div>

          <ol className="local-run-steps">
            <li>
              <div className="local-run-step__copy">
                <strong>{text.installTitle}</strong>
                <span>{text.installCopy}</span>
              </div>
              <a
                className="local-run-inline-action"
                href="https://adoptium.net/temurin/releases/?version=21"
                target="_blank"
                rel="noreferrer"
              >
                {text.installAction} ↗
              </a>
            </li>

            <li>
              <div className="local-run-step__copy">
                <strong>{text.envTitle}</strong>
                <span>{text.envCopy}</span>
              </div>
            </li>

            <li>
              <div className="local-run-step__copy">
                <strong>{text.verifyTitle}</strong>
              </div>
              <code className="local-run-code">java -version</code>
            </li>

            <li>
              <div className="local-run-step__copy">
                <strong>{text.runJarTitle}</strong>
              </div>
              <div className="local-run-command">
                <code>{runCommand}</code>
                <button type="button" onClick={handleCopy}>{copied ? text.copied : text.copy}</button>
              </div>
              <small>{text.portNote}</small>
            </li>

            <li>
              <div className="local-run-step__copy">
                <strong>{text.openTitle}</strong>
              </div>
              <div className="local-run-url-row">
                <code>{localUrl}</code>
                <a href={localUrl} target="_blank" rel="noreferrer">{text.open} ↗</a>
              </div>
            </li>
          </ol>
        </article>
      </div>
    </section>
  );
}
