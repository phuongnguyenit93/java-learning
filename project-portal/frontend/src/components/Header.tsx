import { NavLink } from 'react-router-dom';
import { useLanguage } from '../state/LanguageContext';
import { useTheme } from '../state/ThemeContext';

const labels = {
  vi: {
    home: 'Trang chủ',
    learning: 'Learning',
    search: 'Tìm kiếm...',
    searchLabel: 'Tìm kiếm toàn portal',
  },
  en: {
    home: 'Home',
    learning: 'Learning',
    search: 'Search...',
    searchLabel: 'Search the portal',
  },
};

export function Header() {
  const { language, toggleLanguage } = useLanguage();
  const { theme, toggleTheme } = useTheme();
  const text = labels[language];

  return (
    <header className="topbar">
      <div className="topbar__brand">
        <span className="topbar__brand-icon" aria-hidden="true">☕</span>
        <span>Java <strong>Learning</strong></span>
      </div>

      <nav className="topbar__nav" aria-label="Primary navigation">
        <NavLink to="/" end className={({ isActive }) => `topbar__nav-link${isActive ? ' is-active' : ''}`}>
          {text.home}
        </NavLink>
        <NavLink to="/learning" className={({ isActive }) => `topbar__nav-link${isActive ? ' is-active' : ''}`}>
          {text.learning}
        </NavLink>
      </nav>

      <div className="topbar__actions">
        <label className="global-search" aria-label={text.searchLabel}>
          <span className="global-search__icon" aria-hidden="true">⌕</span>
          <input type="search" placeholder={text.search} />
          <span className="global-search__shortcut">⌘K</span>
        </label>

        <button
          type="button"
          className={`language-toggle language-toggle--${language}`}
          onClick={toggleLanguage}
          aria-label="Toggle Vietnamese and English"
          aria-pressed={language === 'en'}
        >
          <span className="language-toggle__label">VI</span>
          <span className="language-toggle__track" aria-hidden="true">
            <span className="language-toggle__thumb" />
          </span>
          <span className="language-toggle__label">EN</span>
        </button>

        <button
          type="button"
          className="theme-toggle"
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        >
          <span aria-hidden="true">{theme === 'dark' ? '☾' : '☀'}</span>
        </button>
      </div>
    </header>
  );
}
