package com.sandros22.backendbry.controller;

import com.sandros22.backendbry.entity.User;
import com.sandros22.backendbry.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    @GetMapping
    public String home() {
        return "Hello World!";
    }

    @GetMapping("/users")
    public List<User> getUsers() {
        return userService.findAll();
    }

    @PostMapping("/create-user")
    public HttpStatus createUser(User user) {
        userService.save(user);
        return HttpStatus.CREATED;
    }
}
