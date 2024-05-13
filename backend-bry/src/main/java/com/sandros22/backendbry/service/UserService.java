package com.sandros22.backendbry.service;

import com.sandros22.backendbry.entity.User;
import com.sandros22.backendbry.repository.UserRepository;
import org.springframework.stereotype.Service;

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

    public Optional<User> findByID(Integer userId) {
            return userRepository.findById(userId);
    }

    public void deleteById(Integer userId) {
        userRepository.deleteById(userId);
    }

}
