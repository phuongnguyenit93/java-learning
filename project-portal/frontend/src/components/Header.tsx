import { NavLink, useLocation } from 'react-router-dom';
import javaLogo from '../assets/logos/java.svg';
import { useLanguage } from '../state/LanguageContext';
import { useTheme } from '../state/ThemeContext';

const labels = {
  vi: {
    home: 'Trang chủ',
    learning: 'Learning',
    myCv: 'My CV',
    search: 'Tìm kiếm...',
    searchLabel: 'Tìm kiếm toàn portal',
  },
  en: {
    home: 'Home',
    learning: 'Learning',
    myCv: 'My CV',
    search: 'Search...',
    searchLabel: 'Search the portal',
  },
};

export function Header() {
  const { pathname } = useLocation();
  const { language, toggleLanguage } = useLanguage();
  const { theme, toggleTheme } = useTheme();
  const text = labels[language];
  const isJavaLearning = pathname === '/learning/java' || pathname.startsWith('/learning/java/');
  const isMyCv = pathname === '/my-cv' || pathname === '/my-cv/';

  return (
    <header className="topbar">
      <div className={`topbar__brand${isMyCv ? ' topbar__brand--cv' : ''}`}>
        <span className="topbar__brand-icon" aria-hidden="true">
          {isJavaLearning ? <img src={javaLogo} alt="" /> : '☕'}
        </span>
        {isMyCv ? (
          <span className="topbar__brand-name">Nguyen Do Dinh Phuong</span>
        ) : isJavaLearning ? (
          <span>Java <strong>Learning</strong></span>
        ) : (
          <span>Learning <strong>Platform</strong></span>
        )}
      </div>

      <nav className="topbar__nav" aria-label="Primary navigation">
        <NavLink to="/" end className={({ isActive }) => `topbar__nav-link${isActive ? ' is-active' : ''}`}>
          {text.home}
        </NavLink>
        <NavLink to="/learning" className={({ isActive }) => `topbar__nav-link${isActive ? ' is-active' : ''}`}>
          {text.learning}
        </NavLink>
        <NavLink to="/my-cv" className={({ isActive }) => `topbar__nav-link${isActive ? ' is-active' : ''}`}>
          {text.myCv}
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
