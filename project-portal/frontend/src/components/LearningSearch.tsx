import { useLanguage } from '../state/LanguageContext';

interface LearningSearchProps {
  value: string;
  onChange: (value: string) => void;
}

export function LearningSearch({ value, onChange }: LearningSearchProps) {
  const { language } = useLanguage();

  return (
    <label className="learning-search">
      <span className="learning-search__icon" aria-hidden="true">⌕</span>
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={language === 'vi' ? 'Tìm kiến thức...' : 'Search knowledge...'}
      />
      {value && (
        <button type="button" onClick={() => onChange('')} className="learning-search__clear">
          ×
        </button>
      )}
    </label>
  );
}

