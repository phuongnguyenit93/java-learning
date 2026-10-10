import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import defaultFavicon from '../assets/favicon.svg';
import javaFavicon from '../assets/logos/java.svg';

/**
 * The Portal is one SPA document, so route changes must also update the
 * browser-tab metadata. Keep this separate from the visible Header.
 */
export function PageMetadata() {
  const { pathname } = useLocation();

  useEffect(() => {
    const isJavaLearning = pathname === '/learning/java' || pathname.startsWith('/learning/java/');
    const isMyCv = pathname === '/my-cv' || pathname === '/my-cv/';

    const title = isMyCv
      ? 'Nguyen Do Dinh Phuong'
      : pathname.startsWith('/learning/java/project')
        ? 'Java Learning - Project'
        : isJavaLearning
          ? 'Java Learning - Knowledge'
          : 'Learning Platform';

    const favicon = isJavaLearning ? javaFavicon : defaultFavicon;

    document.title = title;

    let icon = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!icon) {
      icon = document.createElement('link');
      icon.rel = 'icon';
      document.head.appendChild(icon);
    }

    icon.type = 'image/svg+xml';
    icon.href = favicon;
  }, [pathname]);

  return null;
}
