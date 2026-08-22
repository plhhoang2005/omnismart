package vn.omnismart.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@SpringBootTest
class IdentityProvisioningServiceTests {

    @Autowired
    private IdentityProvisioningService provisioningService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMemberRepository memberRepository;

    @BeforeEach
    void cleanDatabase() {
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void firstGoogleLoginCreatesOneUserAndOwnerStoreWithoutDuplicatingOnRelogin() {
        AppUser firstLogin = provisioningService.provisionGoogleUser(
                "stable-google-subject", "Owner@Example.com", "Owner Name");
        AppUser secondLogin = provisioningService.provisionGoogleUser(
                "stable-google-subject", "owner@example.com", "Updated Owner");

        assertThat(secondLogin.getId()).isEqualTo(firstLogin.getId());
        assertThat(secondLogin.getDisplayName()).isEqualTo("Updated Owner");
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(storeRepository.count()).isEqualTo(1);
        assertThat(memberRepository.findByUserId(firstLogin.getId()))
                .singleElement()
                .extracting(member -> member.getRole())
                .isEqualTo(StoreRole.OWNER);
    }
}
