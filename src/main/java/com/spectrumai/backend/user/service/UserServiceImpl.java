package com.spectrumai.backend.user.service;

import com.spectrumai.backend.user.model.User;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub temporário — substituir por implementação real quando o domínio
 * de usuários exigir endpoints próprios.
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public User getById(UUID id) {
        throw new UnsupportedOperationException("UserService.getById ainda não implementado");
    }

    @Override
    public User getByEmail(String email) {
        throw new UnsupportedOperationException("UserService.getByEmail ainda não implementado");
    }
}
