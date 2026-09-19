import { quizItems } from '../data/mockLearningData';
import { useLanguage } from '../state/LanguageContext';

interface QuizPanelProps {
  moduleId: string;
}

export function QuizPanel({ moduleId }: QuizPanelProps) {
  const { language } = useLanguage();
  const questions = quizItems.filter((item) => item.moduleId === moduleId);

  if (questions.length === 0) {
    return <EmptyQuiz />;
  }

  return (
    <section className="quiz-grid">
      {questions.map((question, index) => (
        <article key={question.id} className="quiz-card">
          <span className="quiz-card__number">{language === 'vi' ? 'Câu' : 'Question'} {index + 1}</span>
          <h3>{question.question[language]}</h3>
          <div className="quiz-card__answers">
            {question.answers.map((answer, answerIndex) => (
              <button key={`${question.id}-${answerIndex}`} type="button" className="quiz-answer">
                <span>{String.fromCharCode(65 + answerIndex)}</span>
                {answer[language]}
              </button>
            ))}
          </div>
        </article>
      ))}
    </section>
  );
}

function EmptyQuiz() {
  const { language } = useLanguage();

  return (
    <div className="empty-state">
      <strong>{language === 'vi' ? 'Quiz chưa có dữ liệu' : 'No quiz data yet'}</strong>
      <span>
        {language === 'vi'
          ? 'Menu đã sẵn sàng để nối vào generated quiz data.'
          : 'This section is ready for generated quiz data.'}
      </span>
    </div>
  );
}

