import { useLanguage } from '../state/LanguageContext';

const CV_DOCUMENT_ID = '15koZtQTM2F83KUALQPsVgbkcXzX6scvlDqiHEjODLC8';

const CV_EMBED_URL =
  `https://docs.google.com/document/d/${CV_DOCUMENT_ID}/edit?hl=vi&tab=t.0`;

const CV_DOWNLOAD_URL =
  `https://docs.google.com/document/d/${CV_DOCUMENT_ID}/export?format=pdf`;

export function MyCvPage() {
  const { language } = useLanguage();

  const text =
    language === 'vi'
      ? {
          title: 'My CV',
          description: 'Xem trực tiếp CV và tải xuống bản PDF.',
          download: 'Tải CV PDF',
        }
      : {
          title: 'My CV',
          description: 'View my CV directly and download a PDF copy.',
          download: 'Download CV PDF',
        };

  return (
    <main className="my-cv-page" aria-label="My CV">
      <div className="my-cv-toolbar">
        <div className="my-cv-toolbar__copy">
          <strong>{text.title}</strong>
          <span>{text.description}</span>
        </div>

        <a
          className="my-cv-download"
          href={CV_DOWNLOAD_URL}
          target="_blank"
          rel="noreferrer"
        >
          {text.download}
        </a>
      </div>

      <iframe
        className="my-cv-frame"
        src={CV_EMBED_URL}
        title="My CV"
        loading="eager"
      />
    </main>
  );
}
