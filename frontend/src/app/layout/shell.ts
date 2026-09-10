import { Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HealthService } from '../core/services/health-service';

/**
 * Chrome shared by the authenticated screens. Public screens such as the login are routed
 * outside of it, which is what lets them take over the whole viewport.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  templateUrl: './shell.html',
})
export class Shell {
  title = signal('TheArchivist');

  constructor(public health: HealthService) {}
}
