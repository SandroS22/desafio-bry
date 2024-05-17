import { Component } from '@angular/core';
import { HeaderComponent } from "../../components/header/header.component";
import { UsersService } from '../../services/users.service';
import { User } from '../../models/user';

@Component({
    selector: 'app-create-user',
    standalone: true,
    templateUrl: './create-user.component.html',
    styleUrl: './create-user.component.css',
    imports: [HeaderComponent]
})
export class CreateUserComponent {

}
