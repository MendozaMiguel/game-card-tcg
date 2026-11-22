import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardIllustrationSpec } from '../../utils/card-artwork';

@Component({
  selector: 'app-arcane-card-art',
  standalone: true,
  templateUrl: './arcane-card-art.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArcaneCardArtComponent {
  private static nextUid = 0;
  protected readonly uid = ArcaneCardArtComponent.nextUid++;
  readonly spec = input.required<CardIllustrationSpec>();
  readonly compact = input(false);
}
