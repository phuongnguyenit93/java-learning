import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useLanguage } from '../state/LanguageContext';

type UnavailableTopic = 'JavaScript' | 'NodeJS';

const topics = [
  { name: 'Java', emblem: 'J', detail: 'JVM', status: 'available' },
  { name: 'JavaScript', emblem: 'JS', detail: 'WEB', status: 'unavailable' },
  { name: 'NodeJS', emblem: 'N', detail: 'RUNTIME', status: 'unavailable' },
] as const;

export function LearningTopicsPage() {
  const { language } = useLanguage();
  const [unavailableTopic, setUnavailableTopic] = useState<UnavailableTopic | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (unavailableTopic) {
      dialogRef.current?.showModal();
    }
  }, [unavailableTopic]);

  const closeDialog = () => {
    dialogRef.current?.close();
    setUnavailableTopic(null);
    triggerRef.current?.focus();
  };

  return (
    <main className="learning-topics-page">
      <div className="learning-topics-page__intro">
        <span className="learning-topics-page__eyebrow">LEARNING PATHS</span>
        <h1>{language === 'vi' ? 'Bạn muốn học gì hôm nay?' : 'What would you like to learn?'}</h1>
        <p>
          {language === 'vi'
            ? 'Chọn một chủ đề để bắt đầu khám phá kiến thức và dự án thực hành.'
            : 'Choose a topic to explore knowledge and hands-on projects.'}
        </p>
      </div>

      <div className="learning-topics-grid">
        {topics.map((topic, index) => {
          const content = (
            <>
              <span className="learning-topic-card__topline">
                <span className="learning-topic-card__index">0{index + 1} / TOPIC</span>
                <span className="learning-topic-card__arrow" aria-hidden="true">↗</span>
              </span>
              <span className={`learning-topic-card__emblem learning-topic-card__emblem--${topic.name.toLowerCase()}`} aria-hidden="true">
                {topic.emblem}
              </span>
              <span className="learning-topic-card__name">{topic.name}</span>
              <span className="learning-topic-card__detail">{topic.detail}</span>
              <span className={`learning-topic-card__status${topic.status === 'available' ? ' is-available' : ''}`}>
                <span className="learning-topic-card__status-dot" aria-hidden="true" />
                {topic.status === 'available'
                  ? (language === 'vi' ? 'Bắt đầu học' : 'Start learning')
                  : (language === 'vi' ? 'Sắp cập nhật' : 'Coming soon')}
              </span>
            </>
          );

          return topic.status === 'available' ? (
            <Link className="learning-topic-card learning-topic-card--java" key={topic.name} to="/learning/java/knowledge">
              {content}
            </Link>
          ) : (
            <button
              className="learning-topic-card"
              key={topic.name}
              type="button"
              onClick={(event) => {
                triggerRef.current = event.currentTarget;
                setUnavailableTopic(topic.name);
              }}
            >
              {content}
            </button>
          );
        })}
      </div>

      {unavailableTopic && (
        <dialog
          className="learning-coming-soon"
          ref={dialogRef}
          aria-labelledby="learning-coming-soon-title"
          aria-describedby="learning-coming-soon-description"
          onClose={() => {
            setUnavailableTopic(null);
            triggerRef.current?.focus();
          }}
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              closeDialog();
            }
          }}
        >
          <button className="learning-coming-soon__close" type="button" onClick={closeDialog} aria-label={language === 'vi' ? 'Đóng' : 'Close'}>
            ×
          </button>
          <span className="learning-coming-soon__icon" aria-hidden="true">✧</span>
          <span className="learning-coming-soon__eyebrow">{unavailableTopic}</span>
          <h2 id="learning-coming-soon-title">
            {language === 'vi' ? 'Nội dung chưa được cập nhật' : 'Content has not been updated yet'}
          </h2>
          <p id="learning-coming-soon-description">
            {language === 'vi'
              ? 'Chủ đề này đang được chuẩn bị. Hãy quay lại sau nhé!'
              : 'This topic is being prepared. Please check back later!'}
          </p>
        </dialog>
      )}
    </main>
  );
}
