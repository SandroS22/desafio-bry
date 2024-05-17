import { Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { CreateUserComponent } from './pages/create-user/create-user.component';
import { ListUsersComponent } from './pages/list-users/list-users.component';

export const routes: Routes = [
    {
        path: "",
        component: HomeComponent,
      },
      {
        path: "create-user",
        component: CreateUserComponent,
      },
      {
        path: "users",
        component: ListUsersComponent,
      },
];
