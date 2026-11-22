import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { PlayerBoardViewDto, PlayerGameView } from '../../../core/models/arcane.models';
import { AnnouncerService } from '../../../core/services/announcer.service';
import {
  TUTORIAL_STEPS,
  TUTORIAL_TOTAL_STEPS,
  computeAutoTutorialIndex,
} from './arena-tutorial-steps';

const MODAL_BLOCKED_PHASES = new Set([
  'MULLIGAN',
  'WAITING_FOR_REACTION',
  'WAITING_FOR_MODAL_CHOICE',
  'WAITING_FOR_DISCARD',
  'FINISHED',
]);

@Component({
  selector: 'app-arena-tutorial-coach',
  standalone: true,
  template: `
    @if (active() && !isModalPhase()) {
      @if (minimized()) {
        <button
          type="button"
          class="tuto-pill"
          (click)="setMinimized(false)"
          [attr.aria-label]="'Rouvrir le didacticiel : ' + currentStep().badge"
        >
          <svg class="tuto-svg" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2" />
            <polygon points="15 9 13 13 9 15 11 11 15 9" fill="currentColor" />
          </svg>
          <span>Didacticiel · {{ currentStep().index + 1 }}/{{ totalSteps }}</span>
        </button>
      }

      <dialog
        #coachPopover
        popover="manual"
        role="dialog"
        aria-labelledby="tutorial-coach-title"
        aria-describedby="tutorial-coach-desc"
        class="tuto-popover"
      >
        <div class="tuto-header">
          <span class="tuto-badge">
            <svg class="tuto-svg-sm" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2" />
              <polygon points="15 9 13 13 9 15 11 11 15 9" fill="currentColor" />
            </svg>
            <span>{{ currentStep().badge }}</span>
          </span>
          <div class="tuto-header-actions">
            <button
              type="button"
              class="tuto-icon-btn"
              (click)="setMinimized(true)"
              aria-label="Réduire le didacticiel"
              title="Réduire"
            >
              <svg class="tuto-svg-sm" viewBox="0 0 24 24" aria-hidden="true">
                <line x1="6" y1="12" x2="18" y2="12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
              </svg>
            </button>
            <button
              type="button"
              class="tuto-icon-btn"
              (click)="onDismiss()"
              aria-label="Passer le didacticiel"
              title="Passer le didacticiel"
            >
              <svg class="tuto-svg-sm" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>

        <h2 id="tutorial-coach-title" class="tuto-title">
          {{ currentStep().title }}
        </h2>

        <p id="tutorial-coach-desc" class="tuto-body">
          {{ currentStep().body }}
        </p>

        <div class="tuto-hint" role="status">
          <svg class="tuto-svg-sm tuto-hint-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z" fill="currentColor" />
          </svg>
          <span>{{ waitMessage() ?? currentStep().actionHint }}</span>
        </div>

        <div class="tuto-dots" aria-hidden="true">
          @for (step of steps; track step.id) {
            <span
              class="tuto-dot"
              [class.tuto-dot-active]="step.index === currentIndex()"
              [class.tuto-dot-done]="step.index < currentIndex()"
            ></span>
          }
        </div>

        <div class="tuto-footer">
          <button
            type="button"
            class="tuto-btn tuto-btn-ghost"
            [disabled]="currentIndex() <= 1"
            (click)="goPrevious()"
          >
            <span>&lsaquo; Précédent</span>
          </button>

          @if (isLastStep()) {
            <button
              type="button"
              class="tuto-btn tuto-btn-primary"
              [disabled]="saving()"
              (click)="onComplete()"
            >
              <span>{{ saving() ? 'Validation…' : 'Terminer le didacticiel' }}</span>
            </button>
          } @else {
            <button type="button" class="tuto-btn tuto-btn-primary" (click)="goNext()">
              <span>Suivant &rsaquo;</span>
            </button>
          }
        </div>
      </dialog>
    }
  `,
  styles: `
    .tuto-svg {
      inline-size: 0.95rem;
      block-size: 0.95rem;
      flex-shrink: 0;
    }

    .tuto-svg-sm {
      inline-size: 0.85rem;
      block-size: 0.85rem;
      flex-shrink: 0;
    }
    .tuto-pill {
      position: fixed;
      inset-block-start: calc(env(safe-area-inset-top, 0px) + 3.5rem);
      inset-inline-end: 0.75rem;
      z-index: 45;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      min-block-size: 2.35rem;
      padding: 0.35rem 0.75rem;
      border-radius: 9999px;
      border: 1px solid rgba(217, 164, 65, 0.7);
      background: linear-gradient(180deg, rgba(24, 29, 46, 0.96), rgba(14, 17, 28, 0.96));
      color: #f0c96e;
      font-size: 0.75rem;
      font-weight: 800;
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.55);
      cursor: pointer;
    }

    .tuto-popover {
      position: fixed;
      inset-block-start: calc(env(safe-area-inset-top, 0px) + 3.5rem);
      inset-inline-end: 0.75rem;
      inset-block-end: auto;
      inset-inline-start: auto;
      z-index: 55;
      inline-size: min(23.5rem, calc(100vw - 1.5rem));
      max-block-size: min(74dvh, 25rem);
      overflow-y: auto;
      padding: 0.9rem 1rem;
      border-radius: 1rem;
      border: 1px solid rgba(217, 164, 65, 0.55);
      background: linear-gradient(165deg, rgba(20, 24, 38, 0.97), rgba(11, 14, 24, 0.98));
      color: #f1ede4;
      box-shadow: 0 18px 42px rgba(0, 0, 0, 0.72), 0 0 0 1px rgba(240, 201, 110, 0.14) inset;
      overscroll-behavior: contain;
    }

    @media (min-width: 80rem) {
      .tuto-popover,
      .tuto-pill {
        inset-block-start: calc(env(safe-area-inset-top, 0px) + 6.75rem);
      }
    }

    @supports (position-area: block-end) {
      .tuto-popover[data-anchored='true'] {
        inset: auto;
        margin: 0;
        position-anchor: --arena-coach-anchor;
        position-area: block-end span-inline-start;
        position-try-fallbacks: flip-block, flip-inline;
      }
    }

    .tuto-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      margin-block-end: 0.5rem;
    }

    .tuto-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.2rem 0.6rem;
      border-radius: 9999px;
      border: 1px solid rgba(217, 164, 65, 0.55);
      background: rgba(217, 164, 65, 0.14);
      color: #f0c96e;
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .tuto-header-actions {
      display: flex;
      align-items: center;
      gap: 0.25rem;
    }

    .tuto-icon-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-inline-size: 2rem;
      min-block-size: 2rem;
      border-radius: 0.5rem;
      border: 1px solid rgba(100, 116, 139, 0.45);
      background: rgba(30, 36, 56, 0.85);
      color: #cbd5e1;
      cursor: pointer;
    }

    .tuto-title {
      margin: 0 0 0.4rem;
      font-size: 0.96rem;
      line-height: 1.3;
      font-weight: 800;
      color: #f0c96e;
    }

    .tuto-body {
      margin: 0 0 0.65rem;
      font-size: 0.78rem;
      line-height: 1.48;
      color: #d6d0c4;
    }

    .tuto-hint {
      display: flex;
      align-items: flex-start;
      gap: 0.45rem;
      padding: 0.5rem 0.65rem;
      border-radius: 0.65rem;
      border: 1px solid rgba(52, 211, 153, 0.45);
      background: rgba(6, 78, 59, 0.34);
      color: #a7f3d0;
      font-size: 0.75rem;
      font-weight: 700;
      line-height: 1.35;
      margin-block-end: 0.65rem;
    }

    .tuto-hint-icon {
      flex-shrink: 0;
      margin-block-start: 0.1rem;
    }

    .tuto-dots {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.35rem;
      margin-block-end: 0.7rem;
    }

    .tuto-dot {
      inline-size: 0.5rem;
      block-size: 0.3rem;
      border-radius: 9999px;
      background: rgba(148, 163, 184, 0.35);
      transition: inline-size 180ms ease, background-color 180ms ease;
    }

    .tuto-dot-done {
      background: rgba(52, 211, 153, 0.7);
    }

    .tuto-dot-active {
      inline-size: 1.35rem;
      background: #f0c96e;
    }

    .tuto-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    .tuto-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.3rem;
      min-block-size: 2.35rem;
      padding: 0.4rem 0.78rem;
      border-radius: 0.65rem;
      font-size: 0.75rem;
      font-weight: 800;
      cursor: pointer;
    }

    .tuto-btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }

    .tuto-btn-ghost {
      border: 1px solid rgba(100, 116, 139, 0.55);
      background: rgba(30, 36, 56, 0.78);
      color: #e2e8f0;
    }

    .tuto-btn-primary {
      border: 1px solid rgba(240, 201, 110, 0.85);
      background: linear-gradient(180deg, #f0c96e, #d9a441);
      color: #0b0d14;
    }

    .tuto-icon-btn:focus-visible,
    .tuto-btn:focus-visible,
    .tuto-pill:focus-visible {
      outline: 2px solid #f0c96e;
      outline-offset: 2px;
    }

    @media (pointer: coarse) {
      .tuto-icon-btn,
      .tuto-btn,
      .tuto-pill {
        min-block-size: 2.75rem;
        min-inline-size: 2.75rem;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .tuto-dot {
        transition: none;
      }
    }

    @media (forced-colors: active) {
      .tuto-popover,
      .tuto-pill,
      .tuto-btn,
      .tuto-icon-btn {
        border: 2px solid ButtonText;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArenaTutorialCoachComponent {
  private readonly announcer = inject(AnnouncerService);
  private readonly popoverRef = viewChild<ElementRef<HTMLElement>>('coachPopover');

  readonly active = input<boolean>(false);
  readonly gameView = input<PlayerGameView | null>(null);
  readonly myBoard = input<PlayerBoardViewDto | null>(null);
  readonly isMyTurn = input<boolean>(false);
  readonly saving = input<boolean>(false);

  readonly completed = output<void>();
  readonly dismissed = output<void>();

  protected readonly steps = TUTORIAL_STEPS;
  protected readonly totalSteps = TUTORIAL_TOTAL_STEPS;
  protected readonly minimized = signal(false);
  protected readonly currentIndex = signal(0);
  protected readonly waitMessage = computed(() => {
    const idx = this.currentIndex();
    const view = this.gameView();
    const myTurn = this.isMyTurn();
    if (!view || view.phase === 'FINISHED') return null;
    if (idx === 3 && !myTurn) {
      return 'Tour du Mage Automate en cours : votre prochain tour débloquera le combat…';
    }
    if (idx === 4 && myTurn && view.phase === 'MAIN_PHASE' && !view.turn1TruceActive) {
      return 'Prêt pour l’assaut : cliquez maintenant sur « Phase Combat » !';
    }
    return null;
  });

  protected readonly isModalPhase = computed(() => {
    const phase = this.gameView()?.phase;
    return !!phase && MODAL_BLOCKED_PHASES.has(phase);
  });

  protected readonly currentStep = computed(() => {
    const idx = Math.min(Math.max(0, this.currentIndex()), TUTORIAL_STEPS.length - 1);
    return TUTORIAL_STEPS[idx];
  });

  protected readonly isLastStep = computed(
    () => this.currentIndex() >= TUTORIAL_STEPS.length - 1,
  );

  constructor() {
    // Synchronize auto-advancing step monotonically from authoritative server state
    effect(() => {
      if (!this.active()) return;
      const view = this.gameView();
      const board = this.myBoard();
      const myTurn = this.isMyTurn();
      const prevIdx = untracked(() => this.currentIndex());
      const progress = computeAutoTutorialIndex(prevIdx, view, board, myTurn);
      if (progress.currentIndex !== prevIdx) {
        this.currentIndex.set(progress.currentIndex);
        this.minimized.set(false);
        const nextStep = TUTORIAL_STEPS[progress.currentIndex];
        if (nextStep) {
          untracked(() =>
            this.announcer.announce(
              `Didacticiel ${nextStep.badge} : ${nextStep.title}`,
              'polite',
            ),
          );
        }
      }
    });

    // Manage native top-layer Popover visibility without disrupting modal <dialog> phases
    effect(() => {
      const shouldShow = this.active() && !this.isModalPhase() && !this.minimized();
      const el = this.popoverRef()?.nativeElement as
        | (HTMLElement & { showPopover?: () => void; hidePopover?: () => void })
        | undefined;
      if (!el) return;
      if (shouldShow) {
        this.safeShowPopover(el);
      } else {
        this.safeHidePopover(el);
      }
    });
  }

  protected setMinimized(value: boolean): void {
    this.minimized.set(value);
  }

  protected goPrevious(): void {
    const prev = this.currentIndex();
    if (prev > 1) {
      this.currentIndex.set(prev - 1);
    }
  }

  protected goNext(): void {
    const prev = this.currentIndex();
    if (prev < TUTORIAL_STEPS.length - 1) {
      this.currentIndex.set(prev + 1);
    }
  }

  protected onComplete(): void {
    this.completed.emit();
  }

  protected onDismiss(): void {
    this.dismissed.emit();
  }

  private safeShowPopover(
    el: HTMLElement & { showPopover?: () => void; matches: (s: string) => boolean },
  ): void {
    try {
      if (typeof el.showPopover === 'function' && !el.matches(':popover-open')) {
        el.showPopover();
      }
    } catch {
      // Fallback gracefully when running in JSDOM or environments without Popover API
    }
  }

  private safeHidePopover(
    el: HTMLElement & { hidePopover?: () => void; matches: (s: string) => boolean },
  ): void {
    try {
      if (typeof el.hidePopover === 'function' && el.matches(':popover-open')) {
        el.hidePopover();
      }
    } catch {
      // Fallback gracefully when running in JSDOM or environments without Popover API
    }
  }
}
