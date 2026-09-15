import { Component, computed, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HealthService } from '../core/services/health-service';
import { AuthService } from '../core/services/auth-service';

/**
 * Chrome shared by the authenticated screens: a left sidebar in the style of AI chat apps.
 * Public screens such as the login are routed outside of it, which is what lets them take over the whole viewport.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  title = signal('TheArchivist');
  collapsed = signal(false);

  initials = computed(() => this.auth.user()?.username.slice(0, 2).toUpperCase() ?? '');
  role = computed(() => this.auth.user()?.roles[0] ?? '');

  constructor(
    public health: HealthService,
    public auth: AuthService,
    private router: Router
  ) {
    if (!this.auth.user()) {
      this.auth.loadCurrentUser().subscribe();
    }
  }

  toggleSidebar(): void {
    this.collapsed.update(collapsed => !collapsed);
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
