import { Directive, ElementRef, HostListener, inject } from '@angular/core';

@Directive({
  selector: 'textarea[appImeEnterSubmit], input[appImeEnterSubmit]',
  standalone: true,
})
export class ImeEnterSubmitDirective {
  private readonly el = inject(ElementRef<HTMLTextAreaElement | HTMLInputElement>);
  private lastCompositionEndAt: number | null = null;

  @HostListener('compositionend', ['$event'])
  onCompositionEnd(event: Event): void {
    this.lastCompositionEndAt = event.timeStamp;
  }

  @HostListener('keydown', ['$event'])
  onKeyDown(event: Event): void {
    const kbEvent = event as KeyboardEvent;
    const isTextarea = this.el.nativeElement.tagName === 'TEXTAREA';
    if (kbEvent.key !== 'Enter' || (isTextarea && kbEvent.shiftKey)) {
      return;
    }
    kbEvent.preventDefault();
    if (kbEvent.isComposing) {
      return;
    }
    if (
      this.lastCompositionEndAt !== null &&
      Math.abs(kbEvent.timeStamp - this.lastCompositionEndAt) < 50
    ) {
      return;
    }
    this.el.nativeElement.form?.requestSubmit();
  }
}
