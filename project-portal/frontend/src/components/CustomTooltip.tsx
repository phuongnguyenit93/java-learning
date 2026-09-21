import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

interface CustomTooltipProps {
  content: string;
  children: ReactNode;
}

interface TooltipPosition {
  left: number;
  top: number;
  placement: 'top' | 'bottom';
}

export function CustomTooltip({ content, children }: CustomTooltipProps) {
  const anchorRef = useRef<HTMLSpanElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<TooltipPosition | null>(null);

  useLayoutEffect(() => {
    if (!open) {
      return undefined;
    }

    const updatePosition = () => {
      const anchor = anchorRef.current;
      const tooltip = tooltipRef.current;

      if (!anchor || !tooltip) {
        return;
      }

      const anchorRect = anchor.getBoundingClientRect();
      const tooltipRect = tooltip.getBoundingClientRect();
      const viewportPadding = 10;
      const gap = 8;
      const preferredLeft = anchorRect.left + (anchorRect.width / 2) - (tooltipRect.width / 2);
      const maxLeft = Math.max(viewportPadding, window.innerWidth - tooltipRect.width - viewportPadding);
      const left = Math.min(Math.max(preferredLeft, viewportPadding), maxLeft);
      const fitsAbove = anchorRect.top - tooltipRect.height - gap >= viewportPadding;
      const top = fitsAbove
        ? anchorRect.top - tooltipRect.height - gap
        : anchorRect.bottom + gap;

      setPosition({
        left,
        top,
        placement: fitsAbove ? 'top' : 'bottom',
      });
    };

    const frame = window.requestAnimationFrame(updatePosition);
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
    };
  }, [open, content]);

  return (
    <>
      <span
        ref={anchorRef}
        className="custom-tooltip-anchor"
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
      >
        {children}
      </span>

      {open && createPortal(
        <div
          ref={tooltipRef}
          className={`custom-tooltip custom-tooltip--${position?.placement ?? 'top'}`}
          role="tooltip"
          style={{
            left: position?.left ?? 0,
            top: position?.top ?? 0,
            visibility: position ? 'visible' : 'hidden',
          }}
        >
          {content}
        </div>,
        document.body,
      )}
    </>
  );
}
