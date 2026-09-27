interface DownloadActionProps {
  onClick: () => void;
  className?: string;
}

export function DownloadAction({ onClick, className = '' }: DownloadActionProps) {
  return (
    <div className={`download-control${className ? ` ${className}` : ''}`}>
      <button type="button" className="download-action" onClick={onClick}>
        <span aria-hidden="true">↓</span>
        Download
      </button>
    </div>
  );
}
