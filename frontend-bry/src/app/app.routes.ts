import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { CreateUserComponent } from './pages/create-user/create-user.component';
import { ListUsersComponent } from './pages/list-users/list-users.component';
import { DeleteUserComponent } from './pages/delete-user/delete-user.component';
import { UpdateUserComponent } from './pages/update-user/update-user.component';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
  },
  {
    path: 'create-user',
    component: CreateUserComponent,
  },
  {
    path: 'delete-user',
    component: DeleteUserComponent,
  },
  {
    path: 'update-user',
    component: UpdateUserComponent,
  },
  {
    path: 'users',
    component: ListUsersComponent,
  },
];
