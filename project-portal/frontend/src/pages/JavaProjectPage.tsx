import { useState } from 'react';
import { useLanguage } from '../state/LanguageContext';
import type { Language } from '../types/learning';

type DomainId = 'banking' | 'logistics' | 'commerce';

interface ProjectDomain {
  id: DomainId;
  name: string;
  title: Record<Language, string>;
  description: Record<Language, string>;
  label: Record<Language, string>;
}

const domains: ProjectDomain[] = [
  {
    id: 'banking',
    name: 'Banking',
    title: { vi: 'Hệ thống ngân hàng', en: 'Banking Systems' },
    description: {
      vi: 'Khám phá tính toàn vẹn giao dịch, idempotency, kiểm soát đồng thời và thiết kế các luồng chuyển tiền đáng tin cậy.',
      en: 'Explore transaction integrity, idempotency, concurrency control, and reliable money transfer workflows.',
    },
    label: { vi: 'NGHIÊN CỨU THIẾT KẾ NGHIỆP VỤ', en: 'DOMAIN DESIGN STUDY' },
  },
  {
    id: 'logistics',
    name: 'Logistics',
    title: { vi: 'Hệ thống logistics', en: 'Logistics Systems' },
    description: {
      vi: 'Tìm hiểu tính nhất quán tồn kho, quy trình kho vận, theo dõi vận chuyển và giao tiếp giữa WMS với ERP.',
      en: 'Study inventory consistency, warehouse workflows, shipment tracking, and communication between WMS and ERP.',
    },
    label: { vi: 'NGHIÊN CỨU LUỒNG NGHIỆP VỤ', en: 'BUSINESS FLOW STUDY' },
  },
  {
    id: 'commerce',
    name: 'E-commerce',
    title: { vi: 'Hệ thống thương mại điện tử', en: 'E-commerce Systems' },
    description: {
      vi: 'Nghiên cứu giữ chỗ tồn kho, chống bán vượt tồn, điều phối checkout và xử lý lỗi thanh toán.',
      en: 'Study inventory reservation, overselling prevention, checkout orchestration, and payment failure handling.',
    },
    label: { vi: 'NGHIÊN CỨU THIẾT KẾ HỆ THỐNG', en: 'SYSTEM DESIGN STUDY' },
  },
];

function DomainIcon({ domain }: { domain: DomainId }) {
  switch (domain) {
    case 'banking':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M3 10 12 3l9 7M4 10h16M5 21h14M7 10v11M12 10v11M17 10v11" />
        </svg>
      );
    case 'logistics':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <rect x="3" y="7" width="18" height="14" rx="1" />
          <path d="M3 11h18M8 7V4h8v3M8 15h3m2 0h3" />
        </svg>
      );
    case 'commerce':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="9" cy="20" r="1.4" />
          <circle cx="19" cy="20" r="1.4" />
          <path d="M2 3h2l3 13h13l2-9H5" />
        </svg>
      );
  }
}

export function JavaProjectPage() {
  const { language } = useLanguage();
  const [expandedDomain, setExpandedDomain] = useState<DomainId | null>(null);

  return (
    <main className="java-project-page">
      <div className="java-project-content">
        <header className="java-project-intro">
          <span className="java-project-intro__eyebrow">JAVA / PROJECT</span>
          <h1>{language === 'vi' ? 'Khám phá lĩnh vực dự án' : 'Explore Project Domains'}</h1>
          <p>
            {language === 'vi'
              ? 'Tìm hiểu nghiệp vụ và những bài toán thiết kế hệ thống đặc trưng của từng lĩnh vực.'
              : 'Explore business workflows and the system design challenges that define each domain.'}
          </p>
        </header>

        <section className="java-project-domains" aria-label={language === 'vi' ? 'Các lĩnh vực dự án' : 'Project domains'}>
          {domains.map((domain) => {
            const expanded = expandedDomain === domain.id;

            return (
              <button
                key={domain.id}
                type="button"
                className={`java-domain-card java-domain-card--${domain.id}`}
                data-open={expanded}
                aria-expanded={expanded}
                aria-controls={`java-domain-details-${domain.id}`}
                onClick={() => setExpandedDomain((current) => current === domain.id ? null : domain.id)}
              >
                <span className="java-domain-card__pane" aria-hidden="true" />
                <span className="java-domain-card__name">{domain.name}</span>
                <span className="java-domain-card__icon" aria-hidden="true"><DomainIcon domain={domain.id} /></span>
                <span className="java-domain-card__details" id={`java-domain-details-${domain.id}`}>
                  <span className="java-domain-card__title">{domain.title[language]}</span>
                  <span className="java-domain-card__description">{domain.description[language]}</span>
                  <span className="java-domain-card__label">{domain.label[language]} <span aria-hidden="true">→</span></span>
                </span>
              </button>
            );
          })}
        </section>

        <p className="java-project-hint">
          {language === 'vi'
            ? 'Di chuột lên thẻ hoặc chạm để khám phá từng lĩnh vực.'
            : 'Hover over a card or tap to explore each domain.'}
        </p>
      </div>
    </main>
  );
}
