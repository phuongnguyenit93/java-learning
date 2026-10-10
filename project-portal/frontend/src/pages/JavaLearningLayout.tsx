import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useLanguage } from '../state/LanguageContext';

export function JavaLearningLayout() {
  const { language } = useLanguage();
  const location = useLocation();
  const [knowledgePath, setKnowledgePath] = useState('/learning/java/knowledge');

  useEffect(() => {
    if (location.pathname.startsWith('/learning/java/knowledge')) {
      setKnowledgePath(`${location.pathname}${location.search}${location.hash}`);
    }
  }, [location.hash, location.pathname, location.search]);

  return (
    <div className="java-learning">
      <nav className="java-section-nav" aria-label={language === 'vi' ? 'Điều hướng Java' : 'Java navigation'}>
        <NavLink className="java-section-nav__back" to="/learning">
          <span aria-hidden="true">←</span>
          {language === 'vi' ? 'Các chủ đề' : 'All topics'}
        </NavLink>

        <div className="java-section-nav__tabs">
          <NavLink
            to={knowledgePath}
            className={({ isActive }) => `java-section-nav__tab${isActive ? ' is-active' : ''}`}
          >
            Knowledge
          </NavLink>
        </div>

        <span className="java-section-nav__context" aria-hidden="true">JAVA</span>
      </nav>
      <Outlet />
    </div>
  );
}
