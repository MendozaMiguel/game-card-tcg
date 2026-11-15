import { Directive, ElementRef, HostListener, OnInit, inject, input } from '@angular/core';
import { AnnouncerService } from '../../core/services/announcer.service';

@Directive({
  selector: '[appFormAriaSync]',
  standalone: true,
})
export class FormAriaSyncDirective implements OnInit {
  private readonly el = inject(ElementRef<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>);
  private readonly announcer = inject(AnnouncerService);

  readonly dataError = input<string | null>(null, { alias: 'appFormAriaSync' });

  private baseDescribedBy = '';
  private hadErrorShown = false;

  ngOnInit(): void {
    const node = this.el.nativeElement;
    this.baseDescribedBy = node.getAttribute('aria-describedby') ?? '';
    if (!node.hasAttribute('aria-invalid')) {
      node.setAttribute('aria-invalid', 'false');
    }
  }

  @HostListener('blur')
  onBlur(): void {
    this.syncState(true);
  }

  @HostListener('input')
  onInput(): void {
    if (this.el.nativeElement.getAttribute('aria-invalid') === 'true' || this.hadErrorShown) {
      this.syncState(false);
    }
  }

  @HostListener('change')
  onChange(): void {
    this.syncState(true);
  }

  @HostListener('invalid')
  onInvalid(): void {
    this.syncState(false);
  }

  private syncState(announce: boolean): void {
    const node = this.el.nativeElement;
    let isInvalid = false;
    try {
      isInvalid = node.matches(':user-invalid');
    } catch {
      isInvalid = !node.validity.valid;
    }

    node.classList.toggle('user-invalid-fallback', isInvalid);
    node.classList.toggle('user-valid-fallback', !isInvalid && node.value.length > 0);
    node.setAttribute('aria-invalid', isInvalid ? 'true' : 'false');

    const errorId = this.dataError() ?? node.dataset['error'] ?? '';
    const baseIds = this.baseDescribedBy
      .split(/\s+/)
      .filter((id) => id.length > 0 && id !== errorId);
    if (isInvalid && errorId) {
      baseIds.push(errorId);
    }
    if (baseIds.length > 0) {
      node.setAttribute('aria-describedby', baseIds.join(' '));
    } else {
      node.removeAttribute('aria-describedby');
    }

    if (isInvalid && announce && !this.hadErrorShown && errorId) {
      const errorEl = document.getElementById(errorId);
      const labelText = node.labels?.[0]?.textContent?.trim() ?? '';
      const errorText = errorEl?.textContent?.trim() ?? '';
      if (errorText) {
        this.announcer.announce(labelText ? `${labelText} : ${errorText}` : errorText);
      }
      this.hadErrorShown = true;
    } else if (!isInvalid) {
      this.hadErrorShown = false;
    }
  }
}
