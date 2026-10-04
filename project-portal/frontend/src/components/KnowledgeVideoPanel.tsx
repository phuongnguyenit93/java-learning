import { useEffect, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { loadVideoScript } from '../data/video';
import { useLanguage } from '../state/LanguageContext';
import type { VideoIndexItem, VideoScriptDocument } from '../types/learning';

interface KnowledgeVideoPanelProps {
  item: VideoIndexItem;
  compact?: boolean;
}

function resolveVideoEmbedUrl(rawUrl: string): string | null {
  const value = rawUrl.trim();

  if (!value) {
    return null;
  }

  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase().replace(/^www\./, '');

    if (host === 'youtu.be') {
      const videoId = url.pathname.split('/').filter(Boolean)[0];
      return videoId ? `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}` : null;
    }

    if (host === 'youtube.com' || host === 'm.youtube.com') {
      const watchId = url.searchParams.get('v');
      if (watchId) {
        return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(watchId)}`;
      }

      const pathParts = url.pathname.split('/').filter(Boolean);
      if ((pathParts[0] === 'embed' || pathParts[0] === 'shorts') && pathParts[1]) {
        return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(pathParts[1])}`;
      }
    }

    if (host === 'vimeo.com' || host === 'player.vimeo.com') {
      const videoId = url.pathname.split('/').filter(Boolean).find((part) => /^\d+$/.test(part));
      return videoId ? `https://player.vimeo.com/video/${videoId}` : null;
    }
  } catch {
    return null;
  }

  return null;
}

