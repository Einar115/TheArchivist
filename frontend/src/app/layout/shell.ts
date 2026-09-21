import { Component, computed, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HealthService } from '../core/services/health-service';
import { AuthService } from '../core/services/auth-service';
import { ConversationService } from '../core/services/conversation-service';
import { Conversation } from '../core/models/chat.model';

const DAY = 86_400_000;

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
  recentGroups = computed(() => groupByAge(this.conversations.conversations()));

  constructor(
    public health: HealthService,
    public auth: AuthService,
    private conversations: ConversationService,
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
    this.auth.logout().subscribe(() => {
      // Conversations only live in memory: whoever signs in next on this browser must not see them.
      this.conversations.clear();
      this.router.navigateByUrl('/login');
    });
  }
}

function groupByAge(conversations: Conversation[]): { label: string; items: Conversation[] }[] {
  const today = new Date().setHours(0, 0, 0, 0);
  const groups = [
    { label: 'Hoy', items: [] as Conversation[] },
    { label: 'Últimos 7 días', items: [] as Conversation[] },
    { label: 'Anteriores', items: [] as Conversation[] },
  ];
  for (const conversation of conversations) {
    const index = conversation.updatedAt >= today ? 0 : conversation.updatedAt >= today - 6 * DAY ? 1 : 2;
    groups[index].items.push(conversation);
  }
  return groups.filter(group => group.items.length);
}
