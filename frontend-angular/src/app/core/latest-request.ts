import { Observable, Observer, Subscription } from 'rxjs';

/** Cancela la consulta anterior para que una respuesta lenta no cambie el filtro actual. */
export class LatestRequest {
  private subscription?: Subscription;

  run<T>(request: Observable<T>, observer: Partial<Observer<T>>): void {
    this.cancel();
    const current = new Subscription();
    this.subscription = current;
    current.add(request.subscribe(observer));
  }

  cancel(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
  }
}
