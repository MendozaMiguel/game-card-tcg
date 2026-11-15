import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardIllustrationSpec } from '../../utils/card-artwork';

@Component({
  selector: 'app-arcane-card-art',
  standalone: true,
  templateUrl: './arcane-card-art.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArcaneCardArtComponent {
  readonly spec = input.required<CardIllustrationSpec>();
  readonly compact = input(false);
}
