package com.spectrumai.backend.user.service;

import com.spectrumai.backend.user.model.User;

import java.util.UUID;

public interface UserService {

    User getById(UUID id);

    User getByEmail(String email);
}
