import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { PublicHeaderComponent } from './public-header.component';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [RouterLink, PublicHeaderComponent],
  templateUrl: './templates/payment.component.html',
  styleUrl: './original-styles/payment.css',
})
export class PaymentComponent {
  constructor(readonly api: ApiService) {}
}
