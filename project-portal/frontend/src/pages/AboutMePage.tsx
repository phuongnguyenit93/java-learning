import { useState } from 'react';
import { useLanguage } from '../state/LanguageContext';

const CV_DOCUMENT_ID = '15koZtQTM2F83KUALQPsVgbkcXzX6scvlDqiHEjODLC8';

const CV_EMBED_URL =
  `https://docs.google.com/document/d/${CV_DOCUMENT_ID}/edit?hl=vi&tab=t.0`;

const CV_PDF_DOWNLOAD_URL =
  `https://docs.google.com/document/d/${CV_DOCUMENT_ID}/export?format=pdf`;

const CV_DOC_DOWNLOAD_URL =
  `https://docs.google.com/document/d/${CV_DOCUMENT_ID}/export?format=docx`;

export function AboutMePage() {
  const { language } = useLanguage();
  const [isCvVisible, setIsCvVisible] = useState(false);

  const text =
    language === 'vi'
      ? {
          view: 'Xem CV Online',
          hide: 'Ẩn CV của tôi',
          viewerLabel: 'CV trực tuyến',
        }
      : {
          view: 'View CV Online',
          hide: 'Hide My CV',
          viewerLabel: 'Online CV',
        };

  return (
    <main className="about-me-page" aria-label="About Me">
      <section className="about-me-download" aria-labelledby="about-me-download-title">
        <h1 id="about-me-download-title">Download My CV</h1>
        <div className="about-me-download__actions">
          <a
            className="about-me-download__link"
            href={CV_PDF_DOWNLOAD_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Download My CV as PDF"
          >
            PDF
          </a>
          <a
            className="about-me-download__link"
            href={CV_DOC_DOWNLOAD_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Download My CV as Word DOCX"
            title="Microsoft Word (.docx)"
          >
            DOC
          </a>
        </div>
      </section>

      <section className="about-me-viewer" aria-label={text.viewerLabel}>
        <button
          className="about-me-viewer__toggle"
          type="button"
          onClick={() => setIsCvVisible((visible) => !visible)}
          aria-expanded={isCvVisible}
          aria-controls="about-me-cv-embed"
        >
          {isCvVisible ? text.hide : text.view}
        </button>

        <div className="about-me-viewer__window" id="about-me-cv-embed" hidden={!isCvVisible}>
          {isCvVisible && (
            <iframe
              className="about-me-viewer__frame"
              src={CV_EMBED_URL}
              title={text.viewerLabel}
              loading="lazy"
            />
          )}
        </div>
      </section>
    </main>
  );
}
