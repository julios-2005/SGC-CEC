import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AlertCentralComponent } from './shared/alert-central.component';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-root',
  imports: [RouterOutlet, AlertCentralComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
}
