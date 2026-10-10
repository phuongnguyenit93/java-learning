import { useLanguage } from '../state/LanguageContext';

export function JavaProjectPage() {
  const { language } = useLanguage();

  return (
    <main className="java-project-page">
      <section className="java-project-placeholder">
        <span className="java-project-placeholder__icon" aria-hidden="true">⌘</span>
        <span className="java-project-placeholder__eyebrow">JAVA / PROJECT</span>
        <h1>{language === 'vi' ? 'Nội dung đang được cập nhật' : 'Content is being updated'}</h1>
        <p>
          {language === 'vi'
            ? 'Các dự án thực hành Java sẽ được bổ sung tại đây.'
            : 'Java practice projects will be added here.'}
        </p>
      </section>
    </main>
  );
}
