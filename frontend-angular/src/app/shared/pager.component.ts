import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-pager',
  standalone: true,
  template: `
    @if (totalPages > 1) {
      <div class="pager">
        <button [disabled]="page === 0" (click)="changePage.emit(page - 1)">Anterior</button>
        <span>Página {{ page + 1 }} de {{ totalPages }}</span>
        <button [disabled]="page + 1 >= totalPages" (click)="changePage.emit(page + 1)">Siguiente</button>
      </div>
    }
  `,
})
export class PagerComponent {
  @Input() page = 0;
  @Input() totalPages = 0;
  @Output() readonly changePage = new EventEmitter<number>();
}
