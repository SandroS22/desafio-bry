package com.sandros22.backendbry.controller;

import com.sandros22.backendbry.entity.User;
import com.sandros22.backendbry.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
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

    @GetMapping("/user")
    public User findUser(@RequestParam Integer userId) {
        return userService.findById(userId).orElse(null);
    }

    @PostMapping("/users/create-user")
    public ResponseEntity<Void> createUser(@Valid @RequestBody User user){
        User finalUser = new User();
        finalUser.setName(user.getName());
        finalUser.setFace(user.getFace());
        BCryptPasswordEncoder encriptador = new BCryptPasswordEncoder();
        String hashedCpf = encriptador.encode(user.getCpf());
        finalUser.setCpf(hashedCpf);
        userService.save(finalUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/users/delete-user/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer userId) {
        Optional<User> user = userService.findById(userId);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        userService.deleteById(userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/users")
    public ResponseEntity<Void> updateUser(User updatedUser) {
        Optional<User> oldUser = userService.findById(updatedUser.getId());
        if (oldUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        BeanUtils.copyProperties(updatedUser, oldUser.get());
        userService.save(oldUser.get());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
