package com.shop.online.service;

import com.shop.online.common.AuthUser;
import com.shop.online.dto.Requests;
import com.shop.online.dto.Views;
import com.shop.online.exception.BusinessException;
import com.shop.online.repository.CustomerRepository;
import com.shop.online.repository.SellerRepository;
import com.shop.online.security.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SellerRepository sellerRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SellerRepository sellerRepository,
                       CustomerRepository customerRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.sellerRepository = sellerRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Views.LoginResult loginSeller(Requests.Login request) {
        String username = request.username().trim();
        SellerRepository.SellerRow seller = sellerRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), seller.passwordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        String token = jwtUtil.createToken(AuthUser.ROLE_SELLER, seller.id(), seller.username());
        return new Views.LoginResult(token, AuthUser.ROLE_SELLER, seller.username(), seller.id());
    }

    public Views.LoginResult loginCustomer(Requests.Login request) {
        String username = request.username().trim();
        CustomerRepository.CustomerRow customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), customer.passwordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        String token = jwtUtil.createToken(AuthUser.ROLE_CUSTOMER, customer.id(), customer.username());
        return new Views.LoginResult(token, AuthUser.ROLE_CUSTOMER, customer.username(), customer.id());
    }

    public Views.LoginResult register(Requests.Register request) {
        String username = request.username().trim();
        if (customerRepository.existsByUsername(username)) {
            throw BusinessException.conflict("用户名已被注册");
        }
        long id;
        try {
            id = customerRepository.insert(username,
                    passwordEncoder.encode(request.password()),
                    request.phone().trim(),
                    request.defaultTradePlace().trim());
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("用户名已被注册");
        }
        String token = jwtUtil.createToken(AuthUser.ROLE_CUSTOMER, id, username);
        return new Views.LoginResult(token, AuthUser.ROLE_CUSTOMER, username, id);
    }

    public void changeSellerPassword(long sellerId, Requests.PasswordChange request) {
        SellerRepository.SellerRow seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> BusinessException.notFound("卖家账号不存在"));
        if (!passwordEncoder.matches(request.oldPassword(), seller.passwordHash())) {
            throw BusinessException.badRequest("原密码不正确");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        sellerRepository.updatePassword(sellerId, passwordEncoder.encode(request.newPassword()));
    }
}
