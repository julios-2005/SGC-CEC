import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Planificacion } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { PublicHeaderComponent } from './public-header.component';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, RouterLink, PublicHeaderComponent],
  templateUrl: './templates/public-courses.component.html',
  styleUrl: './original-styles/public-courses.css',
})
export class PublicCoursesComponent implements OnInit, OnDestroy {
  private readonly listRequest = new LatestRequest();
  planes: Planificacion[] = [];
  loading = true;
  error = '';

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.listRequest.run(this.api.get<Planificacion[]>('planificaciones/vigentes'), {
      next: data => { this.planes = data; this.loading = false; },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  ngOnDestroy(): void { this.listRequest.cancel(); }

  courseImage(plan: Planificacion): string {
    return plan.curso.foto
      ? this.api.url(`cursos/archivos/${plan.curso.foto}`)
      : '/assets/img/Portada_SGC.jpeg';
  }

  modalityLabel(value: string): string {
    return ({ PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual', HIBRIDO: 'Híbrido' } as Record<string, string>)[value] || value;
  }
}
