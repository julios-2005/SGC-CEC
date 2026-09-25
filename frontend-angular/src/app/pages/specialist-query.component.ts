import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Especialista, Planificacion } from '../core/models';
import { LatestRequest } from '../core/latest-request';

@Component({ changeDetection: ChangeDetectionStrategy.Eager, standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './templates/specialist-query.component.html', styleUrl: './original-styles/specialist-query.css' })
export class SpecialistQueryComponent implements OnInit, OnDestroy {
  private readonly optionsRequest = new LatestRequest();
  private readonly plansRequest = new LatestRequest();
  loading = false;
  specialists: Especialista[] = []; plans: Planificacion[] = []; selectedId: number | null = null; selected?: Especialista; error = '';
  constructor(readonly api: ApiService) {}
  ngOnInit(): void { this.optionsRequest.run(this.api.get<Especialista[]>('especialistas/activos'), { next: data => this.specialists = data, error: e => this.error = this.api.errorMessage(e) }); }
  ngOnDestroy(): void { this.optionsRequest.cancel(); this.plansRequest.cancel(); }
  select(): void {
    this.plansRequest.cancel(); this.selected = this.specialists.find(item => item.id === this.selectedId);
    this.plans = []; this.error = ''; this.loading = !!this.selectedId;
    if (!this.selectedId) return;
    this.plansRequest.run(this.api.get<Planificacion[]>(`planificaciones/especialista/${this.selectedId}`), {
      next: data => { this.plans = data; this.loading = false; },
      error: e => { this.error = this.api.errorMessage(e); this.loading = false; },
    });
  }
  print(): void { if(this.selectedId) this.api.openFile(`especialistas/${this.selectedId}/listado-pdf`); }
  modalityLabel(value:string):string { return ({PRESENCIAL:'Presencial',VIRTUAL:'Virtual',HIBRIDO:'Híbrido'} as Record<string,string>)[value] || value; }
}
