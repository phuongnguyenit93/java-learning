import { useLanguage } from '../state/LanguageContext';

interface EmptyPanelProps {
  type: 'interview' | 'execution' | 'download';
}

export function EmptyPanel({ type }: EmptyPanelProps) {
  const { language } = useLanguage();

  const copy = {
    interview: {
      vi: ['Interview chưa có nội dung', 'Khu vực này sẽ chứa các câu hỏi và nội dung luyện phỏng vấn liên quan đến module.'],
      en: ['Interview content is not available yet', 'This section will contain interview questions and practice content related to the module.'],
    },
    execution: {
      vi: ['Local Run chưa kết nối', 'Khu vực này sẽ nhận Execution Context khi backend/runtime capability được nối vào Portal.'],
      en: ['Local Run is not connected yet', 'This section will consume Execution Context when a backend/runtime capability is connected to the Portal.'],
    },
    download: {
      vi: ['Download chưa có artifact', 'UI đã được chuẩn bị; source ZIP, JAR/WAR và Docker bundle sẽ được nối ở phase sau.'],
      en: ['No downloadable artifacts yet', 'The UI is prepared; source ZIP, JAR/WAR and Docker bundles will be connected in a later phase.'],
    },
  } as const;

  return (
    <div className="empty-state empty-state--large">
      <span className="empty-state__icon" aria-hidden="true">
        {type === 'execution' ? '◎' : type === 'interview' ? '?' : '↓'}
      </span>
      <strong>{copy[type][language][0]}</strong>
      <span>{copy[type][language][1]}</span>
    </div>
  );
}
