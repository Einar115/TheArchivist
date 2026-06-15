import { Routes } from '@angular/router';

export const routes: Routes = [
    { path: '', redirectTo: 'chat', pathMatch: 'full' },
    { path: 'chat', loadComponent: () => import('./features/chat/chat').then(m => m.Chat) },
    { path: 'documents', loadComponent: () => import('./features/documents/documents').then(m => m.Documents) },
];
