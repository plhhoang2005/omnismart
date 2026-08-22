package vn.omnismart.store;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    boolean existsBySlug(String slug);
}