function isDirectVideoUrl(rawUrl: string): boolean {
  return /\.(mp4|webm|ogg)(?:[?#].*)?$/i.test(rawUrl.trim());
}

export function KnowledgeVideoPanel({ item, compact = false }: KnowledgeVideoPanelProps) {
  const { language } = useLanguage();
  const [scriptExpanded, setScriptExpanded] = useState(false);
  const [script, setScript] = useState<VideoScriptDocument | null>(null);
  const [scriptLoading, setScriptLoading] = useState(false);
  const [scriptError, setScriptError] = useState<string | null>(null);
  const embedUrl = useMemo(() => resolveVideoEmbedUrl(item.url), [item.url]);

  useEffect(() => {
    setScript(null);
    setScriptError(null);
    setScriptExpanded(false);
  }, [compact, item.content]);

  useEffect(() => {
    let active = true;

    if (!scriptExpanded || script || scriptLoading) {
      return () => {
        active = false;
      };
    }

    setScriptLoading(true);
    setScriptError(null);

    loadVideoScript(item.content)
      .then((result) => {
        if (!active) {
          return;
        }

        setScript(result);
        setScriptLoading(false);
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }

        setScriptError(error instanceof Error ? error.message : 'Unable to load video script.');
        setScriptLoading(false);
      });

    return () => {
      active = false;
    };
  }, [item.content, script, scriptExpanded, scriptLoading]);

  return (
    <section className={`knowledge-video${compact ? ' knowledge-video--compact' : ''}`}>
      <div className="knowledge-video__header">
        <div className="knowledge-video__heading">
          <span className="knowledge-video__eyebrow">VIDEO</span>
          <strong>{language === 'vi' ? 'Video & kịch bản trình bày' : 'Video & presentation script'}</strong>
        </div>

        <div className="knowledge-video__stats">
          <span>{item.sceneCount} {language === 'vi' ? 'scene' : item.sceneCount === 1 ? 'scene' : 'scenes'}</span>
          {item.transitionCount > 0 && (
            <span>{item.transitionCount} transition</span>
          )}
        </div>
      </div>

      <div className="knowledge-video__player-shell">
        {embedUrl ? (
          <iframe
            className="knowledge-video__iframe"
            src={embedUrl}
            title={item.title}
            loading="lazy"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
          />
        ) : item.url && isDirectVideoUrl(item.url) ? (
          <video className="knowledge-video__native-player" controls preload="metadata" src={item.url}>
            <track kind="captions" />
          </video>
        ) : item.url ? (
          <div className="knowledge-video__placeholder">
            <span className="knowledge-video__play" aria-hidden="true">▶</span>
            <strong>{language === 'vi' ? 'Video đã được liên kết' : 'Video is linked'}</strong>
            <span>{language === 'vi' ? 'Nguồn này không hỗ trợ nhúng trực tiếp.' : 'This source cannot be embedded directly.'}</span>
            <a href={item.url} target="_blank" rel="noreferrer">
              {language === 'vi' ? 'Mở video' : 'Open video'} ↗
            </a>
          </div>
        ) : (
          <div className="knowledge-video__placeholder">
            <span className="knowledge-video__play" aria-hidden="true">▶</span>
            <strong>{language === 'vi' ? 'Video chưa được xuất bản' : 'Video not published yet'}</strong>
            <span>
              {language === 'vi'
                ? 'Kịch bản đã có sẵn; player sẽ xuất hiện khi video.url được cập nhật.'
                : 'The script is ready; the player will appear when video.url is updated.'}
            </span>
          </div>
        )}
      </div>

      <div className="knowledge-video__script">
        <button
          type="button"
          className="knowledge-video__script-toggle"
          onClick={() => setScriptExpanded((current) => !current)}
          aria-expanded={scriptExpanded}
        >
          <span>
            <strong>{language === 'vi' ? 'Kịch bản video' : 'Video script'}</strong>
            <small>
              {item.sectionCount} {language === 'vi' ? 'phần' : item.sectionCount === 1 ? 'section' : 'sections'} · {item.sceneCount} scene
            </small>
          </span>
          <span aria-hidden="true">{scriptExpanded ? '−' : '+'}</span>
        </button>

        {scriptExpanded && (
          <div className="knowledge-video__script-body">
            {scriptLoading && (
              <div className="knowledge-video__status">
                {language === 'vi' ? 'Đang tải kịch bản...' : 'Loading script...'}
              </div>
            )}

            {!scriptLoading && scriptError && (
              <div className="knowledge-video__status knowledge-video__status--error">{scriptError}</div>
            )}

            {!scriptLoading && script && script.sections.map((section) => (
              <section key={`${script.categoryId}:${section.order}`} className="knowledge-video__script-section">
                <div className="knowledge-video__script-section-title">
                  <span>{String(section.order).padStart(2, '0')}</span>
                  <strong>{section.title}</strong>
                </div>

                <div className="knowledge-video__script-table-wrap">
                  <table className="knowledge-video__script-table">
                    <thead>
                      <tr>
                        <th>{language === 'vi' ? 'Thời gian' : 'Time'}</th>
                        <th>Visual</th>
                        <th>Script</th>
                        <th>{language === 'vi' ? 'Mục đích' : 'Purpose'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {section.items.map((scriptItem, index) => (
                        <tr key={`${section.order}:${scriptItem.type}:${index}`}>
                          <td className="knowledge-video__script-time">
                            <span className={`knowledge-video__type knowledge-video__type--${scriptItem.type.toLowerCase()}`}>
                              {scriptItem.type === 'TRANSITION'
                                ? 'Transition'
                                : `Scene ${section.items
                                  .slice(0, index + 1)
                                  .filter((itemAtPosition) => itemAtPosition.type === 'SCENE').length}`}
                            </span>
                            <code>{scriptItem.time}</code>
                            {scriptItem.title && <strong>{scriptItem.title}</strong>}
                          </td>
                          <td>
                            <div className="markdown-content knowledge-video__markdown">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{scriptItem.visual}</ReactMarkdown>
                            </div>
                          </td>
                          <td>
                            <div className="markdown-content knowledge-video__markdown">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{scriptItem.script}</ReactMarkdown>
                            </div>
                          </td>
                          <td>
                            <div className="markdown-content knowledge-video__markdown">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{scriptItem.purpose}</ReactMarkdown>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
