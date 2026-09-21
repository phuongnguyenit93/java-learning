import { Link } from 'react-router-dom';
import { useLanguage } from '../state/LanguageContext';

export function HomePage() {
  const { language } = useLanguage();

  const content =
    language === 'vi'
      ? {
          title: 'Trang chủ đang được cập nhật',
          description:
            'Nội dung của trang chủ vẫn đang trong quá trình hoàn thiện. Bạn có thể chuyển sang tab Learning để xem các module, Knowledge và API Docs hiện có.',
          action: 'Đi tới Learning',
        }
      : {
          title: 'Home page is being updated',
          description:
            'The home page is still being completed. You can continue to the Learning tab to explore the available modules, Knowledge, and API Docs.',
          action: 'Go to Learning',
        };

  return (
    <main className="home-page" aria-label="Home page">
      <section className="home-placeholder">
        <span className="home-placeholder__eyebrow">Java Learning</span>
        <h1>{content.title}</h1>
        <p>{content.description}</p>
        <Link className="home-placeholder__action" to="/learning">
          {content.action}
        </Link>
      </section>
    </main>
  );
}
