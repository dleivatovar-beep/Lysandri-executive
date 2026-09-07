package com.lysandri.api.service;

import com.lysandri.api.dto.request.LoginRequest;
import com.lysandri.api.dto.request.RegisterRequest;
import com.lysandri.api.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);
}
