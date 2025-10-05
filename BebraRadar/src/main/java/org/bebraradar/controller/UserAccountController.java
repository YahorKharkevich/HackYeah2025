package org.bebraradar.controller;

import org.bebraradar.dto.UserRequest;
import org.bebraradar.dto.UserResponse;
import org.bebraradar.entity.UserAccount;
import org.bebraradar.repository.UserAccountRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserAccountController {

    private final UserAccountRepository userAccountRepository;

    public UserAccountController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userAccountRepository.findAll().stream()
            .map(UserAccountController::toDto)
            .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userAccountRepository.findById(id)
            .map(UserAccountController::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UserRequest request) {
        validate(request);
        UserAccount entity = new UserAccount(request.trustLevel());
        return toDto(userAccountRepository.save(entity));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UserRequest request) {
        validate(request);
        UserAccount entity = userAccountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        entity.setTrustLevel(request.trustLevel());
        return toDto(userAccountRepository.save(entity));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            userAccountRepository.deleteById(id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id, ex);
        }
    }

    private static void validate(UserRequest request) {
        if (request.trustLevel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trustLevel is required");
        }
    }

    private static UserResponse toDto(UserAccount entity) {
        return new UserResponse(entity.getId(), entity.getTrustLevel());
    }
}
