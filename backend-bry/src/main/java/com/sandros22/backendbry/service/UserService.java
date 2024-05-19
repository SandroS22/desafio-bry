package com.sandros22.backendbry.service;

import com.sandros22.backendbry.entity.User;
import com.sandros22.backendbry.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void save(User user) {
        userRepository.save(user);
    }

    public Optional<User> findById(Integer userId) {
        return userRepository.findById(userId);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public void deleteById(Integer userId) {
        userRepository.deleteById(userId);
    }

    public Optional<User> findByCpf(String cpf) {
        BCryptPasswordEncoder encriptador = new BCryptPasswordEncoder();
        List<User> users = userRepository.findAll();
        Optional<User> returnUser = Optional.empty();
        for (User user : users) {
            if (encriptador.matches(cpf, user.getCpf())) {
                returnUser = Optional.of(user);
            }
        }
        return returnUser;
    }
}
