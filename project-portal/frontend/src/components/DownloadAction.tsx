import { EmptyPanel } from './EmptyPanel';

interface DownloadActionProps {
  open: boolean;
  onToggle: () => void;
  className?: string;
}

export function DownloadAction({ open, onToggle, className = '' }: DownloadActionProps) {
  return (
    <div className={`download-control${className ? ` ${className}` : ''}`} data-download-control>
      <button type="button" className="download-action" onClick={onToggle} aria-expanded={open}>
        <span aria-hidden="true">↓</span>
        Download
      </button>

      {open && (
        <div className="download-popover">
          <EmptyPanel type="download" />
        </div>
      )}
    </div>
  );
}
