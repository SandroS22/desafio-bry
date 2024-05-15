package com.sandros22.backendbry.controller;

import com.sandros22.backendbry.entity.User;
import com.sandros22.backendbry.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

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
    public List<User> findUsers() {
        return userService.findAll();
    }

    @GetMapping("/users/{userId}")
    public User findUser(@PathVariable Integer userId) {
        return userService.findById(userId).orElse(null);
    }

    @PostMapping("/users/create-user")
    public HttpStatus createUser(User user) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        BCryptPasswordEncoder encriptador = new BCryptPasswordEncoder();
        String hashedCpf = encriptador.encode(user.getCpf());
        user.setCpf(hashedCpf);
        userService.save(user);
        return HttpStatus.CREATED;
    }

    @DeleteMapping("/users/{userId}")
    public HttpStatus deleteUser(@PathVariable Integer userId) {
        Optional<User> user = userService.findById(userId);
        if (user.isEmpty()) {
            return HttpStatus.NOT_FOUND;
        }
        userService.deleteById(userId);
        return HttpStatus.OK;
    }

    @PatchMapping("/users")
    public HttpStatus updateUser(User updatedUser) {
        Optional<User> oldUser = userService.findById(updatedUser.getId());
        if (oldUser.isEmpty()) {
            return HttpStatus.NOT_FOUND;
        }
        BeanUtils.copyProperties(updatedUser, oldUser.get());
        userService.save(oldUser.get());
        return HttpStatus.OK;
    }
}
