package org.bebraradar.repository;

import org.bebraradar.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "users")
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
}
