import { Routes } from '@angular/router';
import { adminGuard, uploaderGuard } from './core/guards/role-guard';

export const routes: Routes = [
    // Outside the shell on purpose: no navbar, full viewport.
    { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.Login) },
    {
        path: '',
        loadComponent: () => import('./layout/shell').then(m => m.Shell),
        children: [
            { path: '', redirectTo: 'chat', pathMatch: 'full' },
            { path: 'chat', loadComponent: () => import('./features/chat/chat').then(m => m.Chat) },
            { path: 'chat/:id', loadComponent: () => import('./features/chat/chat').then(m => m.Chat) },
            {
                path: 'documents',
                canActivate: [uploaderGuard],
                loadComponent: () => import('./features/documents/documents').then(m => m.Documents),
            },
            {
                path: 'admin/users/new',
                canActivate: [adminGuard],
                loadComponent: () => import('./features/admin/register-user/register-user').then(m => m.RegisterUser),
            },
        ],
    },
];
