import { Injectable, signal } from '@angular/core';

export interface UiToast {
  id: number;
  kind: 'info' | 'success' | 'error';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class AnnouncerService {
  private nextId = 1;
  private readonly politeTimerMap = new Map<number, ReturnType<typeof setTimeout>>();

  readonly politeAnnouncement = signal<string>('');
  readonly assertiveAnnouncement = signal<string>('');
  readonly toasts = signal<readonly UiToast[]>([]);

  announce(message: string, urgency: 'polite' | 'assertive' = 'polite'): void {
    const clean = message.trim();
    if (!clean) {
      return;
    }
    if (urgency === 'assertive') {
      this.assertiveAnnouncement.set('');
      queueMicrotask(() => this.assertiveAnnouncement.set(clean));
    } else {
      this.politeAnnouncement.set('');
      queueMicrotask(() => this.politeAnnouncement.set(clean));
    }
  }

  pushToast(message: string, kind: 'info' | 'success' | 'error' = 'info', durationMs = 4500): void {
    const id = this.nextId++;
    this.toasts.update((list) => [...list.slice(-3), { id, kind, message }]);
    this.announce(message, kind === 'error' ? 'assertive' : 'polite');

    const timer = setTimeout(() => this.dismissToast(id), durationMs);
    this.politeTimerMap.set(id, timer);
  }

  dismissToast(id: number): void {
    const timer = this.politeTimerMap.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.politeTimerMap.delete(id);
    }
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
