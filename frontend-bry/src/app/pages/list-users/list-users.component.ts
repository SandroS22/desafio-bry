import { Component, OnInit } from '@angular/core';
import { UsersService } from '../../services/users.service';
import { User } from '../../models/user';
import { HeaderComponent } from "../../components/header/header.component";
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-list-users',
    standalone: true,
    templateUrl: './list-users.component.html',
    styleUrl: './list-users.component.css',
    imports: [HeaderComponent, CommonModule]
})
export class ListUsersComponent implements OnInit {
  constructor(private userService: UsersService){
    this.getAll();
  }

  users: User[] = [];

  getAll(){
    this.userService.getAll().subscribe((users) => (this.users = users));
  }

  ngOnInit(): void {}
}
