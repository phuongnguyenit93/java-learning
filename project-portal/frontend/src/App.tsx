import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom';
import { Header } from './components/Header';
import { HomePage } from './pages/HomePage';
import { LearningPage } from './pages/LearningPage';
import { JavaLearningLayout } from './pages/JavaLearningLayout';
import { LearningTopicsPage } from './pages/LearningTopicsPage';
import { JavaProjectPage } from './pages/JavaProjectPage';
import { MyCvPage } from './pages/MyCvPage';

function LegacyLearningRedirect() {
  const { moduleId } = useParams();
  const { search, hash } = useLocation();

  return <Navigate to={`/learning/java/knowledge/${encodeURIComponent(moduleId ?? '')}${search}${hash}`} replace />;
}

export function App() {
  return (
    <div className="app-shell">
      <Header />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/learning" element={<LearningTopicsPage />} />
        <Route path="/learning/java" element={<JavaLearningLayout />}>
          <Route index element={<Navigate to="knowledge" replace />} />
          <Route path="knowledge" element={<LearningPage />} />
          <Route path="knowledge/:moduleId" element={<LearningPage />} />
          <Route path="project" element={<JavaProjectPage />} />
        </Route>
        <Route path="/learning/:moduleId" element={<LegacyLearningRedirect />} />
        <Route path="/my-cv" element={<MyCvPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
