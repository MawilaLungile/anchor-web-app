package com.anchor.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anchor.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
